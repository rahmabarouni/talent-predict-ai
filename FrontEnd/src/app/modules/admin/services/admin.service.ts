import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { User, UserRequest } from '../../auth/models/user.model';

export interface UserSummary {
  userId: string;
  formationsTotal: number;
  formationsEnCours: number;
  formationsTerminees: number;
  predictionsCount: number;
  latestPredictionScore: number | null;
  latestPredictionDate: string | null;
  latestPredictionLabel: string | null;
  githubUrl: string | null;
  linkedinUrl: string | null;
}

/**
 * Admin-specific service that wraps /api/users endpoints.
 * All methods require ADMIN role.
 */
@Injectable({
  providedIn: 'root'
})
export class AdminService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/users`;

  /** GET /api/users — List all users */
  getAllUsers(): Observable<User[]> {
    return this.http.get<User[]>(this.baseUrl);
  }

  /** GET /api/users/{id} — Get user by ID */
  getUserById(userId: string): Observable<User> {
    return this.http.get<User>(`${this.baseUrl}/${userId}`);
  }

  /** GET /api/users/{id}/summary — Real aggregated stats for admin panel */
  getUserSummary(userId: string): Observable<UserSummary> {
    return this.http.get<UserSummary>(`${this.baseUrl}/${userId}/summary`);
  }

  /** POST /api/users — Create a new user */
  createUser(data: UserRequest): Observable<User> {
    return this.http.post<User>(this.baseUrl, data);
  }

  /** PUT /api/users/{id} — Update user (including role) */
  updateUser(userId: string, data: Partial<UserRequest>): Observable<User> {
    return this.http.put<User>(`${this.baseUrl}/${userId}`, data);
  }

  /** PUT /api/users/{id} — Update user role */
  updateUserRole(userId: string, role: string): Observable<User> {
    return this.http.put<User>(
      `${this.baseUrl}/${userId}`,
      { role }
    );
  }

  /** DELETE /api/users/{id} — Delete user */
  deleteUser(userId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${userId}`);
  }

  /** GET /api/admin/stats — System-wide statistics */
  getSystemStats(): Observable<any> {
    return this.http.get<any>(`${environment.apiUrl}/admin/stats`);
  }
}
