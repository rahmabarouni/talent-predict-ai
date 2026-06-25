export const PCM_PROFILES = {
  EMPATHIQUE: {
    type: 'Empathique',
    description: 'Sensible, chaleureux et compatissant',
    strengths: ['Écoute active', 'Empathie', 'Travail en équipe'],
    challenges: ['Difficulté à dire non', 'Évite les conflits'],
    recommendations: ['Développer l\'assertivité', 'Apprendre la gestion des conflits']
  },
  TRAVAILLOMANE: {
    type: 'Travaillomane',
    description: 'Logique, organisé et responsable',
    strengths: ['Organisation', 'Pensée logique', 'Fiabilité'],
    challenges: ['Perfectionnisme excessif', 'Difficulté à déléguer'],
    recommendations: ['Work-life balance', 'Délégation efficace']
  },
  PERSEVERANT: {
    type: 'Persévérant',
    description: 'Engagé, observateur et dévoué',
    strengths: ['Valeurs fortes', 'Observation', 'Engagement'],
    challenges: ['Rigidité', 'Jugement des autres'],
    recommendations: ['Flexibilité', 'Ouverture d\'esprit']
  },
  REBELLE: {
    type: 'Rebelle',
    description: 'Spontané, créatif et ludique',
    strengths: ['Créativité', 'Spontanéité', 'Énergie positive'],
    challenges: ['Manque de structure', 'Impulsivité'],
    recommendations: ['Gestion du temps', 'Planification']
  },
  PROMOTEUR: {
    type: 'Promoteur',
    description: 'Action, charisme et leadership',
    strengths: ['Leadership', 'Action rapide', 'Persuasion'],
    challenges: ['Impatience', 'Prise de risques excessive'],
    recommendations: ['Patience', 'Évaluation des risques']
  },
  REVEUR: {
    type: 'Rêveur',
    description: 'Calme, imaginatif et introspectif',
    strengths: ['Imagination', 'Calme', 'Réflexion profonde'],
    challenges: ['Passivité', 'Difficulté à s\'exprimer'],
    recommendations: ['Communication active', 'Initiative']
  }
};

export type PCMProfileType = keyof typeof PCM_PROFILES;
