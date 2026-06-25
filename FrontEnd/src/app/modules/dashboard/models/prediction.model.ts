export type PredictionStatus = 'EN_ANALYSE' | 'COMPLETEE' | 'VALIDEE' | 'APPLIQUEE';

export interface PredictionResponse {
  id: string;
  datePrediction: string;
  analyse: string;
  recommandationSoft?: string;
  recommandationTech?: string;
  scoreConfiance?: number;
  scoreFinal?: number;
  scoreSoftSkills?: number;
  scoreTechSkills?: number;
  statut: PredictionStatus;
  formationsProposees?: Record<string, unknown>[];
}

export type Prediction = PredictionResponse;
