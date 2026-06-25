import { Component, OnDestroy, OnInit, inject, ChangeDetectorRef } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { take } from 'rxjs/operators';
import { AuthService } from '../../../auth/services/auth.service';
import { TestApiService } from '../../services/test-api.service';
import { TestStateService } from '../../services/test-state.service';
import { SkillsService, SkillResponse } from '../../../skills/services/skills.service';
import { NotificationService } from '../../../../core/services/notification.service';

@Component({
  selector: 'app-skill-code-challenge',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './skill-code-challenge.component.html',
  styleUrl: './skill-code-challenge.component.scss'
})
export class SkillCodeChallengeComponent implements OnInit, OnDestroy {
  private auth    = inject(AuthService);
  private testApi = inject(TestApiService);
  private state   = inject(TestStateService);
  private skills  = inject(SkillsService);
  private notify  = inject(NotificationService);
  private router  = inject(Router);
  private cdr     = inject(ChangeDetectorRef);

  // ── Skills picker ─────────────────────────────────────────────
  techSkillNames: string[] = [];
  selectedSkill  = 'JavaScript';
  selectedLevel  = 'INTERMEDIATE';

  readonly levels = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'];
  readonly levelLabels: Record<string, string> = {
    BEGINNER: 'Débutant', INTERMEDIATE: 'Intermédiaire',
    ADVANCED: 'Avancé',   EXPERT: 'Expert'
  };

  // ── Challenge state ──────────────────────────────────────────
  challenge: any   = null;
  code             = '';
  hintsUsed        = 0;
  startedAt        = 0;
  timer            = 600;
  result: any      = null;
  loading          = false;
  submitting       = false;
  loadingSkills    = true;

  private intervalId: ReturnType<typeof setInterval> | null = null;

  get timerMinutes(): number  { return Math.floor(this.timer / 60); }
  get timerSeconds(): string  { return String(this.timer % 60).padStart(2, '0'); }
  get timerDanger(): boolean  { return this.timer < 60; }
  get timerWarning(): boolean { return this.timer >= 60 && this.timer < 120; }

  // ── Lifecycle ────────────────────────────────────────────────
  ngOnInit(): void {
    // Pick up context set by the launcher
    this.selectedSkill = this.state.codeSkill();
    this.selectedLevel = this.state.codeLevel();

    const user = this.auth.getCurrentUser();
    if (!user?.id) {
      this.loadingSkills = false;
      this.loadChallenge();
      return;
    }

    // Load skill list for the picker, then auto-load challenge ONCE
    this.skills.getUserSkills(String(user.id))
      .pipe(take(1))  // ensure single emission – no double calls
      .subscribe({
        next: (list: SkillResponse[]) => {
          this.techSkillNames = list
            .filter((s: SkillResponse) => s.type === 'TECH' || (s.type as string) === 'TECH')
            .sort((a: SkillResponse, b: SkillResponse) => (b.niveau ?? 0) - (a.niveau ?? 0))
            .map((s: SkillResponse) => s.nom);

          // Keep selectedSkill in sync if it's not in this user's list
          if (this.techSkillNames.length > 0 && !this.techSkillNames.includes(this.selectedSkill)) {
            this.selectedSkill = this.techSkillNames[0];
          }
          this.loadingSkills = false;
          this.loadChallenge();
        },
        error: () => {
          this.loadingSkills = false;
          this.loadChallenge(); // still try even if skill list fails
        }
      });
  }

  ngOnDestroy(): void {
    this.stopTimer();
  }

  // ── Timer ────────────────────────────────────────────────────
  private startTimer(seconds: number): void {
    this.stopTimer();
    this.timer     = seconds;
    this.startedAt = Date.now();
    this.intervalId = setInterval(() => {
      this.timer = Math.max(0, this.timer - 1);
      this.cdr.markForCheck();
      if (this.timer === 0) { this.stopTimer(); }
    }, 1000);
  }

  private stopTimer(): void {
    if (this.intervalId) { clearInterval(this.intervalId); this.intervalId = null; }
  }

  // ── API calls ────────────────────────────────────────────────
  loadChallenge(): void {
    const user = this.auth.getCurrentUser();
    if (!user?.id) { this.notify.warning('Connexion requise.'); return; }
    if (this.loading) { return; }   // prevent double-fire

    this.loading   = true;
    this.challenge = null;
    this.result    = null;
    this.code      = '';
    this.hintsUsed = 0;
    this.stopTimer();
    this.cdr.markForCheck();

    this.testApi.generateCodeChallenge({
      skill:        this.selectedSkill,
      level:        this.selectedLevel,
      candidate_id: String(user.id)
    }).pipe(take(1)).subscribe({
      next: (res: any) => {
        this.loading   = false;
        this.challenge = res;
        this.code      = res?.starter_code ?? '';
        this.startTimer(res?.time_limit_seconds ?? 600);
        this.cdr.markForCheck();
      },
      error: (err: any) => {
        this.loading = false;
        this.notify.error(err?.error?.message ?? 'Impossible de générer le challenge. Vérifiez votre connexion.');
        this.cdr.markForCheck();
      }
    });
  }

  changeSkill(): void {
    // Reset and reload when user picks a different skill
    this.challenge = null;
    this.result    = null;
    this.stopTimer();
  }

  useHint(): void {
    if (!this.challenge?.hints?.length || this.hintsUsed >= this.challenge.hints.length) { return; }
    const idx = Math.min(this.hintsUsed, this.challenge.hints.length - 1);
    this.hintsUsed++;
    this.notify.info(`💡 Indice ${this.hintsUsed} : ${this.challenge.hints[idx]}`);
  }

  submit(): void {
    const user = this.auth.getCurrentUser();
    if (!user?.id || !this.challenge || this.submitting) { return; }

    const spent     = Math.round((Date.now() - this.startedAt) / 1000);
    this.submitting = true;
    this.stopTimer();
    this.cdr.markForCheck();

    this.testApi.evaluateCodeChallenge({
      challenge_id:       this.challenge.challenge_id,
      skill:              this.challenge.skill ?? this.selectedSkill,
      submitted_code:     this.code,
      hints_used:         this.hintsUsed,
      time_spent_seconds: spent,
      description:        this.challenge.description       ?? '',
      expected_behavior:  this.challenge.expected_behavior ?? ''
    }).pipe(take(1)).subscribe({
      next: (res: any) => {
        this.submitting = false;
        this.result     = res;
        this.cdr.markForCheck();
      },
      error: (err: any) => {
        this.submitting = false;
        this.notify.error(err?.error?.message ?? 'Évaluation échouée.');
        this.cdr.markForCheck();
      }
    });
  }

  retry(): void {
    this.result    = null;
    this.challenge = null;
    this.loadChallenge();
  }

  goBack(): void { void this.router.navigate(['/skill-test']); }
}
