import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, of, catchError, Subscription, finalize } from 'rxjs';
import { TestStateService, McqQuestion } from '../../services/test-state.service';
import { TestApiService } from '../../services/test-api.service';
import { NotificationService } from '../../../../core/services/notification.service';


type GapStatus = 'above' | 'aligned' | 'below';

interface SkillGapEntry {
  skill: string;
  analyzedScore: number;
  testScore: number;
  delta: number;
  status: GapStatus;
  recommendation: string;
}

interface QuizResultViewModel {
  finalScore: number;
  passed: boolean;
  headline: string;
  mcqScore: number;
  codeScore: number;
  confidenceAccuracy: number;
  speedScore: number;
  strongestSkills: string[];
  weakSkills: string[];
  skillScores: Record<string, number>;
  skillGapAnalysis: SkillGapEntry[];
  gapSummary: string;
  mcqSummary: string;
  codeFeedback: string;
  codeBreakdown: unknown;
  rawMcq: Record<string, unknown>;
  rawCode: Record<string, unknown> | null;
  questionCount: number;
  answeredCount: number;
  totalDurationSeconds: number;
}

interface McqSubmittedAnswer {
  question_id: string;
  skill: string;
  selected: string;
  correct: string;
  confidence: string;
  time_spent_seconds: number;
  difficulty: string;
}

@Component({
  selector: 'app-skill-test-quiz',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './skill-test-quiz.component.html',
  styleUrl: './skill-test-quiz.component.scss'
})
export class SkillTestQuizComponent implements OnInit, OnDestroy {
  private state = inject(TestStateService);
  private testApi = inject(TestApiService);
  private router = inject(Router);
  private notify = inject(NotificationService);


  questions: McqQuestion[] = [];
  readonly optionKeys = ['A', 'B', 'C', 'D'] as const;

  answers: Record<string, string> = {};
  confidence: Record<string, string> = {};
  private startedAt = 0;
  private codeStartedAt = 0;
  private timerHandle: ReturnType<typeof setInterval> | null = null;
  private perQuestionElapsedSeconds: Record<string, number> = {};

  currentIndex = 0;
  overallRemainingSec = 0;
  questionRemainingSec = 0;

  codeChallenge: any = null;
  codeSubmission = '';
  codeLoading = false;
  codeError = '';
  codeHintsUsed = 0;
  randomCodeSkill = '';
  private submitSub: Subscription | null = null;
  private submitWatchdogHandle: ReturnType<typeof setTimeout> | null = null;
  private readonly submitHardTimeoutMs = 30_000;

  result: QuizResultViewModel | null = null;
  submitting = false;
  exportingPdf = false;

  ngOnInit(): void {
    this.questions = this.state.questions();
    if (this.questions.length === 0) {
      const fromCompetences = !!sessionStorage.getItem('techIntakeContext');
      void this.router.navigate([fromCompetences ? '/competences' : '/skill-test']);
      return;
    }
    this.startedAt = Date.now();
    for (const q of this.questions) {
      this.confidence[q.id] = q.confidence_required ? 'high' : 'medium';
      this.perQuestionElapsedSeconds[q.id] = 0;
    }



    this.initializeTimers();
    this.loadRandomCodeChallenge();
  }

  ngOnDestroy(): void {

    this.stopTimerLoop();
    this.clearSubmitWatchdog();
    this.submitSub?.unsubscribe();
    this.submitSub = null;
  }



  get currentQuestion(): McqQuestion | null {
    return this.questions[this.currentIndex] ?? null;
  }

  get isFirstQuestion(): boolean {
    return this.currentIndex === 0;
  }

  get isLastQuestion(): boolean {
    return this.currentIndex >= this.questions.length - 1;
  }

  get answeredCount(): number {
    return this.questions.filter(q => !!this.answers[q.id]).length;
  }

  get questionProgressPercent(): number {
    if (this.questions.length === 0) {
      return 0;
    }
    return Math.round(((this.currentIndex + 1) / this.questions.length) * 100);
  }

  get completionPercent(): number {
    if (this.questions.length === 0) {
      return 0;
    }
    return Math.round((this.answeredCount / this.questions.length) * 100);
  }

  get formattedOverallTimer(): string {
    return this.formatSeconds(this.overallRemainingSec);
  }

  get formattedQuestionTimer(): string {
    return this.formatSeconds(this.questionRemainingSec);
  }

  previousQuestion(): void {
    if (this.isFirstQuestion) {
      return;
    }
    this.currentIndex -= 1;
    this.syncQuestionTimer();
  }

  nextQuestion(): void {
    if (this.isLastQuestion) {
      return;
    }
    this.currentIndex += 1;
    this.syncQuestionTimer();
  }

  goToQuestion(index: number): void {
    if (index < 0 || index >= this.questions.length) {
      return;
    }
    this.currentIndex = index;
    this.syncQuestionTimer();
  }

  get hasCodeChallenge(): boolean {
    return !!this.codeChallenge;
  }

  get codeHintsLeft(): number {
    if (!this.codeChallenge?.hints?.length) {
      return 0;
    }
    return Math.max(0, this.codeChallenge.hints.length - this.codeHintsUsed);
  }

  exportPdfReport(): void {
    const data = document.getElementById('pdf-quiz-result');
    if (!data) return;
    
    this.exportingPdf = true;
    this.notify.info("Génération du rapport PDF visuel en cours...");

    import('html2canvas').then(html2canvasModule => {
      const html2canvas = html2canvasModule.default;
      import('jspdf').then(jspdfModule => {
        const jsPDF = jspdfModule.jsPDF;

        // Temporarily hide the action buttons
        const actionButtons = document.querySelector('.result-actions') as HTMLElement;
        const originalDisplay = actionButtons ? actionButtons.style.display : '';
        if (actionButtons) {
          actionButtons.style.display = 'none';
        }

        html2canvas(data, {
          scale: 2,
          useCORS: true,
          logging: false
        }).then(canvas => {
          if (actionButtons) {
            actionButtons.style.display = originalDisplay;
          }

          const imgWidth = 210;
          const pageHeight = 297;
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
          
          pdf.save(`TalentPredict_QuizResult_${new Date().getTime()}.pdf`);
          
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

  private initializeTimers(): void {
    this.overallRemainingSec = Math.max(240, this.questions.length * 55);
    this.syncQuestionTimer();
    this.startTimerLoop();
  }

  private startTimerLoop(): void {
    this.stopTimerLoop();
    this.timerHandle = setInterval(() => {
      if (this.submitting || this.result) {
        return;
      }

      const current = this.currentQuestion;
      if (current) {
        this.perQuestionElapsedSeconds[current.id] = (this.perQuestionElapsedSeconds[current.id] ?? 0) + 1;
        this.syncQuestionTimer();
      }

      if (this.overallRemainingSec > 0) {
        this.overallRemainingSec -= 1;
      }

      if (this.overallRemainingSec <= 0) {
        this.notify.warning('Temps total ecoule. Soumission automatique du test.');
        this.submit();
        return;
      }

      if (this.questionRemainingSec <= 0) {
        if (this.isLastQuestion) {
          this.notify.info('Derniere question expiree. Soumission automatique.');
          this.submit();
          return;
        }
        this.notify.info('Temps ecoule pour cette question. Passage a la suivante.');
        this.nextQuestion();
      }
    }, 1000);
  }

  private stopTimerLoop(): void {
    if (this.timerHandle) {
      clearInterval(this.timerHandle);
      this.timerHandle = null;
    }
  }

  private syncQuestionTimer(): void {
    const current = this.currentQuestion;
    if (!current) {
      this.questionRemainingSec = 0;
      return;
    }
    const limit = this.getQuestionTimeLimit(current);
    const spent = this.perQuestionElapsedSeconds[current.id] ?? 0;
    this.questionRemainingSec = Math.max(0, limit - spent);
  }

  private getQuestionTimeLimit(question: McqQuestion): number {
    const level = (question.difficulty ?? 'medium').toLowerCase();
    if (level === 'hard') {
      return 90;
    }
    if (level === 'easy') {
      return 55;
    }
    return 70;
  }

  private formatSeconds(totalSeconds: number): string {
    const safe = Math.max(0, Math.floor(totalSeconds));
    const m = Math.floor(safe / 60).toString().padStart(2, '0');
    const s = (safe % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  }

  private clearSubmitWatchdog(): void {
    if (this.submitWatchdogHandle) {
      clearTimeout(this.submitWatchdogHandle);
      this.submitWatchdogHandle = null;
    }
  }

  private startSubmitWatchdog(): void {
    this.clearSubmitWatchdog();
    this.submitWatchdogHandle = setTimeout(() => {
      if (!this.submitting) {
        return;
      }

      this.submitSub?.unsubscribe();
      this.submitSub = null;
      this.submitting = false;
      this.startTimerLoop();
      this.notify.error('Evaluation bloquee ou trop lente. Reessayez dans quelques instants.');
    }, this.submitHardTimeoutMs);
  }

  private loadRandomCodeChallenge(): void {
    const userId = this.state.candidateId();
    if (!userId) {
      return;
    }

    const uniqueSkills = Array.from(new Set(this.questions.map(q => q.skill).filter(Boolean)));
    if (uniqueSkills.length === 0) {
      return;
    }

    this.randomCodeSkill = uniqueSkills[Math.floor(Math.random() * uniqueSkills.length)];
    this.codeLoading = true;
    this.codeError = '';
    this.codeChallenge = null;
    this.codeSubmission = '';
    this.codeHintsUsed = 0;

    this.testApi.generateCodeChallenge({
      skill: this.randomCodeSkill,
      level: this.state.level(),
      candidate_id: userId
    }).subscribe({
      next: (res: any) => {
        this.codeLoading = false;
        const payload = res?.data ?? res;

        if (!payload?.challenge_id) {
          this.codeError = 'Le challenge de code n a pas pu etre initialise.';
          return;
        }

        this.codeChallenge = payload;
        this.codeSubmission = payload?.starter_code ?? '';
        this.codeStartedAt = Date.now();
      },
      error: err => {
        this.codeLoading = false;
        if (err?.name === 'TimeoutError') {
          this.codeError = 'Generation du code challenge trop lente. Reessayez dans quelques instants.';
          return;
        }
        this.codeError = err?.error?.message ?? 'Generation du code challenge indisponible pour le moment.';
      }
    });
  }

  useCodeHint(): void {
    if (!this.codeChallenge?.hints?.length || this.codeHintsUsed >= this.codeChallenge.hints.length) {
      return;
    }

    const hint = this.codeChallenge.hints[this.codeHintsUsed];
    this.codeHintsUsed += 1;
    this.notify.info(`Indice ${this.codeHintsUsed}: ${hint}`);
  }

  private numberValue(value: unknown, fallback = 0): number {
    const n = Number(value);
    return Number.isFinite(n) ? n : fallback;
  }

  private findScoreInsensitive(skill: string, scores: Record<string, number>): number {
    if (Object.prototype.hasOwnProperty.call(scores, skill)) {
      return this.numberValue(scores[skill]);
    }
    const key = Object.keys(scores).find(k => k.toLowerCase() === skill.toLowerCase());
    if (!key) {
      return 0;
    }
    return this.numberValue(scores[key]);
  }

  private gapStatus(delta: number): GapStatus {
    if (delta >= 10) {
      return 'above';
    }
    if (delta <= -10) {
      return 'below';
    }
    return 'aligned';
  }

  private gapRecommendation(skill: string, status: GapStatus, delta: number): string {
    if (status === 'above') {
      return `Progression solide en ${skill}: continuez avec des scenarios avances.`;
    }
    if (status === 'below') {
      return `Renforcer ${skill}: revisez les fondamentaux et pratiquez des cas reels.`;
    }
    return `${skill} est coherent avec l'analyse initiale (ecart ${delta >= 0 ? '+' : ''}${delta}).`;
  }

  private buildSkillGapAnalysis(skillScores: Record<string, number>): SkillGapEntry[] {
    const analyzedScores = this.state.analyzedSkillScores();
    const canonicalSkillByLower = new Map<string, string>();

    for (const key of Object.keys(analyzedScores)) {
      canonicalSkillByLower.set(key.toLowerCase(), key);
    }
    for (const key of Object.keys(skillScores)) {
      canonicalSkillByLower.set(key.toLowerCase(), key);
    }

    const entries: SkillGapEntry[] = [];
    for (const skill of canonicalSkillByLower.values()) {
      const analyzed = Math.round(this.findScoreInsensitive(skill, analyzedScores));
      const tested = Math.round(this.findScoreInsensitive(skill, skillScores));
      if (analyzed <= 0 && tested <= 0) {
        continue;
      }
      const delta = tested - analyzed;
      const status = this.gapStatus(delta);
      entries.push({
        skill,
        analyzedScore: analyzed,
        testScore: tested,
        delta,
        status,
        recommendation: this.gapRecommendation(skill, status, delta)
      });
    }

    return entries.sort((a, b) => Math.abs(b.delta) - Math.abs(a.delta));
  }

  private buildGapSummary(gaps: SkillGapEntry[]): string {
    if (gaps.length === 0) {
      return 'Aucun ecart exploitable entre analyse de profil et test technique.';
    }

    const strongest = gaps.filter(g => g.status === 'above').slice(0, 2).map(g => g.skill);
    const weakest = gaps.filter(g => g.status === 'below').slice(0, 2).map(g => g.skill);

    if (strongest.length > 0 && weakest.length > 0) {
      return `Surperformance observee en ${strongest.join(', ')}. Priorite de progression sur ${weakest.join(', ')}.`;
    }
    if (strongest.length > 0) {
      return `Le test confirme une progression notable en ${strongest.join(', ')}.`;
    }
    if (weakest.length > 0) {
      return `Des ecarts negatifs apparaissent surtout en ${weakest.join(', ')}. Un plan de renforcement est recommande.`;
    }
    return 'Profil global coherent entre analyse initiale et resultats du test.';
  }

  private buildLocalFallbackMcqEvaluation(payloadAnswers: McqSubmittedAnswer[], elapsedSeconds: number): Record<string, unknown> {
    const bySkill = new Map<string, { total: number; correct: number }>();
    let correctCount = 0;
    let confidenceAligned = 0;

    for (const answer of payloadAnswers) {
      const selected = String(answer.selected ?? '').toUpperCase();
      const expected = String(answer.correct ?? '').toUpperCase();
      const isCorrect = !!selected && selected === expected;

      if (isCorrect) {
        correctCount += 1;
      }

      const confidence = String(answer.confidence ?? 'medium').toLowerCase();
      if ((isCorrect && confidence !== 'low') || (!isCorrect && confidence === 'low')) {
        confidenceAligned += 1;
      }

      const skill = String(answer.skill ?? 'General').trim() || 'General';
      const prev = bySkill.get(skill) ?? { total: 0, correct: 0 };
      prev.total += 1;
      if (isCorrect) {
        prev.correct += 1;
      }
      bySkill.set(skill, prev);
    }

    const total = Math.max(1, payloadAnswers.length);
    const realScore = Math.round((correctCount / total) * 100);
    const confidenceAccuracy = Math.round((confidenceAligned / total) * 100);

    const expectedDuration = Math.max(60, this.questions.length * 70);
    const speedRatio = expectedDuration / Math.max(1, elapsedSeconds);
    const speedScore = Math.max(30, Math.min(100, Math.round(speedRatio * 100)));

    const skillScores: Record<string, number> = {};
    for (const [skill, stats] of bySkill.entries()) {
      skillScores[skill] = Math.round((stats.correct / Math.max(1, stats.total)) * 100);
    }

    return {
      real_score: realScore,
      confidence_accuracy: confidenceAccuracy,
      speed_score: speedScore,
      skill_scores: skillScores,
      summary: 'Resultat calcule localement (fallback) car le moteur d evaluation est indisponible ou trop lent.'
    };
  }

  private composeProfessionalResult(mcqRaw: any, codeRaw: any): QuizResultViewModel {
    const mcq = mcqRaw?.data ?? mcqRaw ?? {};
    const code = codeRaw?.data ?? codeRaw ?? null;

    const mcqScore = this.numberValue(mcq?.real_score);
    const codeScore = code ? this.numberValue(code?.score) : 0;
    const finalScore = Math.round((mcqScore * 0.7) + (codeScore * 0.3));
    const passed = finalScore >= 60;

    const skillScores = (mcq?.skill_scores && typeof mcq.skill_scores === 'object')
      ? mcq.skill_scores as Record<string, number>
      : {};

    const sortedSkills = Object.entries(skillScores)
      .sort((a, b) => Number(b[1]) - Number(a[1]));

    const strongestSkills = sortedSkills.slice(0, 3).map(([name]) => name);
    const weakSkills = sortedSkills
      .filter(([name, score]) => Number(score) < 50 && !strongestSkills.includes(name))
      .slice(0, 3)
      .map(([name]) => name);
    const skillGapAnalysis = this.buildSkillGapAnalysis(skillScores);
    const gapSummary = this.buildGapSummary(skillGapAnalysis);

    const headline = passed
      ? 'Profil valide pour un entretien technique avance.'
      : 'Des bases presentes, mais un renforcement cible est recommande.';

    return {
      finalScore,
      passed,
      headline,
      mcqScore,
      codeScore,
      confidenceAccuracy: this.numberValue(mcq?.confidence_accuracy),
      speedScore: this.numberValue(mcq?.speed_score),
      strongestSkills,
      weakSkills,
      skillScores,
      skillGapAnalysis,
      gapSummary,
      mcqSummary: String(mcq?.summary ?? ''),
      codeFeedback: String(code?.feedback ?? ''),
      codeBreakdown: code?.breakdown ?? null,
      rawMcq: mcq,
      rawCode: code,
      questionCount: this.questions.length,
      answeredCount: this.answeredCount,
      totalDurationSeconds: Math.max(1, Math.round((Date.now() - this.startedAt) / 1000))
    };
  }

  submit(): void {
    if (this.submitting || this.result) {
      return;
    }

    const userId = this.state.candidateId();
    const testId = this.state.testId();
    if (!userId || !testId) {
      return;
    }

    if (this.codeChallenge && !this.codeSubmission.trim()) {
      this.notify.warning('Veuillez soumettre une solution code avant de terminer le test.');
      return;
    }

    const elapsed = Math.max(1, Math.round((Date.now() - this.startedAt) / 1000));
    const payloadAnswers: McqSubmittedAnswer[] = this.questions.map(q => ({
      question_id: q.id,
      skill: q.skill,
      selected: (this.answers[q.id] ?? 'A').toUpperCase(),
      correct: q.correct,
      confidence: this.confidence[q.id] ?? 'medium',
      time_spent_seconds: Math.max(5, this.perQuestionElapsedSeconds[q.id] ?? Math.round(elapsed / this.questions.length)),
      difficulty: q.difficulty ?? 'medium'
    }));



    const mcq$ = this.testApi.evaluateTest({
      test_id: testId,
      candidate_id: userId,
      answers: payloadAnswers,

    }).pipe(
      catchError(err => {
        this.notify.warning(
          err?.name === 'TimeoutError'
            ? 'Evaluation QCM trop lente. Un score local provisoire est affiche.'
            : 'Evaluation QCM indisponible. Un score local provisoire est affiche.'
        );
        return of(this.buildLocalFallbackMcqEvaluation(payloadAnswers, elapsed));
      })
    );

    const code$ = this.codeChallenge
      ? this.testApi.evaluateCodeChallenge({
          challenge_id: this.codeChallenge.challenge_id,
          skill: this.codeChallenge.skill ?? this.randomCodeSkill,
          submitted_code: this.codeSubmission,
          candidate_id: userId,
          hints_used: this.codeHintsUsed,
          time_spent_seconds: Math.max(1, Math.round((Date.now() - this.codeStartedAt) / 1000)),
          description: this.codeChallenge.description ?? '',
          expected_behavior: this.codeChallenge.expected_behavior ?? ''
        }).pipe(
          catchError(err => {
            this.notify.warning(err?.error?.message ?? 'Evaluation code indisponible. Le score QCM reste calcule.');
            return of(null);
          })
        )
      : of(null);

    this.submitting = true;
    this.stopTimerLoop();
    this.startSubmitWatchdog();
    this.submitSub?.unsubscribe();

    this.submitSub = forkJoin({ mcq: mcq$, code: code$ }).pipe(
      finalize(() => {
        this.clearSubmitWatchdog();
        if (this.submitting && !this.result) {
          this.submitting = false;
          this.startTimerLoop();
        }
      })
    ).subscribe({
      next: ({ mcq, code }) => {
        this.submitSub = null;
        this.submitting = false;
        this.result = this.composeProfessionalResult(mcq, code);

        // Persist tech result to sessionStorage so mes-resultats can display it
        // Persist complete tech result to sessionStorage so mes-resultats can display it fully
        try {
          sessionStorage.setItem('latestTechResult', JSON.stringify(this.result));
          sessionStorage.setItem('latestTechResultMeta', JSON.stringify({
            taken_at: new Date().toISOString(),
            test_type: 'MCQ'
          }));
        } catch {}

        // Auto-navigate to competences results if in that flow
        const fromCompetences = !!sessionStorage.getItem('techIntakeContext');
        if (fromCompetences) {
          setTimeout(() => {
            void this.router.navigate(['/competences/results'], { state: { result: this.result } });
          }, 2000);
        }
      },
      error: err => {
        this.submitSub = null;
        this.submitting = false;
        this.startTimerLoop();
        if (err?.name === 'TimeoutError') {
          this.notify.error('Evaluation trop lente. Reessayez ou soumettez plus tard.');
          return;
        }
        this.notify.error(err?.error?.message ?? 'Évaluation échouée.');
      }
    });
  }
}
