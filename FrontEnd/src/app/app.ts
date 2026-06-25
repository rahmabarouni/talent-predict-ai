import { Component, inject, OnInit, OnDestroy, signal, computed, ApplicationRef } from '@angular/core';

import { Router, RouterOutlet, RouterLink, RouterLinkActive, NavigationEnd } from '@angular/router';
import { filter, Subscription } from 'rxjs';
import { AuthService } from './modules/auth/services/auth.service';
import { NotificationsCenterComponent } from './shared/components/notifications-center/notifications-center.component';
import { NotificationToastComponent } from './shared/components/notification-toast/notification-toast.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NotificationToastComponent, NotificationsCenterComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit, OnDestroy {
  private authService = inject(AuthService);
  private router = inject(Router);
  private appRef = inject(ApplicationRef);
  private subscriptions: Subscription[] = [];

  title = 'TalentPredict';

  /** Signal: true when the current route IS an auth page or landing page */
  private isPublicPage = signal(true);
  /** Signal: true when the user is logged in */
  private authenticated = signal(false);

  /** Signal: avatar URL — updates instantly when profile photo changes */
  avatarUrl = signal<string>(this.authService.getAvatarUrl());

  /**
   * TASK 4: Plain boolean for sidebar open state.
   * Using a plain boolean lets Angular's [style.left] binding update in sync with the DOM.
   */
  isSidebarOpen = true;
  showLogoutModal = false;
  logoutLoading = false;

  /** Computed signal — sidebar shows when logged in AND not on public pages */
  showSidebar = computed(() => !this.isPublicPage() && this.authenticated());

  ngOnInit(): void {
    // TASK 4: Restore sidebar state from localStorage
    if (typeof localStorage !== 'undefined') {
      const saved = localStorage.getItem('sidebarOpen');
      if (window.innerWidth < 768) {
        // Mobile: always start closed
        this.isSidebarOpen = false;
      } else {
        // Desktop: restore from localStorage (default: open)
        this.isSidebarOpen = saved !== 'false';
      }
    }

    // Set initial values from current URL
    this.isPublicPage.set(this.isPublicRoute(this.router.url));
    this.authenticated.set(this.authService.isAuthenticated());

    // Update isPublicPage on every navigation
    this.subscriptions.push(
      this.router.events.pipe(
        filter(event => event instanceof NavigationEnd)
      ).subscribe((event: any) => {
        const url = event.urlAfterRedirects || event.url;
        this.isPublicPage.set(this.isPublicRoute(url));
        this.authenticated.set(this.authService.isAuthenticated());
      })
    );

    // React immediately to auth state changes (login/logout)
    this.subscriptions.push(
      this.authService.currentUser$.subscribe(user => {
        this.authenticated.set(!!user && this.authService.isAuthenticated());
      })
    );

    // React to avatar URL changes (photo upload propagates here instantly)
    this.subscriptions.push(
      this.authService.avatarUrl$.subscribe(url => {
        this.avatarUrl.set(url);
      })
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(s => s.unsubscribe());
  }

  private isPublicRoute(url: string): boolean {
    return url.includes('/auth/') || url === '/' || url === '';
  }

  isAuthenticated(): boolean {
    return this.authService.isAuthenticated();
  }

  isAdmin(): boolean {
    return this.authService.isAdmin();
  }

  isProfileSectionActive(): boolean {
    return this.router.url.startsWith('/profile') || this.router.url.startsWith('/security');
  }

  getCurrentRoleLabel(): string {
    if (this.isAdmin()) return '🏢 RH / Manager';
    return '👤 Employé';
  }

  logout(): void {
    this.logoutLoading = true;
    this.authService.logout().subscribe({
      next: () => {
        this.showLogoutModal = false;
        this.logoutLoading = false;
        this.router.navigateByUrl('/auth/login').then(() => this.appRef.tick());
      },
      error: () => {
        this.showLogoutModal = false;
        this.logoutLoading = false;
        this.router.navigateByUrl('/auth/login').then(() => this.appRef.tick());
      }
    });
  }

  getCurrentUser() {
    return this.authService.getCurrentUser();
  }

  /** TASK 4: Toggle sidebar — persists state in localStorage */
  toggleSidebar(): void {
    this.isSidebarOpen = !this.isSidebarOpen;
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('sidebarOpen', String(this.isSidebarOpen));
    }
  }

  getUserInitials(): string {
    const user = this.authService.getCurrentUser();
    if (!user) return '?';
    return `${user.prenom?.charAt(0) || ''}${user.nom?.charAt(0) || ''}`.toUpperCase();
  }
}
