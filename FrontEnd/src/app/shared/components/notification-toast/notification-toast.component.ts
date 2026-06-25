import { Component, inject, OnInit, OnDestroy, ChangeDetectorRef, ChangeDetectionStrategy } from '@angular/core';

import { NotificationService, Notification } from '../../../core/services/notification.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-notification-toast',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (notification of notifications; track notification.type + notification.message + $index; let i = $index) {
      <div class="toast" [class]="'toast-' + notification.type" (click)="dismiss(i)">
        <span class="toast-icon">
          @switch (notification.type) {
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
        <div class="toast-content">
          <span class="toast-message">{{ notification.message }}</span>
          @if (notification.id) {
            <button class="toast-action" (click)="markAsRead(notification.id, i); $event.stopPropagation()">
              Mark as read
            </button>
          }
        </div>
        <button class="toast-close" (click)="dismiss(i); $event.stopPropagation()">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </div>
    }
  `,
  styles: [`
    :host {
      position: fixed;
      top: 1.5rem;
      right: 1.5rem;
      z-index: 9999;
      display: flex;
      flex-direction: column;
      gap: 0.625rem;
      max-width: 420px;
    }

    .toast {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 1rem 1.25rem;
      border-radius: 12px;
      backdrop-filter: blur(8px);
      cursor: pointer;
      animation: slideIn 0.3s cubic-bezier(0.16, 1, 0.3, 1);
      font-size: 0.875rem;
      font-weight: 600;
      border: 1px solid transparent;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
    }

    .toast-success {
      background: #ecfdf5;
      border-color: #a7f3d0;
      color: #065f46;
    }

    .toast-error {
      background: #fef2f2;
      border-color: #fecaca;
      color: #991b1b;
    }

    .toast-warning {
      background: #fffbeb;
      border-color: #fde68a;
      color: #92400e;
    }

    .toast-info {
      background: #eff6ff;
      border-color: #bfdbfe;
      color: #1e40af;
    }

    .toast-icon {
      flex-shrink: 0;
      display: flex;
      align-items: center;
    }

    .toast-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }

    .toast-message {
      line-height: 1.4;
    }

    .toast-action {
      background: rgba(0, 0, 0, 0.05);
      border: none;
      color: inherit;
      font-size: 0.75rem;
      font-weight: 700;
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      cursor: pointer;
      align-self: flex-start;
      transition: all 0.2s;
    }

    .toast-action:hover {
      background: rgba(0, 0, 0, 0.1);
    }

    .toast-close {
      flex-shrink: 0;
      background: none;
      border: none;
      cursor: pointer;
      opacity: 0.4;
      padding: 0.25rem;
      display: flex;
      align-items: center;
      color: inherit;
      border-radius: 6px;
      transition: all 0.2s;
    }

    .toast-close:hover {
      opacity: 1;
      background: rgba(0, 0, 0, 0.06);
    }

    @keyframes slideIn {
      from {
        opacity: 0;
        transform: translateX(100%) scale(0.95);
      }
      to {
        opacity: 1;
        transform: translateX(0) scale(1);
      }
    }
  `]
})
export class NotificationToastComponent implements OnInit, OnDestroy {
  private notificationService = inject(NotificationService);
  private cdr = inject(ChangeDetectorRef);
  private subscription!: Subscription;

  notifications: Notification[] = [];

  ngOnInit(): void {
    this.subscription = this.notificationService.notifications$.subscribe(notification => {
      this.notifications.push(notification);
      this.cdr.markForCheck(); // Fix NG0100

      // Auto-dismiss after duration
      const duration = notification.duration || 3000;
      setTimeout(() => {
        this.dismiss(this.notifications.indexOf(notification));
      }, duration);
    });
  }

  dismiss(index: number): void {
    if (index >= 0 && index < this.notifications.length) {
      this.notifications.splice(index, 1);
      this.cdr.markForCheck();
    }
  }

  markAsRead(id: string, index: number): void {
    this.notificationService.markRead(id);
    this.dismiss(index);
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }
}
