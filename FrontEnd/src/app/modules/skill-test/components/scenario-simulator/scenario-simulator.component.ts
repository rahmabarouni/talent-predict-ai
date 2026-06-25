import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { take } from 'rxjs/operators';
import { TestApiService } from '../../services/test-api.service';
import { NotificationService } from '../../../../core/services/notification.service';

import { SoftSkillsService } from '../../../evaluation/services/soft-skills.service';
import { AuthService } from '../../../auth/services/auth.service';

interface ScenarioData {
  scenario_title: string;
  scenario_description: string;
  skills_tested: string[];
}

interface ScenarioEvaluation {
  scores: {
    empathy: number;
    assertiveness: number;
    pragmatism: number;
    communication_clarity: number;
  };
  strengths: string[];
  areas_for_improvement: string[];
  overall_feedback: string;
  culture_add_profile: string;
}



@Component({
  selector: 'app-scenario-simulator',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './scenario-simulator.component.html',
  styleUrl: './scenario-simulator.component.scss'
})
export class ScenarioSimulatorComponent implements OnInit {
  private testApi  = inject(TestApiService);
  private notify   = inject(NotificationService);
  private router   = inject(Router);
  private cdr      = inject(ChangeDetectorRef);

  private authService = inject(AuthService);
  private softSkillsService = inject(SoftSkillsService);

  // ── Config ─────────────────────────────────────────────────────
  role = '';
  selectedLevel = 'Mid-Level';
  readonly levels = ['Junior', 'Mid-Level', 'Senior', 'Lead', 'Manager'];

  ngOnInit(): void {
    // Pre-fill from user profile if available
    const user = this.authService.getUserProfile();
    if (user?.position) this.role = user.position;
  }

  readonly rolesSuggestions = [
    'Full-Stack Developer', 'Frontend Developer', 'Backend Developer',
    'DevOps Engineer', 'Data Scientist', 'Product Manager',
    'UX Designer', 'QA Engineer', 'Tech Lead', 'Scrum Master'
  ];

  // ── State ──────────────────────────────────────────────────────
  generatingScenario = false;
  scenario: ScenarioData | null = null;

  candidateResponse = '';
  submitting = false;
  evaluation: ScenarioEvaluation | null = null;




  // ── Character counter ──────────────────────────────────────────
  get responseLength(): number { return this.candidateResponse.length; }
  get responseTooShort(): boolean { return this.responseLength > 0 && this.responseLength < 50; }
  get responseReady(): boolean { return this.responseLength >= 50; }

  // ── Score helpers for template ─────────────────────────────────
  get scoreEntries(): { label: string; key: string; value: number; icon: string }[] {
    if (!this.evaluation?.scores) return [];
    return [
      { label: 'Empathie', key: 'empathy', value: this.evaluation.scores.empathy, icon: '❤️' },
      { label: 'Assertivité', key: 'assertiveness', value: this.evaluation.scores.assertiveness, icon: '💪' },
      { label: 'Pragmatisme', key: 'pragmatism', value: this.evaluation.scores.pragmatism, icon: '🎯' },
      { label: 'Clarté', key: 'communication_clarity', value: this.evaluation.scores.communication_clarity, icon: '💬' },
    ];
  }

  get averageScore(): number {
    if (!this.evaluation?.scores) return 0;
    const s = this.evaluation.scores;
    return Math.round((s.empathy + s.assertiveness + s.pragmatism + s.communication_clarity) / 4);
  }

  // ── Actions ────────────────────────────────────────────────────
  selectRole(r: string): void {
    this.role = r;
  }

  generateScenario(): void {
    if (!this.role.trim()) {
      this.notify.warning('Veuillez choisir ou saisir un rôle.');
      return;
    }

    this.generatingScenario = true;
    this.scenario = null;
    this.evaluation = null;

    this.candidateResponse = '';
    this.cdr.markForCheck();

    this.testApi.generateScenario({
      role: this.role.trim(),
      level: this.selectedLevel
    }).pipe(take(1)).subscribe({
      next: (res: any) => {
        this.generatingScenario = false;
        this.scenario = res as ScenarioData;
        this.cdr.markForCheck();


      },
      error: (err: any) => {
        this.generatingScenario = false;
        this.notify.error(err?.error?.message ?? 'Impossible de générer le scénario.');
        this.cdr.markForCheck();
      }
    });
  }

  submitResponse(): void {
    if (!this.scenario || this.responseTooShort || this.submitting) return;



    this.submitting = true;
    this.cdr.markForCheck();

    this.testApi.evaluateScenario({
      scenario: this.scenario.scenario_description,
      response: this.candidateResponse.trim(),

    }).pipe(take(1)).subscribe({
      next: (res: any) => {
        this.submitting = false;
        this.evaluation = res as ScenarioEvaluation;

        // Save scenario result and navigate to soft results
        const existing = sessionStorage.getItem('softSkillsResult');
        const softData = existing ? JSON.parse(existing) : {};
        softData.scenarioEvaluation = this.evaluation;
        sessionStorage.setItem('softSkillsResult', JSON.stringify(softData));

        // Persist to backend
        this.softSkillsService.saveScenarioResult(this.evaluation).subscribe({
          next: () => console.log('[ScenarioSimulator] Result persisted to backend'),
          error: (e) => console.error('[ScenarioSimulator] Failed to persist result', e)
        });

        this.cdr.markForCheck();
      },
      error: (err: any) => {
        this.submitting = false;
        this.notify.error(err?.error?.message ?? 'Évaluation échouée.');
        this.cdr.markForCheck();
      }
    });
  }

  getScoreClass(value: number): string {
    if (value >= 75) return 'score-high';
    if (value >= 50) return 'score-medium';
    return 'score-low';
  }

  reset(): void {

    this.scenario = null;
    this.evaluation = null;

    this.candidateResponse = '';
    this.role = '';
  }

  newScenarioSameRole(): void {

    this.evaluation = null;

    this.candidateResponse = '';
    this.generateScenario();
  }

  goBack(): void {

    void this.router.navigate(['/skill-test']);
  }

  goToSoftResults(): void {

    void this.router.navigate(['/evaluation/results']);
  }


}
