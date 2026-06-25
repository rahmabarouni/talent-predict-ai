import { Injectable, signal } from '@angular/core';

export interface McqQuestion {
  id: string;
  skill: string;
  difficulty: string;
  type: string;
  question: string;
  options: Record<string, string>;
  correct: string;
  confidence_required: boolean;
}

@Injectable({ providedIn: 'root' })
export class TestStateService {
  readonly testId = signal<string | null>(null);
  readonly questions = signal<McqQuestion[]>([]);
  readonly level = signal<string>('INTERMEDIATE');
  readonly candidateId = signal<string>('');
  readonly analyzedSkillScores = signal<Record<string, number>>({});

  // Code challenge context for the unified quiz + code flow
  readonly codeSkill = signal<string>('JavaScript');
  readonly codeLevel = signal<string>('INTERMEDIATE');

  setSession(
    testId: string,
    questions: McqQuestion[],
    level: string,
    candidateId: string,
    analyzedSkillScores?: Record<string, number>
  ): void {
    this.testId.set(testId);
    this.questions.set(questions);
    this.level.set(level);
    this.candidateId.set(candidateId);
    this.analyzedSkillScores.set(analyzedSkillScores ?? {});
  }

  setAnalyzedSkillScores(scores: Record<string, number>): void {
    this.analyzedSkillScores.set(scores);
  }

  setCodeContext(skill: string, level: string): void {
    this.codeSkill.set(skill);
    this.codeLevel.set(level);
  }

  clear(): void {
    this.testId.set(null);
    this.questions.set([]);
    this.analyzedSkillScores.set({});
  }
}
