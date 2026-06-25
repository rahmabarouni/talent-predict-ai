import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, tap, catchError, throwError } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  AuthRequest, AuthResponse, AuthUser, InscriptionRequest,
  Role, User, ProfileResponse, ProfileUpdateRequest, ChangePasswordRequest
} from '../models/user.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/auth`;
  private usersUrl = `${environment.apiUrl}/users`;
  private profilesUrl = `${environment.apiUrl}/profiles`;

  private currentUserSubject = new BehaviorSubject<AuthUser | null>(this.getUserFromStorage());
  public currentUser$ = this.currentUserSubject.asObservable();

  private userProfileSubject = new BehaviorSubject<User | null>(null);
  public userProfile$ = this.userProfileSubject.asObservable();

  /** Global avatar URL — emits whenever the profile photo changes so all components update instantly */
  private avatarUrlSubject = new BehaviorSubject<string>(this.getStoredAvatarUrl());
  public avatarUrl$ = this.avatarUrlSubject.asObservable();

  private getStoredAvatarUrl(): string {
    try { return localStorage.getItem('avatarUrl') || ''; } catch { return ''; }
  }

  /** Call this after a photo upload to push the new URL to all subscribers */
  setAvatarUrl(url: string): void {
    try { localStorage.setItem('avatarUrl', url); } catch {}
    this.avatarUrlSubject.next(url);
  }

  /** Current avatar URL (sync access) */
  getAvatarUrl(): string {
    return this.avatarUrlSubject.value;
  }

  // Track if a refresh is in progress to prevent multiple simultaneous refreshes
  private refreshInProgress = false;
  private refreshSubject = new BehaviorSubject<boolean>(false);

  constructor() { }

  /**
   * TASK 1: Login — redirects based on role returned from backend.
   * Backend returns access token in body + refresh token in HttpOnly cookie
   */
  login(credentials: AuthRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/login`, credentials).pipe(
      tap(response => {
        this.setSession(response);
      }),
      catchError(err => throwError(() => err))
    );
  }



  /**
   * TASK 1: Register — sends role in payload, redirectUrl returned by backend.
   * Backend returns access token in body + refresh token in HttpOnly cookie
   */
  register(data: InscriptionRequest, autoLogin = true): Observable<AuthResponse> {
    const backendPayload = {
      lastName: data.nom,
      firstName: data.prenom,
      email: data.email,
      phoneNumber: (data as any).phoneNumber,
      password: data.password,
      role: data.role  // ← sends USER or ADMIN
    };
    return this.http.post<AuthResponse>(`${this.baseUrl}/register`, backendPayload).pipe(
      tap(response => {
        if (autoLogin && response?.token) {
          this.setSession(response);
        }
      })
    );
  }

  registerWithoutLogin(data: InscriptionRequest): Observable<AuthResponse> {
    return this.register(data, false);
  }

  verifyEmail(token: string): Observable<{ message: string }> {
    return this.http.get<{ message: string }>(`${this.baseUrl}/verify-email`, {
      params: { token }
    });
  }

  resendVerificationEmail(email: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.baseUrl}/resend-verification`, { email });
  }

  /**
   * SECURITY FEATURE: Refresh the access token using refresh token
   * Refresh token is automatically sent in HttpOnly cookie by browser
   */
  refreshAccessToken(): Observable<{ accessToken: string }> {
    if (this.refreshInProgress) {
      return throwError(() => new Error('Refresh in progress'));
    }

    this.refreshInProgress = true;
    this.refreshSubject.next(true);

    return this.http.post<{ accessToken: string }>(`${this.baseUrl}/refresh-token`, {}).pipe(
      tap(response => {
        // Update stored access token
        const user = this.getCurrentUser();
        if (user) {
          localStorage.setItem('token', response.accessToken);
        }
        this.refreshInProgress = false;
        this.refreshSubject.next(false);
      }),
      catchError(err => {
        this.refreshInProgress = false;
        this.refreshSubject.next(false);
        return throwError(() => err);
      })
    );
  }

  /**
   * SECURITY FEATURE: Check if token refresh is in progress
   */
  isRefreshInProgress(): boolean {
    return this.refreshInProgress;
  }

  /**
   * SECURITY FEATURE: Logout — invalidate refresh token and clear session
   */
  logout(): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.baseUrl}/logout`, {}).pipe(
      tap(() => {
        this.clearSession();
      }),
      catchError(err => {
        // Clear session even if logout request fails
        this.clearSession();
        return throwError(() => err);
      })
    );
  }

  /**
   * TASK 3: Fetch the authenticated user's full user info.
   */
  fetchMyProfile(): Observable<User> {
    const user = this.getCurrentUser();
    if (!user) {
      return throwError(() => new Error('Not authenticated'));
    }
    return this.http.get<User>(`${this.usersUrl}/${user.id}`).pipe(
      tap(profile => {
        this.userProfileSubject.next(profile);
      })
    );
  }

  getLeaderboard(): Observable<{ id: string, username: string, xp: number, level: number }[]> {
    return this.http.get<any[]>(`${this.usersUrl}/leaderboard`);
  }

  /**
   * TASK 3: Get profile (editable) by userId.
   */
  getProfile(userId: string): Observable<ProfileResponse> {
    return this.http.get<ProfileResponse>(`${this.profilesUrl}/users/${userId}`);
  }

  /**
   * TASK 3: Update profile (editable fields only).
   */
  updateProfile(userId: string, data: ProfileUpdateRequest): Observable<ProfileResponse> {
    return this.http.put<ProfileResponse>(`${this.profilesUrl}/users/${userId}`, data);
  }

  /**
   * Publish profile (generates publicSlug).
   */
  publishProfile(userId: string): Observable<ProfileResponse> {
    return this.http.post<ProfileResponse>(`${this.profilesUrl}/accounts/${userId}/publish`, {});
  }

  changePassword(data: ChangePasswordRequest): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.baseUrl}/change-password`, data);
  }

  requestPasswordResetEmail(email: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.baseUrl}/forgot-password`, {
      channel: 'EMAIL',
      email
    });
  }

  requestPasswordResetSms(phoneNumber: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.baseUrl}/forgot-password`, {
      channel: 'SMS',
      phoneNumber
    });
  }

  resetPassword(token: string, newPassword: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.baseUrl}/reset-password`, {
      token,
      newPassword
    });
  }

  /**
   * Trigger full AI analysis (GitHub, CV, LinkedIn, PCM) for the given user.
   * Backend runs analysis in background.
   */
  triggerProfileAnalysis(userId: string): Observable<{ message: string; status: string }> {
    return this.http.post<{ message: string; status: string }>(
      `${this.profilesUrl}/accounts/${userId}/analyse-ia`,
      {}
    );
  }

  /** Poll analysis status (IDLE | RUNNING | COMPLETED | FAILED). */
  getAnalysisStatus(userId: string): Observable<{ status: string; skillsFound?: number; error?: string; timestamp?: string }> {
    return this.http.get<any>(`${this.profilesUrl}/accounts/${userId}/analyse-status`);
  }

  /** Upload profile photo — stores the file and updates urlPhoto. Returns updated profile. */
  uploadProfilePhoto(userId: string, file: File): Observable<ProfileResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ProfileResponse>(`${this.profilesUrl}/accounts/${userId}/upload-photo`, formData).pipe(
      tap(profile => {
        if (profile?.urlPhoto) {
          this.setAvatarUrl(this.getAssetUrl(profile.urlPhoto));
        }
      })
    );
  }

  /** Upload CV PDF — stores the file, updates cvUrl, analyzes with AI. */
  uploadCv(userId: string, file: File): Observable<{ message: string; status: string; skillsAjoutes: string[]; totalDetectes: number }> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<any>(`${this.profilesUrl}/accounts/${userId}/upload-cv`, formData);
  }

  /** Convert a relative upload path (e.g. /uploads/photos/uuid.jpg) to an absolute backend URL. */
  getAssetUrl(path: string): string {
    if (!path || path.startsWith('http')) return path;
    return environment.apiUrl.replace('/api', '') + path;
  }

  getToken(): string | null {
    if (typeof localStorage === 'undefined') return null;
    return localStorage.getItem('token');
  }

  getCurrentUser(): AuthUser | null {
    return this.currentUserSubject.value;
  }

  getUserProfile(): User | null {
    return this.userProfileSubject.value;
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    if (!token) return false;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const expiry = payload.exp * 1000;
      return Date.now() < expiry;
    } catch {
      return false;
    }
  }

  isAdmin(): boolean {
    const user = this.getCurrentUser();
    return user?.role === Role.ADMIN || user?.role === ('ADMIN' as any);
  }



  /**
   * TASK 1: Get the redirect URL based on role.
   */
  getRedirectUrl(): string {
    if (this.isAdmin()) {
      return '/admin/dashboard';
    }

    return '/dashboard';
  }

  private setSession(authResponse: AuthResponse): void {
    if (!authResponse?.token) {
      return;
    }
    localStorage.setItem('token', authResponse.token);
    const user: AuthUser = {
      id: authResponse.id as string,
      nom: authResponse.nom,
      prenom: authResponse.prenom,
      email: authResponse.email,
      role: authResponse.role as Role,
      emailVerified: authResponse.emailVerified,

      dateInscription: new Date()
    };
    localStorage.setItem('user', JSON.stringify(user));
    this.currentUserSubject.next(user);
  }

  clearSession(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    this.currentUserSubject.next(null);
    this.userProfileSubject.next(null);
  }

  private getUserFromStorage(): AuthUser | null {
    if (typeof localStorage === 'undefined') return null;
    const userStr = localStorage.getItem('user');
    return userStr ? JSON.parse(userStr) : null;
  }
}
