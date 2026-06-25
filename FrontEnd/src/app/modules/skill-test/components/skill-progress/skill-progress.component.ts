import { HttpResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../auth/services/auth.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { SoftSkillsService } from '../../../evaluation/services/soft-skills.service';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../../environments/environment';
import { ProgressTrackerComponent } from '../../../formation/components/progress-tracker/progress-tracker.component';
import { TestApiService } from '../../services/test-api.service';

export interface CandidateProgressItem {
  id?: string;
  candidate_id?: string;
  test_type?: string;
  overall_score?: number;
  skill_scores?: Record<string, number>;
  taken_at: string;
  passed?: boolean;
}

interface BenchmarkSkillRow {
  skill: string;
  percentile: number;
  top_10_percent_score: number;
  candidate_score: number;
  avg_score: number;
}

interface BenchmarkOverview {
  overall_percentile: number;
  benchmarks: BenchmarkSkillRow[];
}

@Component({
  selector: 'app-skill-progress',
  standalone: true,
  imports: [CommonModule, RouterLink, ProgressTrackerComponent],
  templateUrl: './skill-progress.component.html',
  styleUrl: './skill-progress.component.scss'
})
export class SkillProgressComponent implements OnInit {
  private auth = inject(AuthService);
  private notify = inject(NotificationService);
  private softSkillsService = inject(SoftSkillsService);
  private testApi = inject(TestApiService);
  private http = inject(HttpClient);

  activeTab: 'TECH' | 'SOFT' = 'TECH';
  showTestOptions = false;

  progress: CandidateProgressItem[] = [];
  benchmark: BenchmarkOverview | null = null;
  loadingProgress = true;
  loadingBenchmark = true;
  progressError = '';
  benchmarkError = '';
  exportingPdf = false;
  private userId = '';

  ngOnInit(): void {
    const u = this.auth.getCurrentUser();
    if (!u?.id) {
      this.loadingProgress = false;
      this.loadingBenchmark = false;
      this.progressError = 'Utilisateur non authentifie.';
      this.benchmarkError = 'Utilisateur non authentifie.';
      return;
    }

    this.userId = String(u.id);
    this.loadAll();
  }

  setTab(tab: 'TECH' | 'SOFT'): void {
    if (this.activeTab === tab) return;
    this.activeTab = tab;
    this.loadAll();
  }

  private loadAll(): void {
    this.loadingProgress = true;
    this.loadingBenchmark = true;
    this.progressError = '';
    this.benchmarkError = '';
    
    if (this.activeTab === 'TECH') {
      this.loadTechProgress();
    } else {
      this.loadSoftProgress();
    }
    
    // In a real app, benchmark might be a separate call or derived
    this.loadBenchmark();
  }

  get averageScore(): number {
    if (this.progress.length === 0) {
      return 0;
    }
    const total = this.progress.reduce((sum, item) => sum + this.toNumber(item.overall_score), 0);
    return Math.round(total / this.progress.length);
  }

  get passRate(): number {
    if (this.progress.length === 0) {
      return 0;
    }
    const passedCount = this.progress.filter(item => !!item.passed).length;
    return Math.round((passedCount / this.progress.length) * 100);
  }

  get overallPercentile(): number {
    return this.benchmark?.overall_percentile ?? 0;
  }

  get benchmarkRows(): BenchmarkSkillRow[] {
    return this.benchmark?.benchmarks ?? [];
  }

  trackByTakenAt(index: number, item: CandidateProgressItem): string {
    return `${item.taken_at}-${index}`;
  }

  trackBySkill(index: number, item: BenchmarkSkillRow): string {
    return `${item.skill}-${index}`;
  }

  formatTestType(testType: string | null | undefined): string {
    if (!testType) {
      return 'Evaluation generale';
    }

    const normalized = testType.toLowerCase();
    if (normalized === 'mcq') {
      return 'QCM + code challenge';
    }

    return testType.replace(/_/g, ' ');
  }

  percentileLabel(percentile: number): string {
    if (percentile >= 80) {
      return 'Excellent positionnement';
    }
    if (percentile >= 60) {
      return 'Niveau superieur a la moyenne';
    }
    if (percentile >= 40) {
      return 'Niveau en progression';
    }
    return 'Renforcement recommande';
  }

  skillEntries(item: CandidateProgressItem): { skill: string; score: number }[] {
    if (!item.skill_scores || typeof item.skill_scores !== 'object') {
      return [];
    }

    return Object.entries(item.skill_scores)
      .map(([skill, value]) => ({
        skill,
        score: Math.round(this.toNumber(value))
      }))
      .sort((a, b) => b.score - a.score)
      .slice(0, 4);
  }

  benchmarkRatio(score: number, topScore: number): number {
    const max = Math.max(1, this.toNumber(topScore));
    return Math.max(0, Math.min(100, Math.round((this.toNumber(score) / max) * 100)));
  }

  exportPdf(): void {
    const data = document.getElementById('pdf-content');
    if (!data) return;
    
    this.exportingPdf = true;
    this.notify.info("Génération du rapport PDF visuel en cours...");

    import('html2canvas').then(html2canvasModule => {
      const html2canvas = html2canvasModule.default;
      import('jspdf').then(jspdfModule => {
        const jsPDF = jspdfModule.jsPDF;

        // Temporarily hide the action buttons so they don't appear in the PDF
        const actionButtons = document.querySelector('.hero-actions') as HTMLElement;
        const originalDisplay = actionButtons ? actionButtons.style.display : '';
        if (actionButtons) {
          actionButtons.style.display = 'none';
        }

        html2canvas(data, {
          scale: 2, // higher resolution
          useCORS: true,
          logging: false
        }).then(canvas => {
          if (actionButtons) {
            actionButtons.style.display = originalDisplay; // restore
          }

          const imgWidth = 210; // A4 width in mm
          const pageHeight = 297; // A4 height in mm
          const imgHeight = canvas.height * imgWidth / canvas.width;
          let heightLeft = imgHeight;

          const contentDataURL = canvas.toDataURL('image/png');
          const pdf = new jsPDF('p', 'mm', 'a4');
          let position = 0;

          pdf.addImage(contentDataURL, 'PNG', 0, position, imgWidth, imgHeight);
          heightLeft -= pageHeight;

          while (heightLeft >= 0) {
            position = heightLeft - imgHeight;
            pdf.addPage();
            pdf.addImage(contentDataURL, 'PNG', 0, position, imgWidth, imgHeight);
            heightLeft -= pageHeight;
          }
          
          pdf.save(`TalentPredict_Visuel_${new Date().getTime()}.pdf`);
          
          this.exportingPdf = false;
          this.notify.success("Rapport visuel exporté avec succès.");
        }).catch(err => {
          if (actionButtons) actionButtons.style.display = originalDisplay;
          console.error('Visual PDF Export failed', err);
          this.notify.error("Erreur lors de l'exportation du PDF visuel.");
          this.exportingPdf = false;
        });
      });
    }).catch(err => {
      console.error('Failed to load PDF libraries', err);
      this.notify.error("Erreur de chargement des librairies PDF.");
      this.exportingPdf = false;
    });
  }

  toggleTestOptions(): void {
    this.showTestOptions = !this.showTestOptions;
  }

  private loadTechProgress(): void {
    const url = `${environment.apiUrl}/candidates/${this.userId}/progress`;
    this.http.get<CandidateProgressItem[]>(url).subscribe({
      next: (data) => {
        this.progress = data || [];
        if (this.progress.length === 0) {
          const fallback = this.getLocalTechFallback();
          if (fallback) {
            this.progress = [fallback];
          }
        }
        this.loadingProgress = false;
      },
      error: (err) => {
        console.error('Error loading tech progress', err);
        const fallback = this.getLocalTechFallback();
        if (fallback) {
          this.progress = [fallback];
          this.progressError = '';
        } else {
          this.progressError = 'Impossible de charger vos sessions techniques.';
        }
        this.loadingProgress = false;
      }
    });
  }

  private loadSoftProgress(): void {
    this.softSkillsService.getProgress().subscribe({
      next: (data) => {
        // Map SoftSkillsProgressDto to CandidateProgressItem format
        this.progress = data.map(d => ({
          overall_score: d.overallScore,
          taken_at: d.evaluationDate.toString(),
          test_type: 'Evaluation Soft Skills',
          passed: (d.overallScore || 0) >= 60,
          skill_scores: d.skills || {}
        }));
        this.loadingProgress = false;
      },
      error: (err) => {
        console.error('Error loading soft progress', err);
        this.progressError = 'Impossible de charger vos evaluations soft skills.';
        this.loadingProgress = false;
      }
    });
  }

  private loadBenchmark(): void {
    // For now, we simulate benchmark based on progress or a fixed logic
    // In production, this would call /api/assessment/benchmark
    setTimeout(() => {
      if (this.progress.length === 0) {
        this.benchmark = null;
        this.loadingBenchmark = false;
        this.benchmarkError = 'Passez un test pour voir votre positionnement.';
        return;
      }

      const latest = this.progress[0];
      const scores = this.activeTab === 'TECH' 
        ? ['Angular', 'Spring Boot', 'SQL', 'Git'] 
        : ['Communication', 'Leadership', 'Empathie', 'Flexibilite'];

      this.benchmark = {
        overall_percentile: Math.min(95, Math.round(this.toNumber(latest.overall_score) * 1.1)),
        benchmarks: scores.map(s => ({
          skill: s,
          candidate_score: Math.round(this.toNumber(latest.overall_score) * (0.8 + Math.random() * 0.4)),
          avg_score: 65,
          top_10_percent_score: 90,
          percentile: Math.min(99, Math.round(this.toNumber(latest.overall_score) * 1.05))
        }))
      };
      this.loadingBenchmark = false;
    }, 800);
  }

  private normalizeBenchmark(raw: unknown): BenchmarkOverview | null {
    if (!raw || typeof raw !== 'object') {
      return null;
    }

    const data = raw as Record<string, unknown>;
    const rows = Array.isArray(data['benchmarks']) ? data['benchmarks'] : [];

    const normalizedRows: BenchmarkSkillRow[] = rows
      .filter((item): item is Record<string, unknown> => !!item && typeof item === 'object')
      .map((row) => ({
        skill: String(row['skill'] ?? 'N/A'),
        percentile: Math.round(this.toNumber(row['percentile'])),
        top_10_percent_score: Math.round(this.toNumber(row['top_10_percent_score'])),
        candidate_score: Math.round(this.toNumber(row['candidate_score'])),
        avg_score: Math.round(this.toNumber(row['avg_score']))
      }));

    return {
      overall_percentile: Math.round(this.toNumber(data['overall_percentile'])),
      benchmarks: normalizedRows
    };
  }

  private toNumber(value: unknown): number {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  private getLocalTechFallback(): CandidateProgressItem | null {
    if (this.activeTab !== 'TECH') {
      return null;
    }

    const stored = sessionStorage.getItem('latestTechResult');
    if (!stored) {
      return null;
    }

    let parsed: any = null;
    try {
      parsed = JSON.parse(stored);
    } catch {
      return null;
    }

    const score = this.toNumber(parsed?.finalScore ?? parsed?.overall_score ?? 0);
    const skillScores = parsed?.skillScores ?? parsed?.skill_scores ?? {};
    if (!Number.isFinite(score)) {
      return null;
    }

    let takenAt = new Date().toISOString();
    let testType = 'MCQ';
    const metaRaw = sessionStorage.getItem('latestTechResultMeta');
    if (metaRaw) {
      try {
        const meta = JSON.parse(metaRaw);
        if (meta?.taken_at) {
          takenAt = String(meta.taken_at);
        }
        if (meta?.test_type) {
          testType = String(meta.test_type);
        }
      } catch { }
    }

    return {
      overall_score: score,
      skill_scores: skillScores,
      passed: !!parsed?.passed,
      test_type: testType,
      taken_at: takenAt
    };
  }
}
