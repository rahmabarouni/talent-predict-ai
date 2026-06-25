export enum TypeSkill {
  SOFT = 'SOFT',
  TECH = 'TECH'
}

export interface SkillResponse {
  id: string;
  nom: string;
  type: TypeSkill;
  niveau: number; // 1-5
  description: string;
  source: string; // CV, GITHUB, PYTHON_AI, PCM
  dateEvaluation: string;
  validee: boolean;
}

export interface SkillRequest {
  nom: string;
  type: TypeSkill;
  niveau: number;
  description?: string;
}

export interface SkillComparison {
  skillName: string;
  userLevel: number;
  averageLevel: number;
  gap: number;
}
