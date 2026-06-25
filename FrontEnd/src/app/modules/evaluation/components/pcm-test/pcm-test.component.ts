import { Component, OnInit, inject } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../../auth/services/auth.service';
import { SoftSkillsService } from '../../services/soft-skills.service';
import { SoftSkillsAnalysisRequest } from '../../models/soft-skills.model';
import { QuestionCardComponent } from '../question-card/question-card.component';

interface PCMQuestion {
  id: string;
  question: string;
  category: string;
}

@Component({
  selector: 'app-pcm-test',
  standalone: true,
  imports: [FormsModule, RouterModule, QuestionCardComponent],
  templateUrl: './pcm-test.component.html',
  styleUrls: ['./pcm-test.component.scss']
})
export class PcmTestComponent implements OnInit {
  private authService = inject(AuthService);
  private softSkillsService = inject(SoftSkillsService);
  private router = inject(Router);

  currentStep = 0;
  totalSteps = 0; // set dynamically
  responses: Record<string, string> = {};
  answers: Record<string, number> = {};
  loading = false;
  error: string | null = null;

  // All 18 source questions
  private readonly allQuestions: PCMQuestion[] = [
    // PCM Personality Questions (q1-q12)
    {
      id: 'q1',
      question: 'Je suis à l\'aise pour exprimer mes émotions et comprendre celles des autres',
      category: 'Empathique'
    },
    {
      id: 'q2',
      question: 'J\'aime organiser mes tâches et respecter les délais',
      category: 'Travaillomane'
    },
    {
      id: 'q3',
      question: 'Je défends mes valeurs et mes opinions avec conviction',
      category: 'Persévérant'
    },
    {
      id: 'q4',
      question: 'J\'aime prendre des risques et relever des défis',
      category: 'Promoteur'
    },
    {
      id: 'q5',
      question: 'Je préfère la créativité et la spontanéité à la routine',
      category: 'Rebelle'
    },
    {
      id: 'q6',
      question: 'J\'ai besoin de calme et de tranquillité pour réfléchir',
      category: 'Rêveur'
    },
    {
      id: 'q7',
      question: 'Je suis sensible aux besoins des autres et j\'aime aider',
      category: 'Empathique'
    },
    {
      id: 'q8',
      question: 'Je suis méthodique et j\'aime les faits concrets',
      category: 'Travaillomane'
    },
    {
      id: 'q9',
      question: 'J\'ai des principes forts et je les respecte',
      category: 'Persévérant'
    },
    {
      id: 'q10',
      question: 'J\'aime diriger et prendre des décisions rapides',
      category: 'Promoteur'
    },
    {
      id: 'q11',
      question: 'Je préfère un environnement ludique et décontracté',
      category: 'Rebelle'
    },
    {
      id: 'q12',
      question: 'J\'aime travailler de manière autonome et calme',
      category: 'Rêveur'
    },

    // SOFT SKILLS QUESTIONS — MISSING SECTIONS (q13-q18)
    {
      id: 'q13',
      question: 'Je prends des initiatives sans attendre qu\'on me le demande',
      category: 'Ownership'
    },
    {
      id: 'q14',
      question: 'J\'assume la responsabilité de mes erreurs',
      category: 'Ownership'
    },
    {
      id: 'q15',
      question: 'Je vais au bout de mes projets sans supervision',
      category: 'Ownership'
    },
    {
      id: 'q16',
      question: 'Je prends naturellement des décisions dans les groupes',
      category: 'Leadership'
    },
    {
      id: 'q17',
      question: 'Je motive et inspire mes collègues',
      category: 'Leadership'
    },
    {
      id: 'q18',
      question: 'J\'ai une vision claire de mes objectifs professionnels',
      category: 'Leadership'
    }
  ];

  // Randomly selected subset shown this session
  questions: PCMQuestion[] = [];

  ngOnInit(): void {
    // Non-randomized: show all questions in their defined order
    this.questions = [...this.allQuestions];
    this.totalSteps = this.questions.length;

    // Initialize both responses (for PCM endpoint) and answers (for soft skills endpoint)
    this.questions.forEach(q => {
      this.responses[q.id] = ''; // String for PCM endpoint
      this.answers[q.id] = 5;     // Number 0-10 for soft skills (default 5)
    });
  }

  get steps(): number[] {
    return Array.from({ length: this.totalSteps }, (_, i) => i);
  }

  get currentQuestions(): PCMQuestion[] {
    const questionsPerStep = 1;
    const start = this.currentStep * questionsPerStep;
    return this.questions.slice(start, start + questionsPerStep);
  }

  get progress(): number {
    return ((this.currentStep + 1) / this.totalSteps) * 100;
  }

  get canProceed(): boolean {
    // Both responses and answers must be filled for current questions
    return this.currentQuestions.every(q => 
      this.responses[q.id] !== '' && this.answers[q.id] !== undefined
    );
  }

  onAnswerChange(questionId: string, answer: string): void {
    this.responses[questionId] = answer;
    // Convert string answer to numeric scale (1-5 → 0-10)
    // If answer was "1" (disagree), map to 0-3; if "5" (agree), map to 8-10
    const numAnswer = parseInt(answer) || 5;
    this.answers[questionId] = Math.round((numAnswer - 1) * 2.5); // 1→0, 3→5, 5→10
  }

  nextStep(): void {
    if (this.canProceed && this.currentStep < this.totalSteps - 1) {
      this.currentStep++;
    }
  }

  previousStep(): void {
    if (this.currentStep > 0) {
      this.currentStep--;
    }
  }

  goToStep(step: number): void {
    // Allow going back to completed steps or current step
    if (step <= this.currentStep) {
      this.currentStep = step;
    }
  }

  private buildReponses(): Record<string, string> {
    const reponses: Record<string, string> = {};
    this.questions.forEach((q, idx) => {
      const answer = this.responses[q.id];
      reponses[`q${idx + 1}`] = answer ? answer.toString() : '5';
    });
    return reponses;
  }

  submitTest(): void {
    if (!this.canProceed) {
      this.error = 'Veuillez répondre à toutes les questions avant de soumettre.';
      return;
    }

    const currentUser = this.authService.getCurrentUser();
    if (!currentUser?.id) {
      this.error = 'Utilisateur non authentifié.';
      return;
    }

    this.loading = true;
    this.error = null;

    // Step 1: Launch soft skills analysis with ALL 18 answers directly
    // (Legacy PCM endpoint bypassed as requested for system cleanup)
    this.launchSoftSkillsAnalysis();
  }

  private launchSoftSkillsAnalysis(): void {
    const profileDataStr = sessionStorage.getItem('softSkillsProfile');
    const profileData = profileDataStr ? JSON.parse(profileDataStr) : {};

    console.log('[PcmTest] launchSoftSkillsAnalysis: profile data =', profileData);

    // Build soft skills request with ALL 18 answers explicitly
    const softSkillsRequest: SoftSkillsAnalysisRequest = {
      fullName:       profileData.fullName       || '',
      email:          profileData.email          || '',
      githubUsername: this.normalizeGithubUsername(profileData.githubUsername || ''),
      cvText:         profileData.cvText         || '',
      // Send each answer individually — NEVER use defaults
      q1:  this.answers['q1']  ?? 5,
      q2:  this.answers['q2']  ?? 5,
      q3:  this.answers['q3']  ?? 5,
      q4:  this.answers['q4']  ?? 5,
      q5:  this.answers['q5']  ?? 5,
      q6:  this.answers['q6']  ?? 5,
      q7:  this.answers['q7']  ?? 5,
      q8:  this.answers['q8']  ?? 5,
      q9:  this.answers['q9']  ?? 5,
      q10: this.answers['q10'] ?? 5,
      q11: this.answers['q11'] ?? 5,
      q12: this.answers['q12'] ?? 5,
      q13: this.answers['q13'] ?? 5,
      q14: this.answers['q14'] ?? 5,
      q15: this.answers['q15'] ?? 5,
      q16: this.answers['q16'] ?? 5,
      q17: this.answers['q17'] ?? 5,
      q18: this.answers['q18'] ?? 5,
    };

    console.log('[PcmTest] Calling softSkillsService.analyze with:', softSkillsRequest);

    // Submit to soft skills analysis
    this.softSkillsService.analyze(softSkillsRequest).subscribe({
      next: (result) => {
        console.log('[PcmTest] Soft skills analysis complete. Result:', result);
        this.loading = false;
        sessionStorage.setItem('softSkillsResult', JSON.stringify(result));
        this.router.navigate(['/evaluation/scenario'],
          { state: { result } });
      },
      error: (err) => {
        console.error('[PcmTest] Soft skills error:', err);
        this.loading = false;
        this.router.navigate(['/evaluation/scenario'],
          { state: { softSkillsError: true } });
      }
    });
  }

  private normalizeGithubUsername(input: string): string {
    const raw = (input || '').trim();
    if (!raw) return '';

    // Accept full URLs like https://github.com/user and reduce them to user.
    const cleaned = raw.replace(/^(https?:\/\/)?(www\.)?github\.com\//i, '');
    return cleaned.split('/')[0].replace(/^@/, '').trim();
  }
}
