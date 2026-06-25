export interface DashboardStats {
  testsCount: number;
  skillsCount: number;
  formationsCount: number;
  formationsEnCours: number;
  formationsTerminees: number;
  progressionMoyenne: number;
  topSkills: TopSkill[];
  recentFormations: RecentFormation[];
}

export interface TopSkill {
  nom: string;
  niveau: number;
  type: string;
}

export interface RecentFormation {
  titre: string;
  statut: string;
  progression: number;
  dateDebut: Date;
}

export interface DashboardResponse {
  testsCount: number;
  skillsCount: number;
  formationsCount: number;
  formationsEnCours: number;
  formationsTerminees: number;
  progressionMoyenne: number;
  topSkills: TopSkill[];
  recentFormations: RecentFormation[];
}
