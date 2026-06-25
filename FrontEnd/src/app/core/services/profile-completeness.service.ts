import { Injectable } from '@angular/core';
import { ProfileResponse } from '../../modules/auth/models/user.model';

export interface CompletenessResult {
  score: number;           // 0–100
  filledCount: number;
  totalCount: number;
  missing: MissingField[];
  tips: string[];
}

export interface MissingField {
  key: string;   // field name on ProfileResponse
  label: string; // human-readable label
  inputId?: string; // HTML id for the form input (for scrolling)
  tip?: string;  // Contextual tip shown below the ring
}

const PROFILE_FIELDS: { key: keyof ProfileResponse; label: string; inputId?: string; points: number; tip?: string }[] = [
  { key: 'urlPhoto',           label: 'Photo de profil',       inputId: 'photoInput',          points: 10, tip: 'Ajoutez une photo pour humaniser votre profil et obtenir plus de visibilité.' },
  { key: 'description',        label: 'Bio / Description',     inputId: 'description',         points: 10, tip: 'Une bio de plus de 50 caractères améliore votre crédibilité auprès des recruteurs.' },
  { key: 'titreProfessionnel', label: 'Titre professionnel',   inputId: 'titreProfessionnel',  points: 10, tip: 'Un titre clair aide les recruteurs à comprendre votre expertise.' },
  { key: 'poste',              label: 'Poste / Rôle cible',    inputId: 'poste',               points: 10, tip: 'Précisez votre poste pour apparaître dans les bonnes recherches.' },
  { key: 'departementEditable',label: 'Département',           inputId: 'departementEditable', points: 10, tip: 'Ajoutez votre département pour que votre manager puisse vous retrouver facilement.' },
  { key: 'experienceAns',      label: 'Années d\'expérience',  inputId: 'experienceAns',       points: 10 },
  { key: 'niveauEtudes',       label: 'Niveau d\'études',      inputId: 'niveauEtudes',        points: 10 },
  { key: 'githubUrl',          label: 'GitHub',                inputId: 'githubUrl',           points: 10 },
  { key: 'lienLinkedin',       label: 'LinkedIn',              inputId: 'lienLinkedin',        points: 10 },
  { key: 'cvUrl',              label: 'CV (PDF)',              inputId: 'cvInput',             points: 10 },
];

@Injectable({
  providedIn: 'root'
})
export class ProfileCompletenessService {

  compute(profile: ProfileResponse | null | undefined): CompletenessResult {
    if (!profile) {
      return {
        score: 0,
        filledCount: 0,
        totalCount: PROFILE_FIELDS.length,
        missing: PROFILE_FIELDS.map(f => ({ key: f.key, label: f.label, inputId: f.inputId, tip: f.tip })),
        tips: []
      };
    }

    const missing: MissingField[] = [];
    const tips: string[] = [];
    let totalPoints = 0;
    let earnedPoints = 0;

    for (const field of PROFILE_FIELDS) {
      totalPoints += field.points;
      const val = (profile as any)[field.key];
      const isEmpty = val === null || val === undefined || val === '' || val === 0 ||
        (Array.isArray(val) && val.length === 0);

      // Special rule: bio must be > 50 chars to count
      const bioTooShort = field.key === 'description' && typeof val === 'string' && val.length > 0 && val.length < 50;

      if (isEmpty || bioTooShort) {
        missing.push({ key: field.key, label: field.label, inputId: field.inputId, tip: field.tip });
        if (field.tip) tips.push(field.tip);
      } else {
        earnedPoints += field.points;
      }
    }

    const score = Math.round((earnedPoints / totalPoints) * 100);
    const filledCount = PROFILE_FIELDS.length - missing.length;

    return { score, filledCount, totalCount: PROFILE_FIELDS.length, missing, tips };
  }
}
