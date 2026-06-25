export enum TypeFormation {
  TECH_SKILL = 'TECH_SKILL',
  SOFT_SKILL = 'SOFT_SKILL',
  CERTIFICATION = 'CERTIFICATION',
  WORKSHOP = 'WORKSHOP',
  // Legacy values kept for backward-compatibility with old data.
  TECHNIQUE = 'TECHNIQUE',
  SOFT_SKILLS = 'SOFT_SKILLS',
  MANAGEMENT = 'MANAGEMENT',
  LANGUES = 'LANGUES'
}

export enum StatutFormation {
  PROPOSEE = 'PROPOSEE',
  EN_ATTENTE = 'EN_ATTENTE',
  ACCEPTEE = 'ACCEPTEE',
  REJETEE = 'REJETEE',
  PROPOSEE_ADMIN = 'PROPOSEE_ADMIN',
  EN_COURS = 'EN_COURS',
  EN_ATTENTE_VALIDATION = 'EN_ATTENTE_VALIDATION',
  TERMINEE = 'TERMINEE',
  ANNULEE = 'ANNULEE'
}

export interface Formation {
  id: string;
  userId?: string;
  titre: string;
  description: string;
  type: TypeFormation;
  statut: StatutFormation;
  duree: number; // in hours
  progression: number; // 0-100
  dateProposition: Date;
  dateDebut?: Date;
  dateFin?: Date;
  fournisseur?: string;
  url?: string;
  reviewNote?: string;
  nextAction?: string;
  reviewedBy?: string;
  reviewedAt?: Date;
  miniTestScore?: number;
  miniTestPassed?: boolean;
  miniTestTakenAt?: Date;
  miniTestNotes?: string;
  certificateUrl?: string;
  certificateUploadedAt?: Date;
}

export interface FormationRequest {
  titre: string;
  description: string;
  type: TypeFormation;
  duree: number;
  fournisseur?: string;
  dateDebut?: string;
  url?: string;
  statut?: StatutFormation;
}

export interface FormationResponse {
  id: string;
  userId?: string;
  titre: string;
  description: string;
  type: TypeFormation;
  statut: StatutFormation;
  duree: number;
  progression: number;
  dateProposition: Date;
  dateDebut?: Date;
  dateFin?: Date;
  fournisseur?: string;
  url?: string;
  reviewNote?: string;
  nextAction?: string;
  reviewedBy?: string;
  reviewedAt?: Date;
  miniTestScore?: number;
  miniTestPassed?: boolean;
  miniTestTakenAt?: Date;
  miniTestNotes?: string;
  certificateUrl?: string;
  certificateUploadedAt?: Date;
  candidatName?: string;
  requestedAt?: Date;
  adminNote?: string;
}

export interface MiniTestSubmissionRequest {
  score?: number;
  correctAnswers?: number;
  totalQuestions?: number;
  passingScore?: number;
  notes?: string;
}

export interface FormationReviewNotesRequest {
  reviewNote?: string;
  nextAction?: string;
  reviewedBy?: string;
}
