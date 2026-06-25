import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

export interface ServerNotificationResponse {
  id: string;
  type: string;
  category: string;
  title: string;
  body: string;
  read: boolean;
  createdAt: string;
  readAt: string | null;
  emailAlert: boolean;
  emailedAt: string | null;
  targetUrl: string | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  last: boolean;
  first: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class NotificationCenterApiService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/notifications`;

  list(unreadOnly = false, page = 0, size = 20): Observable<PageResponse<ServerNotificationResponse>> {
    return this.http.get<PageResponse<ServerNotificationResponse>>(this.baseUrl, {
      params: { unreadOnly, page, size }
    });
  }

  connectSse(token: string): EventSource {
    return new EventSource(`${this.baseUrl}/stream?token=${token}`);
  }

  getUnreadCount(): Observable<{ unreadCount: number }> {
    return this.http.get<{ unreadCount: number }>(`${this.baseUrl}/unread-count`);
  }

  markRead(notificationId: string): Observable<ServerNotificationResponse> {
    return this.http.patch<ServerNotificationResponse>(`${this.baseUrl}/${notificationId}/read`, {});
  }

  markAllRead(): Observable<{ message: string }> {
    return this.http.patch<{ message: string }>(`${this.baseUrl}/read-all`, {});
  }

  delete(notificationId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${notificationId}`);
  }

  /**
   * Admin → send a direct in-app notification to a specific user.
   * The message is persisted in the DB and SSE-pushed in real time.
   */
  sendDirect(payload: {
    targetUserId: string;
    title: string;
    body: string;
    type?: 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR';
    targetUrl?: string;
    emailAlert?: boolean;
  }): Observable<ServerNotificationResponse> {
    return this.http.post<ServerNotificationResponse>(`${this.baseUrl}/admin/direct`, payload);
  }

  clearAll(): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.baseUrl}/clear`);
  }
}
