import { Component, inject, OnInit, OnDestroy, ChangeDetectorRef, HostListener, ElementRef } from '@angular/core';

import { RouterLink } from '@angular/router';
import { NotificationService, AppNotification } from '../../../core/services/notification.service';
import { NotificationCenterApiService, ServerNotificationResponse } from '../../../core/services/notification-center-api.service';
import { AuthService } from '../../../modules/auth/services/auth.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-notifications-center',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="notif-center" [class.open]="isOpen">
      <button class="notif-bell" (click)="toggle()" [title]="'Notifications'">
        <svg [class.bell-ring]="unreadCount > 0" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.73 21a2 2 0 0 1-3.46 0" />
        </svg>
        @if (unreadCount > 0) {
        <span class="notif-badge">
          <span class="badge-pulse"></span>
          <span class="badge-text">{{ unreadCount > 9 ? '9+' : unreadCount }}</span>
        </span>
        }
      </button>

      @if (isOpen) {
      <div class="notif-dropdown animate-scale-in">
        <div class="notif-header">
          <div class="notif-header-title">
            <h4>Notifications</h4>
            @if (unreadCount > 0) {
            <span class="header-badge">{{ unreadCount }} new</span>
            }
          </div>
          <div class="notif-actions">
            @if (unreadCount > 0) {
            <button class="notif-action-btn" (click)="markAllRead()" title="Tout marquer comme lu">
               <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
            </button>
            }
            @if (notifications.length > 0) {
            <button class="notif-action-btn danger" (click)="clearAll()" title="Tout effacer">
               <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
            </button>
            }
            <button class="notif-action-btn" (click)="close()" title="Fermer">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>
          </div>
        </div>

        <div class="notif-filters">
          <button class="filter-chip" [class.active]="activeFilter === 'all'" (click)="setFilter('all')">
            All
            <span>{{ notifications.length }}</span>
          </button>
          <button class="filter-chip" [class.active]="activeFilter === 'unread'" (click)="setFilter('unread')">
            Unread
            <span>{{ unreadCount }}</span>
          </button>
        </div>

        <div class="notif-list">
          @if (filteredNotifications().length === 0) {
          <div class="notif-empty">
            <div class="empty-icon-wrapper">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
                <path d="M13.73 21a2 2 0 0 1-3.46 0" />
              </svg>
            </div>
            <h5>{{ activeFilter === 'unread' ? 'No unread notifications' : 'No notifications' }}</h5>
            <p>{{ activeFilter === 'unread' ? 'Everything has been reviewed.' : 'You are up to date with your alerts.' }}</p>
          </div>
          }
          @for (notif of filteredNotifications(); track notif.id) {
          <div class="notif-item" [class.unread]="!notif.read" (click)="markRead(notif.id)">
            <div class="notif-indicator"></div>
            <span class="notif-type-icon" [class]="'type-' + notif.type">
              @switch (notif.type) {
                @case ('success') {
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
                }
                @case ('error') {
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
                }
                @case ('warning') {
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                }
                @case ('info') {
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
                }
              }
            </span>
            <div class="notif-body">
              <span class="notif-title">{{ notif.title }}</span>
              <span class="notif-text">{{ notif.body }}</span>
              @if (notif.targetUrl) {
                <div class="notif-actions-inline">
                  <a [routerLink]="notif.targetUrl" class="notif-action-link" (click)="$event.stopPropagation(); close()">View Details</a>
                </div>
              }
              <span class="notif-time">{{ timeAgo(notif.timestamp) }}</span>
            </div>
            <button class="notif-remove" (click)="remove(notif.id); $event.stopPropagation()" title="Supprimer">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          }
          @if (hasMore && filteredNotifications().length > 0) {
            <div class="load-more-container">
              <button class="load-more-btn" (click)="loadMore(); $event.stopPropagation()">
                 {{ isLoadingMore ? 'Loading...' : 'Load older notifications' }}
              </button>
            </div>
          }
        </div>
      </div>
      }
    </div>
  `,
  styles: [`
    .notif-center {
      position: relative;
    }

    .notif-bell {
      background: none;
      border: none;
      color: var(--text-secondary, #64748b);
      cursor: pointer;
      padding: 0.5rem;
      border-radius: 8px;
      position: relative;
      display: flex;
      align-items: center;
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
    }

    .notif-bell:hover, .notif-center.open .notif-bell {
      background: var(--primary-bg, rgba(99, 102, 241, 0.08));
      color: var(--primary, #6366f1);
    }

    .bell-ring {
      animation: ringing 2.5s ease infinite;
      transform-origin: top center;
    }

    @keyframes ringing {
      0%, 100% { transform: rotate(0); }
      5%, 15%, 25% { transform: rotate(15deg); }
      10%, 20%, 30% { transform: rotate(-15deg); }
      35% { transform: rotate(0); }
    }

    .notif-badge {
      position: absolute;
      top: 4px;
      right: 6px;
      width: 16px;
      height: 16px;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .badge-pulse {
      position: absolute;
      inset: -2px;
      background: #ef4444;
      border-radius: 50%;
      opacity: 0.8;
      animation: ping 1.5s cubic-bezier(0, 0, 0.2, 1) infinite;
    }

    .badge-text {
      position: relative;
      background: #ef4444;
      color: white;
      font-size: 0.6rem;
      font-weight: 800;
      border-radius: 50%;
      width: 100%;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 0 0 2px var(--bg-body, #f8fafc);
      z-index: 10;
    }

    @keyframes ping {
      75%, 100% {
        transform: scale(2);
        opacity: 0;
      }
    }

    .notif-dropdown {
      position: absolute;
      top: calc(100% + 12px);
      right: 0;
      width: 380px;
      max-height: 500px;
      background: rgba(255, 255, 255, 0.95);
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
      border: 1px solid rgba(0, 0, 0, 0.08);
      border-radius: 16px;
      box-shadow: 0 20px 40px -8px rgba(0, 0, 0, 0.15), 0 0 0 1px rgba(0,0,0,0.02);
      display: flex;
      flex-direction: column;
      z-index: 9999;
      transform-origin: top right;
      overflow: hidden;
    }

    .notif-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 1rem 1.25rem;
      border-bottom: 1px solid rgba(0, 0, 0, 0.06);
      background: rgba(255, 255, 255, 0.5);
    }

    .notif-header-title {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .notif-header h4 {
      font-size: 1rem;
      font-weight: 700;
      color: var(--text-primary, #1e293b);
      margin: 0;
      letter-spacing: -0.01em;
    }

    .header-badge {
      background: var(--primary-bg, rgba(99, 102, 241, 0.1));
      color: var(--primary, #6366f1);
      font-size: 0.7rem;
      font-weight: 700;
      padding: 0.2rem 0.5rem;
      border-radius: 999px;
    }

    .notif-actions {
      display: flex;
      gap: 0.375rem;
    }

    .notif-action-btn {
      background: none;
      border: none;
      color: var(--text-secondary, #64748b);
      cursor: pointer;
      padding: 0.375rem;
      border-radius: 8px;
      transition: all 0.2s;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .notif-action-btn:hover {
      background: var(--primary-bg, rgba(99, 102, 241, 0.08));
      color: var(--primary, #6366f1);
    }

    .notif-action-btn.danger:hover {
      background: var(--danger-bg, rgba(239, 68, 68, 0.08));
      color: var(--danger, #ef4444);
    }

    .notif-filters {
      display: flex;
      gap: 0.5rem;
      padding: 0.7rem 1.25rem;
      border-bottom: 1px solid rgba(0, 0, 0, 0.05);
      background: rgba(248, 250, 252, 0.72);
    }

    .filter-chip {
      border: 1px solid rgba(15, 23, 42, 0.08);
      background: white;
      color: var(--text-secondary, #64748b);
      border-radius: 999px;
      font-size: 0.75rem;
      font-weight: 700;
      padding: 0.25rem 0.55rem;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      transition: all 0.2s ease;
    }

    .filter-chip span {
      min-width: 18px;
      height: 18px;
      border-radius: 999px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      background: rgba(100, 116, 139, 0.12);
      color: inherit;
      font-size: 0.68rem;
      line-height: 1;
      padding: 0 0.3rem;
    }

    .filter-chip:hover {
      border-color: rgba(99, 102, 241, 0.35);
      color: var(--primary, #6366f1);
    }

    .filter-chip.active {
      border-color: rgba(99, 102, 241, 0.35);
      background: rgba(99, 102, 241, 0.12);
      color: var(--primary-dark, #4f46e5);
    }

    .filter-chip.active span {
      background: rgba(79, 70, 229, 0.16);
    }

    .notif-list {
      flex: 1;
      overflow-y: auto;
      padding: 0.5rem 0;
    }

    .notif-list::-webkit-scrollbar {
      width: 6px;
    }
    .notif-list::-webkit-scrollbar-thumb {
      background: rgba(0,0,0,0.1);
      border-radius: 3px;
    }

    .notif-empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 3.5rem 1.5rem;
      text-align: center;
    }

    .empty-icon-wrapper {
      width: 64px;
      height: 64px;
      border-radius: 50%;
      background: linear-gradient(135deg, rgba(99, 102, 241, 0.1), rgba(139, 92, 246, 0.1));
      color: var(--primary, #6366f1);
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 1.25rem;
    }

    .notif-empty h5 {
      font-size: 0.9375rem;
      font-weight: 600;
      color: var(--text-primary, #1e293b);
      margin: 0 0 0.375rem 0;
    }

    .notif-empty p {
      font-size: 0.8125rem;
      color: var(--text-secondary, #64748b);
      margin: 0;
    }

    .notif-item {
      display: flex;
      align-items: flex-start;
      gap: 0.875rem;
      padding: 1rem 1.25rem;
      cursor: pointer;
      transition: all 0.2s ease;
      position: relative;
      border-bottom: 1px solid rgba(0, 0, 0, 0.03);
    }

    .notif-item:last-child {
      border-bottom: none;
    }

    .notif-item:hover {
      background: rgba(0,0,0,0.02);
    }

    .notif-indicator {
      position: absolute;
      left: 0;
      top: 1rem;
      bottom: 1rem;
      width: 3px;
      border-radius: 0 4px 4px 0;
      background: var(--primary, #6366f1);
      opacity: 0;
      transform: scaleY(0.5);
      transition: all 0.2s ease;
    }

    .notif-item.unread .notif-indicator {
      opacity: 1;
      transform: scaleY(1);
    }

    .notif-item.unread {
      background: rgba(99, 102, 241, 0.03);
    }
    
    .notif-item.unread:hover {
      background: rgba(99, 102, 241, 0.05);
    }

    .notif-type-icon {
      flex-shrink: 0;
      width: 38px;
      height: 38px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: inset 0 0 0 1px rgba(0,0,0,0.05);
    }

    .type-success { background: var(--success-bg, #dcfce7); color: var(--success, #16a34a); box-shadow: inset 0 0 0 1px rgba(34,197,94,0.1); }
    .type-error { background: var(--danger-bg, #fee2e2); color: var(--danger, #dc2626); box-shadow: inset 0 0 0 1px rgba(239,68,68,0.1); }
    .type-warning { background: var(--warning-bg, #fef3c7); color: var(--warning, #d97706); box-shadow: inset 0 0 0 1px rgba(245,158,11,0.1); }
    .type-info { background: var(--info-bg, #dbeafe); color: var(--info, #2563eb); box-shadow: inset 0 0 0 1px rgba(59,130,246,0.1); }

    .notif-body {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }

    .notif-actions-inline {
      margin-top: 0.25rem;
      margin-bottom: 0.25rem;
    }

    .notif-action-link {
      display: inline-block;
      font-size: 0.75rem;
      font-weight: 600;
      color: var(--primary, #6366f1);
      background: var(--primary-bg, rgba(99, 102, 241, 0.1));
      padding: 0.25rem 0.6rem;
      border-radius: 6px;
      text-decoration: none;
      transition: all 0.2s;
    }

    .notif-action-link:hover {
      background: var(--primary, #6366f1);
      color: white;
    }

    .load-more-container {
      padding: 1rem;
      display: flex;
      justify-content: center;
      border-top: 1px solid rgba(0,0,0,0.03);
    }

    .load-more-btn {
      background: white;
      border: 1px solid rgba(0,0,0,0.1);
      color: var(--text-secondary, #64748b);
      font-size: 0.8125rem;
      font-weight: 600;
      padding: 0.4rem 1rem;
      border-radius: 8px;
      cursor: pointer;
      transition: all 0.2s;
    }

    .load-more-btn:hover {
      background: rgba(0,0,0,0.02);
      color: var(--text-primary, #1e293b);
    }

    .notif-title {
      font-size: 0.875rem;
      font-weight: 600;
      color: var(--text-primary, #1e293b);
      line-height: 1.3;
      transition: color 0.2s;
    }

    .notif-item.unread .notif-title {
      color: var(--primary-dark, #4f46e5);
    }

    .notif-text {
      font-size: 0.8125rem;
      color: var(--text-secondary, #64748b);
      line-height: 1.4;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .notif-time {
      font-size: 0.72rem;
      font-weight: 500;
      color: var(--text-muted, #94a3b8);
      margin-top: 0.25rem;
    }

    .notif-remove {
      background: #ffffff;
      border: 1px solid rgba(0,0,0,0.08);
      cursor: pointer;
      padding: 0.375rem;
      border-radius: 8px;
      color: var(--text-muted, #94a3b8);
      opacity: 0;
      transform: translateX(10px) scale(0.95);
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
      display: flex;
      align-items: center;
      box-shadow: 0 2px 4px rgba(0,0,0,0.02);
      margin-top: 0.25rem;
      flex-shrink: 0;
    }

    .notif-item:hover .notif-remove {
      opacity: 1;
      transform: translateX(0) scale(1);
    }

    .notif-remove:hover {
      color: var(--danger, #ef4444);
      border-color: rgba(239,68,68,0.3);
      background: var(--danger-bg, #fef2f2);
    }

    .animate-scale-in {
      animation: menuScaleIn 0.3s cubic-bezier(0.16, 1, 0.3, 1) forwards;
    }

    @keyframes menuScaleIn {
      from { opacity: 0; transform: scale(0.96) translateY(-10px); }
      to { opacity: 1; transform: scale(1) translateY(0); }
    }

    @media (max-width: 560px) {
      .notif-dropdown {
        width: min(92vw, 380px);
        right: -8px;
      }

      .notif-header {
        padding: 0.85rem 1rem;
      }

      .notif-item {
        padding: 0.85rem 1rem;
      }
    }
  `]
})
export class NotificationsCenterComponent implements OnInit, OnDestroy {
  private notificationService = inject(NotificationService);
  private notificationApi = inject(NotificationCenterApiService);
  private authService = inject(AuthService);
  private cdr = inject(ChangeDetectorRef);
  private elRef = inject(ElementRef);
  private sub!: Subscription;
  private unreadSub!: Subscription;
  private syncInProgress = false;
  private eventSource?: EventSource;
  private timeRefreshInterval?: any;
  private retryDelay = 2000; // Start with 2s
  private maxRetryDelay = 30000; // Max 30s

  notifications: AppNotification[] = [];
  unreadCount = 0;
  isOpen = false;
  activeFilter: 'all' | 'unread' = 'all';
  
  currentPage = 0;
  hasMore = false;
  isLoadingMore = false;

  ngOnInit(): void {
    this.sub = this.notificationService.appNotifications$.subscribe(list => {
      this.notifications = list;
      this.cdr.markForCheck();
    });
    this.unreadSub = this.notificationService.unreadCount$.subscribe(count => {
      this.unreadCount = count;
      this.cdr.markForCheck();
    });

    this.syncFromServer(true);
    
    // Connect to SSE stream for real-time updates
    this.setupSse();

    // Periodically refresh "time ago" display
    this.timeRefreshInterval = setInterval(() => {
      this.cdr.markForCheck();
    }, 60000); // Every minute
  }

  private setupSse(): void {
    const token = this.authService.getToken();
    if (!token) return;

    this.eventSource = this.notificationApi.connectSse(token);
    
    this.eventSource.onopen = () => {
      console.log('SSE connected successfully');
      this.retryDelay = 2000; // Reset delay on success
    };

    this.eventSource.addEventListener('NOTIFICATION', (event: MessageEvent) => {
      try {
        const serverNotif: ServerNotificationResponse = JSON.parse(event.data);
        const mapped = this.mapServerNotification(serverNotif);
        
        // Add to local state via sync
        this.notificationService.syncServerNotifications([mapped]);
        
        // Show toast popup with action link
        if (mapped.type === 'success') this.notificationService.success(mapped.title, 4000, mapped.id);
        else if (mapped.type === 'error') this.notificationService.error(mapped.title, 6000, mapped.id);
        else if (mapped.type === 'warning') this.notificationService.warning(mapped.title, 5000, mapped.id);
        else this.notificationService.info(mapped.title, 4000, mapped.id);
        
      } catch (e) {
        console.error('Error parsing SSE notification', e);
      }
    });

    this.eventSource.onerror = () => {
      console.warn(`SSE connection error, retrying in ${this.retryDelay}ms...`);
      if (this.eventSource) {
        this.eventSource.close();
      }
      
      // Exponential backoff
      setTimeout(() => {
        this.retryDelay = Math.min(this.retryDelay * 1.5, this.maxRetryDelay);
        this.setupSse();
      }, this.retryDelay);
    };
  }

  toggle(): void {
    this.isOpen = !this.isOpen;
    if (this.isOpen && this.notifications.length === 0) {
      this.syncFromServer(true);
    }
    this.cdr.detectChanges();
  }

  close(): void {
    this.isOpen = false;
    this.cdr.markForCheck();
  }

  setFilter(filter: 'all' | 'unread'): void {
    this.activeFilter = filter;
  }

  filteredNotifications(): AppNotification[] {
    if (this.activeFilter === 'unread') {
      return this.notifications.filter(n => !n.read);
    }
    return this.notifications;
  }

  markRead(id: string): void {
    this.notificationService.markRead(id);

    const notification = this.notificationService.getNotificationById(id);
    if (notification?.source === 'server') {
      this.notificationApi.markRead(id).subscribe({
        error: () => this.syncFromServer()
      });
    }
  }

  markAllRead(): void {
    this.notificationService.markAllRead();

    if (this.notifications.some(notification => notification.source === 'server' && !notification.read)) {
      this.notificationApi.markAllRead().subscribe({
        error: () => this.syncFromServer()
      });
    }
  }

  clearAll(): void {
    const hadServerNotifications = this.notifications.some(notification => notification.source === 'server');
    this.notificationService.clearAll();

    if (hadServerNotifications) {
      this.notificationApi.clearAll().subscribe({
        error: () => this.syncFromServer()
      });
    }

    this.activeFilter = 'all';
    this.isOpen = false;
  }

  remove(id: string): void {
    const notification = this.notificationService.getNotificationById(id);
    this.notificationService.removeNotification(id);

    if (notification?.source === 'server') {
      this.notificationApi.delete(id).subscribe({
        error: () => this.syncFromServer()
      });
    }
  }

  timeAgo(ts: number): string {
    const seconds = Math.floor((Date.now() - ts) / 1000);
    if (seconds < 60) return 'À l\'instant';
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `Il y a ${minutes} min`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `Il y a ${hours}h`;
    const days = Math.floor(hours / 24);
    return `Il y a ${days}j`;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    if (this.isOpen && !this.elRef.nativeElement.contains(event.target)) {
      this.isOpen = false;
      this.cdr.markForCheck();
    }
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.isOpen) {
      this.close();
    }
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.unreadSub?.unsubscribe();
    if (this.eventSource) {
      this.eventSource.close();
    }
    if (this.timeRefreshInterval) {
      clearInterval(this.timeRefreshInterval);
    }
  }

  loadMore(): void {
    if (!this.hasMore || this.isLoadingMore) return;
    this.currentPage++;
    this.isLoadingMore = true;
    this.syncFromServer(false);
  }

  private syncFromServer(reset = false): void {
    if (this.syncInProgress && reset) {
      return;
    }

    if (reset) {
      this.currentPage = 0;
    }

    this.syncInProgress = true;
    this.notificationApi.list(false, this.currentPage, 20).subscribe({
      next: (page) => {
        const mappedNotifications = page.content.map(notification => this.mapServerNotification(notification));
        
        if (reset) {
          // Full sync overrides server ones but keeps local ones
          this.notificationService.syncServerNotifications(mappedNotifications);
        } else {
          // Append
          const existing = this.notifications;
          const newUnique = mappedNotifications.filter(n => !existing.some(e => e.id === n.id));
          this.notificationService.syncServerNotifications([...existing, ...newUnique]);
        }
        
        this.hasMore = !page.last;
      },
      error: () => {
        this.syncInProgress = false;
        this.isLoadingMore = false;
      },
      complete: () => {
        this.syncInProgress = false;
        this.isLoadingMore = false;
      }
    });
  }

  private mapServerNotification(notification: ServerNotificationResponse): AppNotification {
    const parsedDate = new Date(notification.createdAt).getTime();

    return {
      id: notification.id,
      type: this.mapType(notification.type),
      title: notification.title,
      body: notification.body,
      timestamp: Number.isNaN(parsedDate) ? Date.now() : parsedDate,
      read: notification.read,
      source: 'server',
      targetUrl: notification.targetUrl
    };
  }

  private mapType(type: string): AppNotification['type'] {
    switch ((type || '').toUpperCase()) {
      case 'SUCCESS':
        return 'success';
      case 'ERROR':
        return 'error';
      case 'WARNING':
        return 'warning';
      default:
        return 'info';
    }
  }
}
