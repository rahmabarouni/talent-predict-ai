import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../../auth/services/auth.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { TestApiService } from '../../../skill-test/services/test-api.service';
import { SkillsService } from '../../../skills/services/skills.service';
import { jsPDF } from 'jspdf';
import html2canvas from 'html2canvas';

@Component({
  selector: 'app-tech-results',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './tech-results.component.html',
  styleUrl: './tech-results.component.scss'
})
export class TechResultsComponent implements OnInit {
  private router = inject(Router);
  private authService = inject(AuthService);
  private skillsService = inject(SkillsService);
  private notify = inject(NotificationService);
  private testApi = inject(TestApiService);

  quizResult: any = null;
  githubResult: any = null;
  detectedSkills: string[] = [];
  techSkills: any[] = [];
  recentTests: any[] = [];
  loading = true;
  exportingPdf = false;
  pdfMode = false;
  activeTab: 'overview' | 'skills' | 'github' | 'gaps' = 'overview';

  // AI Forensics
  githubDeepResult: any = null;
  loadingGithubDeep = false;


  ngOnInit(): void {
    // 1. Load quiz result from router state
    const nav = this.router.getCurrentNavigation();
    this.quizResult = nav?.extras?.state?.['result'] ?? null;

    // 2. Fall back to sessionStorage (e.g. after page refresh or direct navigation)
    if (!this.quizResult) {
      const stored = sessionStorage.getItem('latestTechResult');
      if (stored) {
        try {
          const parsed = JSON.parse(stored);
          // Load the full quizResult object from the new storage format
          this.quizResult = parsed;
          
          // If the parsed object is missing critical fields (old session format fallback)
          if (this.quizResult && this.quizResult.finalScore === undefined) {
            this.quizResult = {
              finalScore: parsed.overall_score ?? 0,
              passed: parsed.passed ?? false,
              skillScores: parsed.skill_scores ?? {},
              skillGapAnalysis: [],
              mcqSummary: '',
              headline: parsed.overall_score >= 60
                ? 'Profil valide pour un entretien technique avancé.'
                : 'Des bases présentes, mais un renforcement ciblé est recommandé.',
            };
          }
        } catch { /* ignore parse errors */ }
      }
    }

    // 3. Load context from intake
    const ctx = sessionStorage.getItem('techIntakeContext');
    if (ctx) {
      try {
        const parsed = JSON.parse(ctx);
        const aiAnalysis = parsed.aiAnalysis;
        
        if (aiAnalysis && !parsed.githubResult) {
            // Map aiAnalysis to githubResult for backwards compatibility with the UI
            this.githubResult = {
                username: aiAnalysis.candidate || parsed.githubUsername,
                data: {
                    summary: aiAnalysis.summary,
                    code_complexity_estimate: "N/A",
                    verified_skills: aiAnalysis.skills ? aiAnalysis.skills.map((s: any) => ({
                        skill: s.name,
                        confidence: s.level || 'Moyen',
                        evidence: `Source: ${(s.sources || []).join(', ')}`
                    })) : [],
                    missing_claimed_skills: aiAnalysis.job_match?.missing_skills || []
                }
            };
        } else {
            this.githubResult = parsed.githubResult ?? null;
        }
        
        this.detectedSkills = parsed.detectedSkills ?? [];


        
        const ghUser = this.githubResult?.username || parsed.githubUsername;
        if (ghUser) {
            this.runGithubDeepAnalysis(ghUser, aiAnalysis);
        }
      } catch {}
    }

    this.loadUserData();
  }

  private loadUserData(): void {
    const user = this.authService.getCurrentUser();
    if (!user?.id) { this.loading = false; return; }
    const userId = String(user.id);

    this.skillsService.getUserSkills(userId).subscribe({
      next: (skills: any[]) => {
        this.techSkills = skills
          .filter((s: any) => s.type === 'TECH' || (s.type as string) === 'TECH')
          .sort((a: any, b: any) => (b.niveau ?? 0) - (a.niveau ?? 0));
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });

    // Benchmark history disabled
    /*
    this.benchmarkService.progress(userId).subscribe({
      next: (rows) => {
        this.recentTests = [...rows]
          .sort((a: any, b: any) => new Date(b.taken_at).getTime() - new Date(a.taken_at).getTime())
          .slice(0, 5);
      }
    });
    */
  }

  get overallScore(): number {
    return this.quizResult?.finalScore ?? 0;
  }

  get scoreLabel(): string {
    const s = this.overallScore;
    if (s >= 80) return 'Excellent';
    if (s >= 65) return 'Bon';
    if (s >= 50) return 'Moyen';
    return 'À améliorer';
  }

  get scoreColor(): string {
    const s = this.overallScore;
    if (s >= 80) return '#22c55e';
    if (s >= 65) return '#3b82f6';
    if (s >= 50) return '#f59e0b';
    return '#ef4444';
  }

  get strokeDash(): string {
    const pct = Math.min(100, Math.max(0, this.overallScore));
    const circ = 2 * Math.PI * 54;
    return `${(pct / 100) * circ} ${circ}`;
  }

  get skillGaps(): { skill: string; analyzed: number; tested: number; delta: number }[] {
    return this.quizResult?.skillGapAnalysis ?? [];
  }

  get gapBelow(): { skill: string; analyzed: number; tested: number; delta: number }[] {
    return this.skillGaps.filter(g => g.delta <= -10);
  }

  get gapAbove(): { skill: string; analyzed: number; tested: number; delta: number }[] {
    return this.skillGaps.filter(g => g.delta >= 10);
  }

  exportPdf(): void {
    if (this.exportingPdf) return;
    this.exportingPdf = true;
    this.pdfMode = true;
    this.notify.info('Génération du PDF en cours. Veuillez patienter...');

    setTimeout(() => {
      const element = document.getElementById('print-area');
      if (!element) {
        this.exportingPdf = false;
        this.pdfMode = false;
        this.notify.error('Erreur lors de la génération du PDF.');
        return;
      }

      html2canvas(element, { scale: 2, useCORS: true, logging: false }).then(canvas => {
        const imgData = canvas.toDataURL('image/png');
        const pdf = new jsPDF('p', 'mm', 'a4');
        const pdfWidth = pdf.internal.pageSize.getWidth();
        const pdfHeight = (canvas.height * pdfWidth) / canvas.width;
        
        pdf.addImage(imgData, 'PNG', 0, 0, pdfWidth, pdfHeight);
        
        // Add footer with app name and export date
        const pageCount = (pdf.internal as any).getNumberOfPages();
        for (let i = 1; i <= pageCount; i++) {
          pdf.setPage(i);
          pdf.setFontSize(10);
          pdf.setTextColor(100);
          const footerText = `TalentPredict - Généré le ${new Date().toLocaleDateString('fr-FR')} - Page ${i}/${pageCount}`;
          pdf.text(footerText, pdfWidth / 2, pdf.internal.pageSize.getHeight() - 10, { align: 'center' });
        }

        const candidateName = this.githubResult?.username || 'Candidat';
        pdf.save(`TalentPredict_Rapport_${candidateName}.pdf`);
        
        this.exportingPdf = false;
        this.pdfMode = false;
        this.notify.success('PDF exporté avec succès !');
      }).catch(err => {
        console.error('PDF Generation Error:', err);
        this.exportingPdf = false;
        this.pdfMode = false;
        this.notify.error('Échec de la génération du PDF.');
      });
    }, 500); // Give Angular time to render pdfMode changes
  }

  retakeTest(): void { this.router.navigate(['/competences']); }
  goToMesResultats(): void { this.router.navigate(['/mes-resultats']); }

  getScoreClass(score: number): string {
    if (score >= 75) return 'high';
    if (score >= 50) return 'mid';
    return 'low';
  }

  // ── AI Forensics ───────────────────────────────────────────────

  runGithubDeepAnalysis(username: string, aiAnalysis?: any): void {
    const user = this.authService.getCurrentUser();
    if (!user || !username) return;

    this.loadingGithubDeep = true;
    this.testApi.analyzeGithubDeep({
      github_username: username,
      candidate_id: user.id,
      github_data: aiAnalysis || {}
    }).subscribe({
      next: (res: any) => {
        this.githubDeepResult = res;
        this.loadingGithubDeep = false;
      },
      error: (err) => {
        this.loadingGithubDeep = false;
        this.githubDeepResult = { error: "Erreur lors de l'analyse profonde: " + (err?.message || "Service injoignable") };
      }
    });
  }


}
