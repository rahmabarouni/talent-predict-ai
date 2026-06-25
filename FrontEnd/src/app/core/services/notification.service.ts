import { Injectable } from '@angular/core';
import { Subject, Observable, BehaviorSubject } from 'rxjs';

export interface Notification {
  type: 'success' | 'error' | 'info' | 'warning';
  message: string;
  duration?: number;
  id?: string; // Link to app notification
}

export interface AppNotification {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  title: string;
  body: string;
  timestamp: number;
  read: boolean;
  source: 'local' | 'server';
  targetUrl?: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  // Toast notifications
  private notificationSubject = new Subject<Notification>();
  public notifications$: Observable<Notification> = this.notificationSubject.asObservable();

  // Center app notifications
  private appNotifSubject = new BehaviorSubject<AppNotification[]>([]);
  public appNotifications$: Observable<AppNotification[]> = this.appNotifSubject.asObservable();
  
  private unreadCountSubject = new BehaviorSubject<number>(0);
  public unreadCount$: Observable<number> = this.unreadCountSubject.asObservable();

  success(message: string, duration = 3000, id?: string): void {
    this.show({ type: 'success', message, duration, id });
    if (!id) this.addAppNotification('success', 'Succès', message);
  }

  error(message: string, duration = 5000, id?: string): void {
    this.show({ type: 'error', message, duration, id });
    if (!id) this.addAppNotification('error', 'Erreur', message);
  }

  info(message: string, duration = 3000, id?: string): void {
    this.show({ type: 'info', message, duration, id });
    if (!id) this.addAppNotification('info', 'Information', message);
  }

  warning(message: string, duration = 4000, id?: string): void {
    this.show({ type: 'warning', message, duration, id });
    if (!id) this.addAppNotification('warning', 'Attention', message);
  }

  private show(notification: Notification): void {
    this.notificationSubject.next(notification);
  }

  // ---- App Notifications Center Methods ----

  private addAppNotification(type: 'success' | 'error' | 'warning' | 'info', title: string, body: string): void {
    const newNotif: AppNotification = {
      id: `local-${Math.random().toString(36).substring(2, 9)}`,
      type,
      title,
      body,
      timestamp: Date.now(),
      read: false,
      source: 'local'
    };
    
    const current = this.appNotifSubject.value;
    const updated = [newNotif, ...current].slice(0, 50); // Keep last 50
    this.appNotifSubject.next(updated);
    this.updateUnreadCount();
  }

  syncServerNotifications(serverNotifications: AppNotification[]): void {
    const localNotifications = this.appNotifSubject.value.filter(notification => notification.source === 'local');

    // Simple grouping logic: if multiple notifications of same type/title/body arrive, 
    // we could group them, but for now we just deduplicate by ID.
    const merged = [...serverNotifications, ...localNotifications]
      .filter((v, i, a) => a.findIndex(t => t.id === v.id) === i)
      .sort((a, b) => b.timestamp - a.timestamp)
      .slice(0, 100);

    this.appNotifSubject.next(merged);
    this.updateUnreadCount();
  }

  getNotificationById(id: string): AppNotification | undefined {
    return this.appNotifSubject.value.find(notification => notification.id === id);
  }

  markRead(id: string): void {
    const current = this.appNotifSubject.value;
    const updated = current.map(n => n.id === id ? { ...n, read: true } : n);
    this.appNotifSubject.next(updated);
    this.updateUnreadCount();
  }

  markAllRead(): void {
    const current = this.appNotifSubject.value;
    const updated = current.map(n => ({ ...n, read: true }));
    this.appNotifSubject.next(updated);
    this.updateUnreadCount();
  }

  clearAll(): void {
    this.appNotifSubject.next([]);
    this.updateUnreadCount();
  }

  removeNotification(id: string): void {
    const current = this.appNotifSubject.value;
    const updated = current.filter(n => n.id !== id);
    this.appNotifSubject.next(updated);
    this.updateUnreadCount();
  }

  private updateUnreadCount(): void {
    const current = this.appNotifSubject.value;
    const count = current.filter(n => !n.read).length;
    this.unreadCountSubject.next(count);
  }
}
