/**
 * PCM (Process Communication Model) personality types
 * 4 core personality archetypes
 */
export enum PCMPersonalityType {
  DREAMER = 'DREAMER',
  THINKER = 'THINKER',
  PERSISTER = 'PERSISTER',
  REBEL = 'REBEL'
}

/**
 * PCM Question for the personality test
 */
export interface PCMQuestion {
  id: number;
  text: string;
  description?: string;
  personalityAspect: string; // Which trait does this question assess
  displayOrder: number;
}

/**
 * User's answer to a PCM question
 */
export interface PCMAnswer {
  questionId: number;
  selectedOptionIndex: number; // Index of selected answer option
  value: number; // Score for this answer
}

/**
 * PCM Test session/attempt
 */
export interface PCMTest {
  id: number;
  userId: number;
  startedAt: Date;
  completedAt?: Date;
  answers: PCMAnswer[];
  status: 'IN_PROGRESS' | 'COMPLETED' | 'ABANDONED';
}

/**
 * PCM Personality result/profile
 */
export interface PCMResult {
  id: number;
  userId: number;
  testId: number;
  primaryType: PCMPersonalityType;
  secondaryType?: PCMPersonalityType;
  scores: {
    dreamer: number;
    thinker: number;
    persister: number;
    rebel: number;
  };
  description: string;
  strengths: string[];
  weaknesses: string[];
  recommendations: string[];
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Request to submit PCM test answers
 */
export interface PCMSubmitRequest {
  testId: number;
  answers: PCMAnswer[];
}

/**
 * PCM Statistics for dashboard
 */
export interface PCMStatistics {
  totalTests: number;
  completedTests: number;
  primaryPersonalityType: PCMPersonalityType;
  averageScores: {
    dreamer: number;
    thinker: number;
    persister: number;
    rebel: number;
  };
  testHistory: PCMResult[];
}
