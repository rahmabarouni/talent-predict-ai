import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

export interface SecuritySessionInfo {
  id: string;
  deviceId: string;
  createdAt: string;
  expiresAt: string;
  revoked: boolean;
}

export interface LoginEventInfo {
  eventType: string;
  ipAddress: string;
  userAgent: string;
  deviceId: string;
  createdAt: string;
  details: string;
}

export interface SecurityDashboardResponse {
  emailVerified: boolean;

  activeSessions: SecuritySessionInfo[];
  loginHistory: LoginEventInfo[];
}



export interface PrivacySettingsResponse {
  marketingEmailsConsent: boolean;
  analyticsConsent: boolean;
  profileVisibilityConsent: boolean;
  dataProcessingConsent: boolean;
  consentVersion: string;
  consentUpdatedAt: string;
  dataRetentionDays: number;
  deleteRequestedAt: string | null;
}

export interface PrivacySettingsUpdateRequest {
  marketingEmailsConsent?: boolean;
  analyticsConsent?: boolean;
  profileVisibilityConsent?: boolean;
  dataProcessingConsent?: boolean;
  consentVersion?: string;
  dataRetentionDays?: number;
}

export interface RetentionApplyResponse {
  deletedNotifications: number;
  deletedAuditLogs: number;
  message: string;
}

@Injectable({
  providedIn: 'root'
})
export class SecurityPrivacyService {
  private http = inject(HttpClient);

  private securityBaseUrl = `${environment.apiUrl}/security`;
  private privacyBaseUrl = `${environment.apiUrl}/privacy`;

  getSecurityDashboard(): Observable<SecurityDashboardResponse> {
    return this.http.get<SecurityDashboardResponse>(`${this.securityBaseUrl}/dashboard`);
  }

  getSessions(): Observable<SecuritySessionInfo[]> {
    return this.http.get<SecuritySessionInfo[]>(`${this.securityBaseUrl}/sessions`);
  }

  revokeSession(sessionId: string): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.securityBaseUrl}/sessions/${sessionId}`);
  }

  revokeAllSessions(): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.securityBaseUrl}/sessions`);
  }

  getLoginHistory(): Observable<LoginEventInfo[]> {
    return this.http.get<LoginEventInfo[]>(`${this.securityBaseUrl}/login-history`);
  }



  getPrivacySettings(): Observable<PrivacySettingsResponse> {
    return this.http.get<PrivacySettingsResponse>(`${this.privacyBaseUrl}/settings`);
  }

  updatePrivacySettings(request: PrivacySettingsUpdateRequest): Observable<PrivacySettingsResponse> {
    return this.http.put<PrivacySettingsResponse>(`${this.privacyBaseUrl}/settings`, request);
  }

  exportDataDownload(): Observable<Blob> {
    return this.http.get(`${this.privacyBaseUrl}/export/download`, { responseType: 'blob' });
  }

  requestDeletion(): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.privacyBaseUrl}/request-deletion`, {});
  }

  applyRetention(): Observable<RetentionApplyResponse> {
    return this.http.post<RetentionApplyResponse>(`${this.privacyBaseUrl}/apply-retention`, {});
  }

  deleteAccount(confirmPhrase: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.privacyBaseUrl}/delete-account`, { confirmPhrase });
  }
}
