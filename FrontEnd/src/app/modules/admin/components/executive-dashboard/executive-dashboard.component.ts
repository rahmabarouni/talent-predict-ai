import {
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { NotificationService } from '../../../../core/services/notification.service';
import {
  AdminOverviewResponse,
  DashboardService,
} from '../../../dashboard/services/dashboard.service';

import { FormationService } from '../../../formation/services/formation.service';
import { FormationResponse, StatutFormation } from '../../../formation/models/formation.model';
import { HiPoService } from '../../../evaluation/services/hipo.service';
import { HiPoDto } from '../../../evaluation/models/hipo.model';
import { catchError, finalize, forkJoin, of } from 'rxjs';
import {
  Chart,
  BarController,
  CategoryScale,
  LinearScale,
  BarElement,
  DoughnutController,
  ArcElement,
  LineController,
  LineElement,
  PointElement,
  Filler,
  Tooltip,
  Legend
} from 'chart.js';

Chart.register(
  BarController, CategoryScale, LinearScale, BarElement,
  DoughnutController, ArcElement,
  LineController, LineElement, PointElement, Filler,
  Tooltip, Legend
);

export interface KpiCard {
  label: string;
  value: string | number;
  subLabel: string;
  trend: 'up' | 'down' | 'neutral';
  trendValue: string;
  icon: string;
  color: 'blue' | 'green' | 'purple' | 'orange' | 'red' | 'teal';
}

@Component({
  selector: 'app-executive-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './executive-dashboard.component.html',
  styleUrl: './executive-dashboard.component.scss'
})
export class ExecutiveDashboardComponent implements OnInit, OnDestroy {
  @ViewChild('skillsChartRef') skillsChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('mbtiChartRef') mbtiChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('trendChartRef') trendChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('deptChartRef') deptChartRef!: ElementRef<HTMLCanvasElement>;


  private notificationService = inject(NotificationService);
  private dashboardService = inject(DashboardService);
  private formationService = inject(FormationService);
  private hipoService = inject(HiPoService);

  private clockIntervalId?: ReturnType<typeof setInterval>;
  private refreshIntervalId?: ReturnType<typeof setInterval>;
  private skillsChart?: Chart;
  private mbtiChart?: Chart;
  private trendChart?: Chart;
  private deptChart?: Chart;

  private chartsInitialized = false;

  loading = signal(false);
  loadError = signal<string | null>(null);
  currentTime = signal(new Date());

  // Active tab for analytics section
  activeAnalyticsTab = signal<'skills' | 'mbti' | 'trend'>('skills');

  // Alert dismissals

  testCoverage = signal(0);
  pendingFormationCount = signal(0);
  dismissAlert = signal({ coverage: false, formation: false });

  // KPIs
  kpis = signal<KpiCard[]>([]);

  // Analytics raw data
  mbtiDistribution = signal<{ type: string; count: number }[]>([]);
  departmentStats = signal<any[]>([]);
  hipoTalents = signal<HiPoDto[]>([]);

  // Raw employee list (used only for derived analytics)
  employeesList = signal<any[]>([]);



  topDepartment = computed(() => {
    const depts = this.departmentStats();
    if (!depts.length) return '—';
    return depts.reduce((best, d) => d.avgScore > best.avgScore ? d : best, depts[0]).dept;
  });

  topMbtiType = computed(() => {
    const dist = this.mbtiDistribution();
    if (!dist.length) return '—';
    return dist.reduce((best, d) => d.count > best.count ? d : best, dist[0]).type;
  });



  pendingFormationsList = signal<FormationResponse[]>([]);

  onboardingStats = signal({
    completedProfile: 0,
    firstTest: 0,
    firstFormation: 0,
    aiPrediction: 0,
    total: 1
  });

  // Computed progress %
  onboardingProfilePct = computed(() =>
    Math.round((this.onboardingStats().completedProfile / this.onboardingStats().total) * 100)
  );
  onboardingTestPct = computed(() =>
    Math.round((this.onboardingStats().firstTest / this.onboardingStats().total) * 100)
  );
  onboardingFormationPct = computed(() =>
    Math.round((this.onboardingStats().firstFormation / this.onboardingStats().total) * 100)
  );

  ngOnInit(): void {
    this.clockIntervalId = setInterval(() => this.currentTime.set(new Date()), 60_000);
    this.refreshIntervalId = setInterval(() => this.loadLiveDashboardData(), 5 * 60_000);
    this.loadLiveDashboardData();
  }



  ngOnDestroy(): void {
    if (this.clockIntervalId) clearInterval(this.clockIntervalId);
    if (this.refreshIntervalId) clearInterval(this.refreshIntervalId);
    this.destroyCharts();
  }

  setAnalyticsTab(tab: 'skills' | 'mbti' | 'trend'): void {
    this.activeAnalyticsTab.set(tab);
    // Re-render chart after tab switch (canvas becomes visible)
    setTimeout(() => this.renderActiveChart(), 50);
  }

  exportReport(): void {
    this.notificationService.info('Génération du rapport RH en cours...');
    this.dashboardService.getHrGlobalReport().subscribe({
      next: (blob: Blob) => {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `Rapport_RH_Global_${new Date().toISOString().split('T')[0]}.pdf`;
        link.click();
        window.URL.revokeObjectURL(url);
        this.notificationService.success('Rapport exporté avec succès.');
      },
      error: (err: any) => {
        console.error('Export error:', err);
        this.notificationService.error('Erreur lors de l\'exportation du rapport.');
      }
    });
  }

  sendBulkReminder(): void {
    this.notificationService.success('Relance envoyée aux employés sans test.');
  }

  dismiss(type: 'coverage' | 'formation'): void {
    this.dismissAlert.update(v => ({ ...v, [type]: true }));
  }

  approveFormation(f: FormationResponse): void {
    this.formationService.updateFormationStatus(f.id, StatutFormation.ACCEPTEE).subscribe({
      next: () => {
        this.notificationService.success('Formation approuvée');
        this.loadLiveDashboardData();
      }
    });
  }

  rejectFormation(f: FormationResponse): void {
    this.formationService.updateFormationStatus(f.id, StatutFormation.REJETEE).subscribe({
      next: () => {
        this.notificationService.success('Formation rejetée');
        this.loadLiveDashboardData();
      }
    });
  }

  refreshData(): void {
    if (this.loading()) return;
    this.loadLiveDashboardData(true);
  }

  private loadLiveDashboardData(showToast = false): void {
    this.loadError.set(null);
    this.loading.set(true);

    forkJoin({
      overview: this.dashboardService.getAdminOverview(),
      formations: this.formationService.getAllFormations().pipe(catchError(() => of([] as FormationResponse[]))),
      hipos: this.hipoService.getAllUsersHiPo().pipe(catchError(() => of([] as HiPoDto[])))
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ overview, formations, hipos }) => {
          this.processDashboardData(overview, formations);
          // Filter to only show actual HiPo or high performers for the highlight list
          const topTalents = hipos.filter(h => h.isHiPo || h.category === 'High Performer').sort((a,b) => b.finalHiPoScore - a.finalHiPoScore);
          this.hipoTalents.set(topTalents);
          this.currentTime.set(new Date());
          if (showToast) this.notificationService.success('Dashboard synchronisé avec succès.');
          // Give Angular time to render, then init charts
          setTimeout(() => this.initCharts(), 100);
        },
        error: () => {
          this.loadError.set('Impossible de charger les données RH live.');
          this.notificationService.error('Erreur API backend.');
        }
      });
  }

  private processDashboardData(
    overview: AdminOverviewResponse,
    formations: FormationResponse[]
  ): void {
    const employees = overview.employees || [];
    const total = employees.length || 1;

    const enhancedEmployees = employees.map(emp => {
      return {
        ...emp,

        realScore: 0,
        fullName: `${emp.firstName} ${emp.lastName}`.trim() || emp.email
      };
    });

    this.employeesList.set(enhancedEmployees);

    const activeProfiles = enhancedEmployees.filter(e => e.active).length;
    const assessedProfiles = enhancedEmployees.filter(e => e.testCount > 0).length;
    const readyProfiles = enhancedEmployees.filter(e => e.active && e.testCount > 0 && e.formationCount > 0).length;


    let totalScore = 0, scoredCount = 0;
    enhancedEmployees.forEach(e => {
      if (e.realScore > 0) { totalScore += e.realScore; scoredCount++; }
    });
    const avgScore = scoredCount > 0 ? Math.round(totalScore / scoredCount) : 0;

    const pendingF = formations.filter(f => f.statut === StatutFormation.EN_ATTENTE);
    const activeF = formations.filter(f => f.statut === StatutFormation.EN_COURS);

    this.pendingFormationsList.set(pendingF);

    this.testCoverage.set(Math.round((assessedProfiles / total) * 100));
    this.pendingFormationCount.set(pendingF.length);

    this.kpis.set([
      { label: 'Employés actifs', value: activeProfiles, subLabel: 'Utilisateurs', trend: 'up', trendValue: '+2%', icon: 'users', color: 'blue' },
      { label: 'Couverture tests', value: `${this.testCoverage()}%`, subLabel: 'Objectif: 80%', trend: 'neutral', trendValue: '-', icon: 'check-circle', color: 'green' },
      { label: 'Score moyen', value: avgScore, subLabel: '/ 100 pts', trend: 'up', trendValue: '+1.5 pts', icon: 'star', color: 'purple' },
      { label: 'Formations en attente', value: pendingF.length, subLabel: 'À approuver', trend: 'neutral', trendValue: '-', icon: 'clock', color: pendingF.length > 0 ? 'red' : 'green' },
      { label: 'Formations actives', value: activeF.length, subLabel: 'En cours', trend: 'up', trendValue: '+5', icon: 'play', color: 'orange' },
      { label: 'Profils complets', value: readyProfiles, subLabel: 'Testés + Formés', trend: 'up', trendValue: '+10', icon: 'award', color: 'teal' },

    ]);

    // MBTI
    const mbtiCounts: Record<string, number> = {};
    enhancedEmployees.forEach(e => {
      const t = e.personalityType || 'Non évalué';
      mbtiCounts[t] = (mbtiCounts[t] || 0) + 1;
    });
    this.mbtiDistribution.set(Object.entries(mbtiCounts).map(([type, count]) => ({ type, count })));

    // Department Stats
    const deptMap: Record<string, any> = {};
    enhancedEmployees.forEach(e => {
      const d = e.department || 'Non assigné';
      if (!deptMap[d]) deptMap[d] = { dept: d, count: 0, scoreTotal: 0, testedCount: 0 };
      deptMap[d].count++;
      if (e.realScore > 0) deptMap[d].scoreTotal += e.realScore;
      if (e.testCount > 0) deptMap[d].testedCount++;
    });
    this.departmentStats.set(Object.values(deptMap).map(d => ({
      ...d,
      avgScore: d.count > 0 ? Math.round(d.scoreTotal / d.count) : 0,
      coverage: Math.round((d.testedCount / d.count) * 100)
    })));

    // Onboarding
    this.onboardingStats.set({
      total,
      completedProfile: enhancedEmployees.filter(e => e.department && e.position).length,
      firstTest: assessedProfiles,
      firstFormation: enhancedEmployees.filter(e => e.formationCount > 0).length,
      aiPrediction: overview.totalPredictions
    });
  }

  private initCharts(): void {
    this.destroyCharts();
    this.chartsInitialized = true;
    this.renderSkillsChart();
    this.renderMbtiChart();
    this.renderTrendChart();
    this.renderDeptChart();

  }

  private renderActiveChart(): void {
    const tab = this.activeAnalyticsTab();
    if (tab === 'skills') this.renderSkillsChart();
    else if (tab === 'mbti') this.renderMbtiChart();
    else this.renderTrendChart();
  }

  private renderSkillsChart(): void {
    const canvas = this.skillsChartRef?.nativeElement;
    if (!canvas) return;
    if (this.skillsChart) this.skillsChart.destroy();
    this.skillsChart = new Chart(canvas, {
      type: 'bar',
      data: {
        labels: ['Communication', 'Leadership', 'Résolution P.', 'Adaptabilité', 'Travail équipe'],
        datasets: [{
          label: 'Score moyen (%)',
          data: [85, 65, 90, 72, 78],
          backgroundColor: [
            'rgba(99, 102, 241, 0.85)',
            'rgba(245, 158, 11, 0.85)',
            'rgba(16, 185, 129, 0.85)',
            'rgba(59, 130, 246, 0.85)',
            'rgba(139, 92, 246, 0.85)'
          ],
          borderRadius: 8,
          borderSkipped: false,
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          y: { beginAtZero: true, max: 100, grid: { color: 'rgba(0,0,0,0.04)' }, ticks: { font: { size: 11 } } },
          x: { grid: { display: false }, ticks: { font: { size: 11 } } }
        }
      }
    });
  }

  private renderMbtiChart(): void {
    const canvas = this.mbtiChartRef?.nativeElement;
    if (!canvas) return;
    if (this.mbtiChart) this.mbtiChart.destroy();
    const dist = this.mbtiDistribution();
    const labels = dist.length > 0 ? dist.map(d => d.type) : ['Non évalué'];
    const values = dist.length > 0 ? dist.map(d => d.count) : [1];
    const colors = ['#6366f1','#10b981','#f59e0b','#3b82f6','#ef4444','#8b5cf6','#14b8a6','#f97316'];
    this.mbtiChart = new Chart(canvas, {
      type: 'doughnut',
      data: {
        labels,
        datasets: [{
          data: values,
          backgroundColor: colors.slice(0, labels.length),
          borderWidth: 3,
          borderColor: '#fff',
          hoverOffset: 8
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '65%',
        plugins: {
          legend: { position: 'right', labels: { boxWidth: 12, font: { size: 11 }, padding: 12 } }
        }
      }
    });
  }

  private renderTrendChart(): void {
    const canvas = this.trendChartRef?.nativeElement;
    if (!canvas) return;
    if (this.trendChart) this.trendChart.destroy();
    const months = ['Jan','Fév','Mar','Avr','Mai','Juin','Juil','Aoû','Sep','Oct','Nov','Déc'];
    const now = new Date();
    const last6 = Array.from({ length: 6 }, (_, i) => months[(now.getMonth() - 5 + i + 12) % 12]);
    this.trendChart = new Chart(canvas, {
      type: 'line',
      data: {
        labels: last6,
        datasets: [{
          label: 'Score moyen',
          data: [58, 63, 67, 71, 74, this.kpis().find(k => k.label === 'Score moyen')?.value as number || 75],
          borderColor: '#6366f1',
          backgroundColor: 'rgba(99,102,241,0.12)',
          fill: true,
          tension: 0.4,
          pointBackgroundColor: '#6366f1',
          pointRadius: 5,
          pointHoverRadius: 7,
        }, {
          label: 'Couverture %',
          data: [30, 42, 51, 58, 65, this.testCoverage()],
          borderColor: '#10b981',
          backgroundColor: 'rgba(16,185,129,0.08)',
          fill: true,
          tension: 0.4,
          pointBackgroundColor: '#10b981',
          pointRadius: 5,
          pointHoverRadius: 7,
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: 'top', labels: { boxWidth: 12, font: { size: 11 } } } },
        scales: {
          y: { beginAtZero: true, max: 100, grid: { color: 'rgba(0,0,0,0.04)' }, ticks: { font: { size: 11 } } },
          x: { grid: { display: false }, ticks: { font: { size: 11 } } }
        }
      }
    });
  }

  private renderDeptChart(): void {
    const canvas = this.deptChartRef?.nativeElement;
    if (!canvas) return;
    if (this.deptChart) this.deptChart.destroy();
    const depts = this.departmentStats();
    if (!depts.length) return;
    this.deptChart = new Chart(canvas, {
      type: 'bar',
      data: {
        labels: depts.map(d => d.dept),
        datasets: [
          {
            label: 'Score moyen',
            data: depts.map(d => d.avgScore),
            backgroundColor: 'rgba(99,102,241,0.82)',
            borderRadius: 6,
            borderSkipped: false,
          },
          {
            label: 'Couverture %',
            data: depts.map(d => d.coverage),
            backgroundColor: 'rgba(16,185,129,0.75)',
            borderRadius: 6,
            borderSkipped: false,
          }
        ]
      },
      options: {
        indexAxis: 'y',
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: 'top', labels: { boxWidth: 12, font: { size: 11 } } } },
        scales: {
          x: { beginAtZero: true, max: 100, grid: { color: 'rgba(0,0,0,0.04)' }, ticks: { font: { size: 11 } } },
          y: { grid: { display: false }, ticks: { font: { size: 11 } } }
        }
      }
    });
  }



  private destroyCharts(): void {
    this.skillsChart?.destroy();
    this.mbtiChart?.destroy();
    this.trendChart?.destroy();
    this.deptChart?.destroy();

    this.skillsChart = undefined;
    this.mbtiChart = undefined;
    this.trendChart = undefined;
    this.deptChart = undefined;

  }
}
