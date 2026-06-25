import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { timeout } from 'rxjs/operators';
import {
  DashboardFormation,
  DashboardService,
  DashboardSkill,
  EmployeeDashboardResponse,
  TestSummary
} from '../../services/dashboard.service';
import { AuthService } from '../../../auth/services/auth.service';
import { PredictionResponse } from '../../models/prediction.model';
import { NotificationService } from '../../../../core/services/notification.service';

type RadarToggle = 'tous' | 'tech' | 'soft';
type MomentumDirection = 'up' | 'down' | 'flat';

interface RadarEntry {
  name: string;
  score: number;
  type: 'tech' | 'soft';
}

interface SkillMomentumItem {
  name: string;
  current: number;
  delta: number;
  dir: MomentumDirection;
}

interface CareerMatch {
  role: string;
  match: number;
  gaps: string[];
  targetRoute: string;
}

interface CareerBlueprint {
  role: string;
  tech: string[];
  soft: string[];
  targetRoute: string;
}

interface WeeklyInsight {
  skillChange: string;
  formationProgress: string;
  recommendation: string;
}

interface BadgeItem {
  icon: string;
  name: string;
  date: string;
}

interface LockedBadgeItem {
  icon: string;
  name: string;
  condition: string;
}

interface FeedItem {
  icon: string;
  text: string;
  date: string;
  highlight?: boolean;
}

@Component({
  selector: 'app-user-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './user-dashboard.component.html',
  styleUrls: ['./user-dashboard.component.scss']
})
export class UserDashboardComponent implements OnInit {
  Math = Math;
  private dashboardService = inject(DashboardService);
  private authService = inject(AuthService);
  private notificationService = inject(NotificationService);

  private readonly roleBlueprints: CareerBlueprint[] = [
    {
      role: 'Frontend Developer',
      tech: ['angular', 'typescript', 'javascript'],
      soft: ['communication', 'adaptabilite'],
      targetRoute: '/mes-resultats/progress'
    },
    {
      role: 'Full Stack Developer',
      tech: ['java', 'spring', 'sql'],
      soft: ['collaboration', 'problem solving'],
      targetRoute: '/mes-resultats'
    },
    {
      role: 'Tech Lead',
      tech: ['architecture', 'system design', 'code review'],
      soft: ['leadership', 'communication'],
      targetRoute: '/formations'
    }
  ];

  dashboardData: EmployeeDashboardResponse | null = null;
  loading = true;
  error: string | null = null;
  latestPrediction: PredictionResponse | null = null;
  generatingPrediction = false;

  currentDate = new Date();
  radarToggle: RadarToggle = 'tous';

  ngOnInit(): void {
    const currentUser = this.authService.getCurrentUser();
    if (currentUser?.id) {
      const userId = String(currentUser.id);
      this.dashboardService.getEmployeeDashboard(userId).pipe(
        timeout(15000)
      ).subscribe({
        next: (data) => {
          this.dashboardData = data;
          this.loading = false;
        },
        error: () => {
          this.error = 'Impossible de charger les données du tableau de bord.';
          this.loading = false;
        }
      });
      // Fetch the latest AI prediction independently
      this.dashboardService.getLatestPrediction(userId).subscribe({
        next: p => { this.latestPrediction = p; },
        error: () => { /* non-fatal, prediction card shows empty state */ }
      });
    } else {
      this.error = 'Utilisateur non authentifié.';
      this.loading = false;
    }
  }

  generatePrediction(): void {
    const currentUser = this.authService.getCurrentUser();
    if (!currentUser?.id || this.generatingPrediction) return;
    this.generatingPrediction = true;
    this.dashboardService.generatePrediction(String(currentUser.id)).subscribe({
      next: p => {
        this.latestPrediction = p;
        this.generatingPrediction = false;
      },
      error: (err: any) => {
        this.generatingPrediction = false;
        console.error('Erreur lors de la génération de la prédiction:', err);
        const errorMsg = err?.error?.message || 'Erreur inconnue';
        this.notificationService.error('Échec de l\'analyse IA : ' + errorMsg);
      }
    });
  }

  downloadPassport(): void {
    const currentUser = this.authService.getCurrentUser();
    if (!currentUser?.id) return;
    
    this.dashboardService.getTalentPassport(String(currentUser.id)).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `TalentPassport_${this.displayName.replace(' ', '_')}.pdf`;
        link.click();
        window.URL.revokeObjectURL(url);
      },
      error: (err) => {
        console.error('Export error:', err);
        this.notificationService.error('Erreur lors de l\'exportation du PDF.');
      }
    });
  }

  get displayName(): string {
    if (this.dashboardData) {
      return `${this.dashboardData.firstName} ${this.dashboardData.lastName}`;
    }
    const user = this.authService.getCurrentUser();
    return user ? `${user.prenom} ${user.nom}` : '';
  }

  get managerBannerMessage(): string {
    const latestEvent = this.feed[0];
    if (!latestEvent) {
      return 'Aucune activité récente. Lancez un test pour enrichir votre tableau de bord.';
    }
    return `${latestEvent.text} - ${latestEvent.date}`;
  }

  get pcmType(): string {
    const tests = this.testsSortedByDateDesc;
    const lastWithPcm = tests.find(t => t.personalityType);
    return lastWithPcm?.personalityType ?? 'Analyseur';
  }

  get pcmDescription(): string {
    const type = this.pcmType.toLowerCase();
    if (type.includes('analyseur')) return 'Logique et efficacité';
    if (type.includes('persévérant') || type.includes('perseverant')) return 'Convictions et engagement';
    if (type.includes('empathique')) return 'Écoute et harmonie';
    if (type.includes('énergiseur') || type.includes('energiseur')) return 'Spontanéité et énergie';
    if (type.includes('imagineur')) return 'Calme et imagination';
    if (type.includes('promoteur')) return 'Action et impact';
    return 'Profil en cours d\'analyse';
  }

  formatPercent(val: number | undefined | null): number {
    if (val == null) return 0;
    let v = val;
    if (v <= 1 && v > 0) v *= 100;
    return Math.round(v * 10) / 10;
  }

  formatRecommendation(value?: string | null): string {
    if (!value) return '';
    const trimmed = value.trim();
    if (!trimmed) return '';

    if (trimmed.startsWith('[') && trimmed.endsWith(']')) {
      const inner = trimmed.slice(1, -1).trim();
      if (!inner) return '';
      return inner
        .split(',')
        .map(part => part.trim().replace(/^['"]|['"]$/g, ''))
        .filter(Boolean)
        .join(', ');
    }

    if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
      try {
        const parsed = JSON.parse(trimmed);
        if (Array.isArray(parsed)) {
          return parsed.map(item => String(item).trim()).filter(Boolean).join(', ');
        }
        if (parsed && typeof parsed === 'object') {
          return Object.values(parsed)
            .map(item => String(item).trim())
            .filter(Boolean)
            .join(', ');
        }
      } catch {
        return trimmed;
      }
    }

    return trimmed;
  }

  get scoreMoyen(): number {
    return this.formatPercent(this.dashboardData?.scoreEvaluationMoyen);
  }

  get testsCompletes(): number {
    return this.dashboardData?.nombreTests ?? 0;
  }

  get streakHebdo(): number {
    const testDays = this.uniqueTestDaysDesc;
    if (testDays.length === 0) {
      return 0;
    }

    let streak = 1;
    let previousTs = testDays[0].getTime();
    const weekInMs = 7 * 24 * 60 * 60 * 1000;

    for (let i = 1; i < testDays.length; i++) {
      const currentTs = testDays[i].getTime();
      if (previousTs - currentTs <= weekInMs) {
        streak += 1;
        previousTs = currentTs;
        continue;
      }
      break;
    }

    return streak;
  }

  get softSkillsCount(): number {
    return this.dashboardData?.nombreSkillsSoft ?? 0;
  }

  get techSkillsCount(): number {
    return this.dashboardData?.nombreSkillsTech ?? 0;
  }

  get formationsActives(): number {
    return this.dashboardData?.nombreFormationsEnCours ?? 0;
  }

  get uniqueFormationsRecentes(): DashboardFormation[] {
    const formations = this.dashboardData?.formationsRecentes ?? [];
    const unique = new Map<string, DashboardFormation>();
    for (const f of formations) {
      const key = (f.titre ?? '').trim().toLowerCase();
      if (!key || unique.has(key)) {
        continue;
      }
      unique.set(key, f);
    }
    return Array.from(unique.values());
  }

  get activeFormation(): DashboardFormation | null {
    return this.uniqueFormationsRecentes.find(f => this.isFormationInProgress(f.statut)) ?? null;
  }

  get weeklyInsight(): WeeklyInsight {
    const strongestProgress = this.skillMomentum.find((item) => item.dir === 'up');
    const activeFormation = this.activeFormation;
    const weakestSkill = this.radarBottom3[0];

    const recommendationFromAi =
      this.formatRecommendation(this.dashboardData?.dernierePrediction?.recommandationSoft) ||
      this.formatRecommendation(this.dashboardData?.dernierePrediction?.recommandationTech);

    return {
      skillChange: strongestProgress
        ? `${strongestProgress.name} progresse de ${Math.abs(strongestProgress.delta)} pts.`
        : 'Aucune variation détectée récemment dans les soft skills.',
      formationProgress: activeFormation
        ? `${activeFormation.titre} (${activeFormation.progression ?? 0}% complété)`
        : 'Aucune formation active pour le moment.',
      recommendation:
        recommendationFromAi?.trim() ||
        (weakestSkill
          ? `Concentrez-vous sur ${weakestSkill} pour équilibrer votre profil.`
          : 'Passez un test pour recevoir une recommandation personnalisée.')
    };
  }

  get scoreEvolutionPoints(): { x: number, y: number, date: Date | string, score: number }[] {
    const tests = [...this.testsSortedByDateDesc].reverse();
    if (tests.length === 0) return [];

    const w = 300;
    const h = 120;

    if (tests.length === 1) {
      const score = this.formatPercent(tests[0].overallScore);
      return [{ x: w / 2, y: h - (score / 100 * h), date: tests[0].dateTest, score }];
    }

    return tests.map((t, i) => {
      const score = this.formatPercent(t.overallScore);
      return {
        x: (i / (tests.length - 1)) * w,
        y: h - (score / 100 * h),
        date: t.dateTest,
        score
      };
    });
  }

  get scoreEvolutionPath(): string {
    const pts = this.scoreEvolutionPoints;
    if (pts.length === 0) return '';
    return 'M ' + pts.map(p => `${p.x},${p.y}`).join(' L ');
  }

  get careerMatches(): CareerMatch[] {
    const skillIndex = this.buildSkillScoreIndex();
    const fallbackScore = this.scoreMoyen > 0 ? this.scoreMoyen : 50;

    return this.roleBlueprints
      .map((blueprint) => {
        const scores: number[] = [];
        const gaps: string[] = [];

        for (const tech of blueprint.tech) {
          const key = this.normalizeName(tech);
          const score = skillIndex.get(key) ?? Math.round(fallbackScore * 0.55);
          scores.push(score);
          if (score < 65) {
            gaps.push(this.toTitleCase(tech));
          }
        }

        for (const soft of blueprint.soft) {
          const key = this.normalizeName(soft);
          const score = skillIndex.get(key) ?? Math.round(fallbackScore * 0.6);
          scores.push(score);
          if (score < 65) {
            gaps.push(this.toTitleCase(soft));
          }
        }

        const averageScore = scores.length
          ? Math.round(scores.reduce((sum, value) => sum + value, 0) / scores.length)
          : Math.round(fallbackScore);

        return {
          role: blueprint.role,
          match: this.clamp(averageScore, 35, 99),
          gaps: gaps.slice(0, 3),
          targetRoute: blueprint.targetRoute
        };
      })
      .sort((a, b) => b.match - a.match)
      .slice(0, 3);
  }

  get radarEntries(): RadarEntry[] {
    const softEntries = Object.entries(this.latestSoftScores).map(([name, value]) => ({
      name,
      score: this.clamp(this.formatPercent(value), 0, 100),
      type: 'soft' as const
    }));

    const techEntries = (this.dashboardData?.topSkills ?? []).map((skill) => ({
      name: skill.nom,
      score: this.clamp(Math.round((skill.niveau ?? 0) * 20), 0, 100),
      type: this.normalizeSkillType(skill) === 'soft' ? 'soft' as const : 'tech' as const
    }));

    let entries = this.dedupeRadarEntries([...softEntries, ...techEntries]);

    if (this.radarToggle === 'tech') {
      entries = entries.filter(e => e.type === 'tech');
    }
    if (this.radarToggle === 'soft') {
      entries = entries.filter(e => e.type === 'soft');
    }

    return entries.slice(0, 6);
  }

  get radarTop3(): string[] {
    return [...this.radarEntries].sort((a, b) => b.score - a.score).slice(0, 3).map(e => e.name);
  }

  get radarBottom3(): string[] {
    return [...this.radarEntries].sort((a, b) => a.score - b.score).slice(0, 3).map(e => e.name);
  }

  get radarPolygonPoints(): string {
    const entries = this.radarEntries;
    if (entries.length === 0) return '';
    const cx = 100, cy = 100, radius = 80;
    return entries.map((entry, i) => {
      const angle = (Math.PI * 2 * i) / entries.length - Math.PI / 2;
      const r = radius * (entry.score / 100);
      return `${cx + r * Math.cos(angle)},${cy + r * Math.sin(angle)}`;
    }).join(' ');
  }

  getRadarGridPoints(level: number): string {
    const entries = this.radarEntries;
    if (entries.length === 0) return '';
    const cx = 100, cy = 100, radius = 80;
    return entries.map((_, i) => {
      const angle = (Math.PI * 2 * i) / entries.length - Math.PI / 2;
      return `${cx + radius * level * Math.cos(angle)},${cy + radius * level * Math.sin(angle)}`;
    }).join(' ');
  }

  get radarLabels(): { text: string; x: number; y: number; anchor: string }[] {
    const entries = this.radarEntries;
    const cx = 100, cy = 100, radius = 95;
    return entries.map((entry, i) => {
      const angle = (Math.PI * 2 * i) / entries.length - Math.PI / 2;
      const x = cx + radius * Math.cos(angle);
      const y = cy + radius * Math.sin(angle);
      let anchor = 'middle';
      if (Math.cos(angle) > 0.1) anchor = 'start';
      if (Math.cos(angle) < -0.1) anchor = 'end';
      return { text: entry.name, x, y, anchor };
    });
  }

  get skillMomentum(): SkillMomentumItem[] {
    const tests = this.testsSortedByDateDesc;
    if (tests.length < 2) {
      return [];
    }

    const currentScores = tests[0].softSkillsScores ?? {};
    const previousScores = tests[1].softSkillsScores ?? {};

    const skillNames = Array.from(
      new Set<string>([...Object.keys(currentScores), ...Object.keys(previousScores)])
    );

    return skillNames
      .map((name) => {
        const current = this.formatPercent(currentScores[name] ?? 0);
        const previous = this.formatPercent(previousScores[name] ?? 0);
        const delta = Math.round((current - previous) * 10) / 10;
        let dir: MomentumDirection = 'flat';
        if (delta > 0) {
          dir = 'up';
        } else if (delta < 0) {
          dir = 'down';
        }

        return {
          name,
          current,
          delta,
          dir
        };
      })
      .filter((item) => item.current > 0 || item.delta !== 0)
      .sort((a, b) => Math.abs(b.delta) - Math.abs(a.delta))
      .slice(0, 6);
  }

  get testActionText(): string {
    if (this.testsCompletes === 0) {
      return 'Démarrez votre première évaluation soft skills.';
    }
    return 'Continuez vos évaluations pour enrichir vos insights IA.';
  }

  get formationActionText(): string {
    const activeFormation = this.activeFormation;
    if (!activeFormation) {
      return 'Découvrez une formation recommandée pour votre profil.';
    }
    return `${activeFormation.titre} (${activeFormation.progression ?? 0}% complété).`;
  }

  get skillPracticeText(): string {
    const weakestSkill = this.radarBottom3[0];
    if (!weakestSkill) {
      return 'Travaillez vos compétences clés avec les exercices dédiés.';
    }
    return `Renforcez ${weakestSkill} avec un exercice ciblé.`;
  }

  get last3Tests(): TestSummary[] {
    return this.testsSortedByDateDesc.slice(0, 3);
  }

  get badges(): BadgeItem[] {
    const result: BadgeItem[] = [];
    const firstTest = this.testsSortedByDateDesc[this.testsSortedByDateDesc.length - 1];

    if (firstTest) {
      result.push({
        icon: '🏆',
        name: 'Premier test complété',
        date: this.formatDateLabel(firstTest.dateTest)
      });
    }

    if (this.streakHebdo >= 2) {
      result.push({
        icon: '🔥',
        name: `Streak ${this.streakHebdo} semaines`,
        date: 'Cette semaine'
      });
    }

    if (this.scoreMoyen >= 80) {
      result.push({
        icon: '🚀',
        name: 'Score moyen > 80%',
        date: 'Performance élevée'
      });
    }

    if ((this.dashboardData?.nombreFormationsTerminees ?? 0) > 0) {
      result.push({
        icon: '📚',
        name: 'Formation terminée',
        date: 'Objectif atteint'
      });
    }

    return result.slice(0, 4);
  }

  get lockedBadges(): LockedBadgeItem[] {
    const result: LockedBadgeItem[] = [];

    if (this.testsCompletes === 0) {
      result.push({
        icon: '🧪',
        name: 'Lancer votre analyse',
        condition: 'Compléter un premier test'
      });
    }

    if (this.scoreMoyen < 90) {
      result.push({
        icon: '⭐',
        name: 'Excellence',
        condition: 'Atteindre 90% de score moyen'
      });
    }

    if ((this.dashboardData?.nombreFormationsTerminees ?? 0) === 0) {
      result.push({
        icon: '🎓',
        name: 'Learning Milestone',
        condition: 'Terminer une formation'
      });
    }

    return result.slice(0, 3);
  }

  get feed(): FeedItem[] {
    const events: FeedItem[] = [];
    const latestTest = this.testsSortedByDateDesc[0];
    const latestPrediction = this.dashboardData?.dernierePrediction;
    const activeFormation = this.activeFormation;
    const completedFormation = this.uniqueFormationsRecentes.find((f) => this.isFormationCompleted(f.statut));

    if (latestTest) {
      events.push({
        icon: '✅',
        text: `Test enregistré (${this.formatPercent(latestTest.overallScore)}%)`,
        date: this.formatDateLabel(latestTest.dateTest),
        highlight: true
      });
    }

    if (activeFormation) {
      events.push({
        icon: '📚',
        text: `Formation en cours: ${activeFormation.titre}`,
        date: `${activeFormation.progression ?? 0}%`
      });
    }

    if (completedFormation) {
      events.push({
        icon: '🏁',
        text: `Formation terminée: ${completedFormation.titre}`,
        date: this.formatDateLabel(completedFormation.dateDebut || completedFormation.dateProposition)
      });
    }

    if (latestPrediction) {
      events.push({
        icon: '🤖',
        text: 'Prédiction IA mise à jour',
        date: this.formatDateLabel(latestPrediction.datePrediction)
      });
    }

    return events.slice(0, 4);
  }

  get testsSortedByDateDesc(): TestSummary[] {
    return [...(this.dashboardData?.testsRecents ?? [])].sort(
      (a, b) => this.getTimeValue(b.dateTest) - this.getTimeValue(a.dateTest)
    );
  }

  get latestSoftScores(): Record<string, number> {
    const latestTest = this.testsSortedByDateDesc[0];
    const scores = latestTest?.softSkillsScores ?? {};
    const normalized: Record<string, number> = {};

    for (const [key, value] of Object.entries(scores)) {
      normalized[key] = this.formatPercent(value);
    }

    return normalized;
  }

  isFormationInProgress(status?: string): boolean {
    return this.normalizeFormationStatus(status) === 'EN_COURS';
  }

  isFormationCompleted(status?: string): boolean {
    return this.normalizeFormationStatus(status) === 'TERMINEE';
  }

  formatFormationStatus(status?: string): string {
    const normalized = this.normalizeFormationStatus(status);
    if (normalized === 'EN_COURS') {
      return 'En cours';
    }
    if (normalized === 'TERMINEE') {
      return 'Terminée';
    }
    if (!normalized) {
      return 'Non défini';
    }
    return this.toTitleCase(normalized.replace(/_/g, ' ').toLowerCase());
  }

  private get uniqueTestDaysDesc(): Date[] {
    const dedup = new Set<string>();
    const days: Date[] = [];

    for (const test of this.testsSortedByDateDesc) {
      const date = this.toDate(test.dateTest);
      if (!date) {
        continue;
      }

      const normalized = new Date(date.getFullYear(), date.getMonth(), date.getDate());
      const key = normalized.toISOString();
      if (dedup.has(key)) {
        continue;
      }

      dedup.add(key);
      days.push(normalized);
    }

    return days;
  }

  private dedupeRadarEntries(entries: RadarEntry[]): RadarEntry[] {
    const map = new Map<string, RadarEntry>();
    for (const entry of entries) {
      if (!entry.name?.trim()) {
        continue;
      }

      const key = `${this.normalizeName(entry.name)}:${entry.type}`;
      const current = map.get(key);
      if (!current || entry.score > current.score) {
        map.set(key, entry);
      }
    }
    return Array.from(map.values());
  }

  private buildSkillScoreIndex(): Map<string, number> {
    const index = new Map<string, number>();

    for (const skill of this.dashboardData?.topSkills ?? []) {
      if (!skill.nom) {
        continue;
      }
      const key = this.normalizeName(skill.nom);
      const value = this.clamp(Math.round((skill.niveau ?? 0) * 20), 0, 100);
      const current = index.get(key) ?? 0;
      if (value > current) {
        index.set(key, value);
      }
    }

    for (const [name, value] of Object.entries(this.latestSoftScores)) {
      const key = this.normalizeName(name);
      const current = index.get(key) ?? 0;
      if (value > current) {
        index.set(key, value);
      }
    }

    return index;
  }

  private normalizeSkillType(skill: DashboardSkill): 'tech' | 'soft' {
    return String(skill.type || '').toUpperCase() === 'SOFT' ? 'soft' : 'tech';
  }

  private normalizeFormationStatus(status?: string): string {
    return String(status || '').toUpperCase().trim();
  }

  private normalizeName(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .trim();
  }

  private toTitleCase(value: string): string {
    return value
      .split(' ')
      .filter(Boolean)
      .map(word => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }

  private toDate(value: Date | string | undefined | null): Date | null {
    if (!value) {
      return null;
    }
    const date = value instanceof Date ? value : new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
  }

  private formatDateLabel(value: Date | string | undefined | null): string {
    const date = this.toDate(value);
    if (!date) {
      return 'Date non disponible';
    }
    return new Intl.DateTimeFormat('fr-FR', {
      day: '2-digit',
      month: 'short',
      year: 'numeric'
    }).format(date);
  }

  private getTimeValue(value: Date | string | undefined): number {
    return this.toDate(value)?.getTime() ?? 0;
  }

  private clamp(value: number, min: number, max: number): number {
    return Math.max(min, Math.min(max, value));
  }
}
