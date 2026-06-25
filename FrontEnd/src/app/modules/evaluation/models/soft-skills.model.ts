export interface SoftSkillsAnalysisRequest {
  fullName: string;
  email: string;
  githubUsername?: string;
  cvText?: string;
  // PCM questions (0-10)
  q1: number;  q2: number;  q3: number;   // communication
  q4: number;  q5: number;  q6: number;   // discipline
  q7: number;  q8: number;  q9: number;   // curiosity
  q10: number; q11: number; q12: number;  // collaboration
  q13: number; q14: number; q15: number;  // ownership
  q16: number; q17: number; q18: number;  // leadership
}

export interface SoftSkillsResult {
  userName: string;
  userEmail: string;
  overallScore: number;
  personalityType?: string;
  personalityDescription?: string;
  mergedSoftSkills: Record<string, number>;
  top3Strengths: string[];
  top3Weaknesses: string[];
  summary: string;
  careerAdvice: string;
  keyStrengths: string[];
  keyWeaknesses: string[];
  trainingRecommendations: Record<string, string>;
  sourceData: {
    cv:     { overall_score: number };
    github: { overall_score: number };
    pcm:    { overall_score: number };
  };
  weightsApplied: Record<string, number>;
}

export interface SoftSkillsProgress {
  evaluationDate: string;
  overallScore: number;
  skills: Record<string, number>;
  improvementDelta: number | null;
  summary: string;
}
