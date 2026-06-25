export enum Role {
  USER = 'USER',
  ADMIN = 'ADMIN'
}

/**
 * Lightweight user from auth response (login/register).
 */
export interface AuthUser {
  id: string;
  nom: string;
  prenom: string;
  email: string;
  role: Role;
  emailVerified?: boolean;

  dateInscription: Date;
}

/**
 * Full user profile returned by GET /api/users/{id}
 */
export interface User {
  id: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  department: string;
  position: string;
  hireDate: string;
  profilePictureUrl: string;
  isActive: boolean;
  role: Role;
  createdAt: string;
  updatedAt: string;
  xp?: number;
  level?: number;
  scoreMoyen?: number;
  testsCount?: number;
  formationsCount?: number;
  lastLogin?: string;
  riskLevel?: string;
  statut?: string;
}

/**
 * Profile DTO from GET/PUT /api/profiles/users/{userId}
 */
export interface ProfileResponse {
  id: string;
  userId: string;
  // Read-only from user
  firstName: string;
  lastName: string;
  email: string;
  position: string;
  department: string;
  // Editable
  titreProfessionnel: string;
  description: string;
  urlPhoto: string;
  experienceAns: number;
  niveauEtudes: string;
  lienLinkedin: string;
  githubUrl: string;
  cvUrl: string;
  portfolioUrl: string;
  // New editable fields
  poste: string;
  departementEditable: string;
  ville: string;
  disponibilite: string;
  typeContrat: string[];
  // GitHub stats (populated by IA analysis)
  githubRepos: number;
  githubFollowers: number;
  githubFollowing: number;
  githubBio: string;
  githubCompany: string;
  githubLocation: string;
  githubAvatarUrl: string;
  githubName: string;
  aiSummary: string;
  publicSlug?: string;
  updatedAt?: string;
}

export interface ProfileUpdateRequest {
  titreProfessionnel?: string;
  description?: string;
  urlPhoto?: string;
  experienceAns?: number;
  niveauEtudes?: string;
  lienLinkedin?: string;
  githubUrl?: string;
  cvUrl?: string;
  portfolioUrl?: string;
  poste?: string;
  departementEditable?: string;
  ville?: string;
  disponibilite?: string;
  typeContrat?: string[];
}

export interface UserRequest {
  username?: string;
  email: string;
  password?: string;
  firstName: string;
  lastName: string;
  department?: string;
  position?: string;
  hireDate?: string;
  profilePictureUrl?: string;
  isActive?: boolean;
  role?: Role;
}

export interface AuthRequest {
  email: string;
  password: string;

}

export interface AuthResponse {
  token?: string | null;
  type: string;
  id: string;
  email: string;
  role: string;
  nom: string;
  prenom: string;
  redirectUrl?: string;
  emailVerified?: boolean;

}

export interface InscriptionRequest {
  nom: string;
  prenom: string;
  email: string;
  phoneNumber?: string;
  password: string;
  role: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}
