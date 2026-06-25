import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../../auth/services/auth.service';
import { SkillsService } from '../../../skills/services/skills.service';
import { SoftSkillsService } from '../../../evaluation/services/soft-skills.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { FormsModule } from '@angular/forms';
import { AuthUser } from '../../../auth/models/user.model';
import { SoftSkillsResult } from '../../../evaluation/models/soft-skills.model';
import { SkillResponse } from '../../../skills/models/skill.model';
import { environment } from '../../../../../environments/environment';

type TechSkill = SkillResponse & {
  delta: number;
  score100: number;
};

interface TechTestResult {
  finalScore: number;
  overall_score?: number; // legacy fallback
  passed: boolean;
  skillScores: Record<string, number>;
}

interface CandidateProgressItem {
  test_type?: string;
  overall_score?: number;
  skill_scores?: Record<string, number>;
  taken_at: string;
  passed?: boolean;
}


@Component({
  selector: 'app-mes-resultats',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './mes-resultats.component.html',
  styleUrls: ['./mes-resultats.component.scss']
})
export class MesResultatsComponent implements OnInit {
  Math = Math;
  private router = inject(Router);
  private http = inject(HttpClient);
  private authService = inject(AuthService);
  private skillsService = inject(SkillsService);
  private softSkillsService = inject(SoftSkillsService);
  private notify = inject(NotificationService);

  currentUser: AuthUser | null = null;
  loadingTech = true;
  loadingSoft = true;

  // Tech data
  techSkills: TechSkill[] = [];
  latestTechTest: TechTestResult | null = null;
  githubResult: any = null;

  // Soft data
  softResult: SoftSkillsResult | null = null;

  // LinkedIn analysis from profile
  linkedinUrl = '';

  // New states
  shareableLinkVisible = false;
  isProfilePublic = false;

  get formattedTechScore(): number {
    let score = this.latestTechTest?.finalScore ?? this.latestTechTest?.overall_score ?? 0;
    if (score <= 1 && score > 0) score *= 100;
    return Math.round(score * 10) / 10;
  }

  get formattedSoftScore(): number {
    let score = this.softResult?.overallScore ?? 0;
    if (score <= 10 && score > 0) score *= 10;
    return Math.round(score * 10) / 10;
  }

  // Overall readiness
  get overallReadiness(): number {
    const techScore = this.formattedTechScore;
    const softScore = this.formattedSoftScore;

    let total = 0;
    let count = 0;
    if (techScore > 0) { total += techScore; count++; }
    if (softScore > 0) { total += softScore; count++; }

    return count > 0 ? Math.round(total / count) : 0;
  }

  get readinessLabel(): string {
    const r = this.overallReadiness;
    if (r >= 80) return 'Prêt pour un entretien senior';
    if (r >= 65) return 'Profil solide — quelques axes à consolider';
    if (r >= 50) return 'Profil en développement';
    return 'Débutez votre parcours d\'évaluation';
  }

  get readinessColor(): string {
    const r = this.overallReadiness;
    if (r >= 80) return '#22c55e';
    if (r >= 50) return '#f59e0b';
    return '#ef4444';
  }

  get strokeDash(): string {
    const pct = Math.min(100, Math.max(0, this.overallReadiness));
    const circ = 2 * Math.PI * 54;
    return `${(pct / 100) * circ} ${circ}`;
  }

  get techGaps(): string[] {
    const ctx = sessionStorage.getItem('techIntakeContext');
    if (!ctx) return [];
    try {
      const parsed = JSON.parse(ctx);
      return parsed.githubResult?.data?.missing_claimed_skills ?? [];
    } catch { return []; }
  }

  get softGaps(): string[] {
    return this.softResult?.top3Weaknesses ?? [];
  }

  readonly softSkillIcons: Record<string, string> = {
    communication: '💬', discipline: '⏰', curiosity: '🔍',
    collaboration: '🤝', ownership: '🎯', leadership: '👑', adaptability: '🌱', problem_solving: '🧩'
  };

  get pcmType(): string {
    return this.softResult?.personalityType ?? '';
  }

  get pcmDescription(): string {
    const type = this.pcmType.toLowerCase();
    if (type.includes('analyseur')) return 'Logique, structure et décision basée sur les faits.\nCherche l\'efficacité.';
    if (type.includes('persévérant') || type.includes('perseverant')) return 'Convictions fortes, engagement et sens des responsabilités.\nRecherche le sens.';
    if (type.includes('empathique')) return 'Écoute active, sensibilité relationnelle et coopération.\nPrivilégie l\'harmonie.';
    if (type.includes('énergiseur') || type.includes('energiseur')) return 'Spontanéité, énergie sociale et communication vivante.\nRecherche le plaisir.';
    if (type.includes('imagineur')) return 'Réflexion profonde, calme et vision imaginative.\nA besoin de solitude.';
    if (type.includes('promoteur')) return 'Orientation action, adaptation rapide et impact concret.\nRecherche le défi.';
    return 'Profil en attente d\'analyse détaillée.';
  }

  // Career Match data
  careerMatches = [
    { role: 'Frontend Developer', match: 87, gaps: ['Angular Advanced', 'Testing (Jest)', 'Leadership'], reason: 'Excellente maîtrise de l\'écosystème frontend avec une base technique solide.' },
    { role: 'Full Stack Developer', match: 72, gaps: ['Node.js', 'System Design', 'Communication', 'DevOps'], reason: 'Compétences polyvalentes, nécessite une montée en puissance sur le backend.' },
    { role: 'Tech Lead', match: 55, gaps: ['Team Management', 'Architecture Système', 'Agile / Scrum', 'Gestion de conflits'], reason: 'Potentiel identifié, mais nécessite plus d\'expérience en gestion d\'équipe.' }
  ];

  ngOnInit(): void {
    this.currentUser = this.authService.getCurrentUser() as AuthUser;
    if (!this.currentUser?.id) return;
    const userId = this.currentUser.id;

    try {
      const cached = sessionStorage.getItem('userProfileUrls');
      if (cached) {
        const urls = JSON.parse(cached);
        this.linkedinUrl = urls.linkedinUrl ?? '';
      }
    } catch { }

    this.skillsService.getUserSkills(userId).subscribe({
      next: (skills: SkillResponse[]) => {
        this.techSkills = skills
          .filter((s: SkillResponse) => s.type === 'TECH' || (s.type as string) === 'TECH')
          .sort((a: any, b: any) => (b.niveau ?? 0) - (a.niveau ?? 0))
          .map((s: SkillResponse) => ({
            ...s,
            delta: Math.floor(Math.random() * 3) - 1,
            score100: Math.round(((s.niveau ?? 0) / 5) * 100)
          }))
          .slice(0, 6);
        this.loadingTech = false;
      },
      error: () => { this.loadingTech = false; }
    });

    if (!this.latestTechTest) {
      const storedTech = sessionStorage.getItem('latestTechResult');
      if (storedTech) {
        try { this.latestTechTest = JSON.parse(storedTech); } catch { }
      }
    }

    if (!this.latestTechTest) {
      this.loadLatestTechTest(userId);
    }

    const stored = sessionStorage.getItem('softSkillsResult');
    if (stored) {
      try { this.softResult = JSON.parse(stored); } catch { }
    }
    this.softSkillsService.getLastAnalysis().subscribe({
      next: (res) => { if (res) this.softResult = res; this.loadingSoft = false; },
      error: () => { this.loadingSoft = false; }
    });

    const ctx = sessionStorage.getItem('techIntakeContext');
    if (ctx) {
      try { this.githubResult = JSON.parse(ctx)?.githubResult ?? null; } catch { }
    }
  }

  getSoftSkillEntries(): { name: string; score: number, delta: number }[] {
    if (!this.softResult?.mergedSoftSkills) return [];
    return Object.entries(this.softResult.mergedSoftSkills)
      .sort((a: any, b: any) => b[1] - a[1])
      .map(([name, score]) => ({
        name,
        score: Number(score),
        delta: Math.floor(Math.random() * 3) - 1 // Mock delta
      }));
  }

  // --- Radar Chart Helpers ---
  get radarPolygonPoints(): string {
    const entries = this.getSoftSkillEntries().slice(0, 5);
    if (entries.length === 0) return '';
    const cx = 130, cy = 130, radius = 90;

    return entries.map((entry, i) => {
      const angle = (Math.PI * 2 * i) / entries.length - Math.PI / 2;
      const normalizedScore = Math.max(0.1, entry.score / 10);
      const r = radius * normalizedScore;
      const x = cx + r * Math.cos(angle);
      const y = cy + r * Math.sin(angle);
      return `${x},${y}`;
    }).join(' ');
  }

  get radarAxisPoints(): { x2: number; y2: number }[] {
    const entries = this.getSoftSkillEntries().slice(0, 5);
    if (entries.length === 0) return [];
    const cx = 130, cy = 130, radius = 90;

    return entries.map((_, i) => {
      const angle = (Math.PI * 2 * i) / entries.length - Math.PI / 2;
      return {
        x2: cx + radius * Math.cos(angle),
        y2: cy + radius * Math.sin(angle)
      };
    });
  }

  getRadarGridPoints(level: number): string {
    const entries = this.getSoftSkillEntries().slice(0, 5);
    if (entries.length === 0) return '';
    const cx = 130, cy = 130, radius = 90;
    return entries.map((_, i) => {
      const angle = (Math.PI * 2 * i) / entries.length - Math.PI / 2;
      const x = cx + radius * level * Math.cos(angle);
      const y = cy + radius * level * Math.sin(angle);
      return `${x},${y}`;
    }).join(' ');
  }

  getRadarPointX(score: number, index: number, total: number): number {
    const cx = 130, radius = 90;
    const angle = (Math.PI * 2 * index) / total - Math.PI / 2;
    return cx + radius * (score / 10) * Math.cos(angle);
  }

  getRadarPointY(score: number, index: number, total: number): number {
    const cy = 130, radius = 90;
    const angle = (Math.PI * 2 * index) / total - Math.PI / 2;
    return cy + radius * (score / 10) * Math.sin(angle);
  }

  get radarLabels(): { text: string; x: number; y: number; anchor: string }[] {
    const entries = this.getSoftSkillEntries().slice(0, 5);
    if (entries.length === 0) return [];
    const cx = 130, cy = 130, radius = 110;

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

  getScoreColor(score: number, max = 10): string {
    const pct = max === 10 ? score * 10 : score;
    if (pct >= 80) return '#22c55e';
    if (pct >= 50) return '#f59e0b';
    return '#ef4444';
  }

  getDotColorClass(score100: number): string {
    if (score100 >= 80) return 'dot-green';
    if (score100 >= 50) return 'dot-orange';
    return 'dot-red';
  }

  toggleShareProfile() {
    this.shareableLinkVisible = !this.shareableLinkVisible;
  }

  copyShareLink() {
    navigator.clipboard.writeText(window.location.origin + '/public/profile/' + this.currentUser?.id);
    this.notify.success('Lien copié dans le presse-papiers');
  }

  exportGlobalPdf(): void {
    this.notify.info('Génération du rapport PDF complet en cours...');
    setTimeout(() => {
      window.print();
    }, 600);
  }

  private loadLatestTechTest(userId: string): void {
    const url = `${environment.apiUrl}/candidates/${userId}/progress`;
    this.http.get<CandidateProgressItem[]>(url).subscribe({
      next: (rows) => {
        const latest = rows?.[0];
        if (!latest) return;
        const score = Number(latest.overall_score ?? 0);
        const skillScores = (latest.skill_scores && typeof latest.skill_scores === 'object')
          ? latest.skill_scores as Record<string, number>
          : {};
        this.latestTechTest = {
          finalScore: Number.isFinite(score) ? score : 0,
          overall_score: Number.isFinite(score) ? score : 0,
          passed: !!latest.passed,
          skillScores
        };
        try { sessionStorage.setItem('latestTechResult', JSON.stringify(this.latestTechTest)); } catch { }
      },
      error: () => { }
    });
  }

  goToTech(): void { this.router.navigate(['/competences']); }
  goToTechResults(): void { this.router.navigate(['/competences/results']); }
  goToSoft(): void { this.router.navigate(['/evaluation/intro']); }
  goToSoftResults(): void { this.router.navigate(['/evaluation/results']); }
  goToFormations(): void { this.router.navigate(['/formations']); }
  goToProgress(): void { this.router.navigate(['/mes-resultats/progress']); }
}
