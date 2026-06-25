import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../../auth/services/auth.service';
import { ProfileResponse } from '../../../auth/models/user.model';
import { AdminProfileGeneralComponent } from './admin-profile-general.component';
import { AdminProfileSecurityComponent } from './admin-profile-security.component';
import { AdminProfilePrivacyComponent } from './admin-profile-privacy.component';

@Component({
  selector: 'app-admin-profile',
  standalone: true,
  imports: [CommonModule, AdminProfileGeneralComponent, AdminProfileSecurityComponent, AdminProfilePrivacyComponent],
  template: `
    <div class="profile-dashboard">
    
      <!-- Premium Hero Header -->
      <div class="profile-hero">
        <div class="hero-bg-shapes">
          <div class="shape shape-1"></div>
          <div class="shape shape-2"></div>
        </div>
        <div class="hero-content">
          <div class="avatar-wrapper">
            <div class="hero-avatar">
              @if (getPhotoUrl()) {
                <img [src]="getPhotoUrl()" alt="Avatar" class="avatar-img" />
              } @else {
                {{ initials }}
              }
            </div>
            <div class="avatar-ring"></div>
          </div>
          <div class="hero-info">
            <div class="hero-title-wrapper">
              <h1 class="hero-name">{{ currentUser?.prenom }} {{ currentUser?.nom }}</h1>
              <span class="verified-badge" title="Profil vérifié">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                </svg>
              </span>
            </div>
            <div class="hero-tags">
              <span class="hero-role-badge">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path><polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline><line x1="12" y1="22.08" x2="12" y2="12"></line></svg>
                Administration RH
              </span>
              <span class="hero-email">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path><polyline points="22,6 12,13 2,6"></polyline></svg>
                {{ currentUser?.email }}
              </span>
            </div>
          </div>
    
          <div class="hero-stats">
            <div class="stat-box">
              <span class="stat-value">{{ profile?.experienceAns || '0' }}</span>
              <span class="stat-label">Ans d'Expérience</span>
            </div>
          </div>
        </div>
      </div>
    
      <!-- Main Content Area -->
      <div class="profile-content">
        @if (loading) {
          <div class="loading-state">
            <div class="spinner"></div>
            <p>Chargement de votre espace personnel...</p>
          </div>
        }
    
        @if (profile && !loading) {
          <div class="profile-layout">
    
            <!-- Left Sidebar (Navigation/Quick Links) -->
            <aside class="profile-sidebar">
              <div class="sidebar-card">
                <h3 class="sidebar-title">Paramètres</h3>
                <ul class="sidebar-nav">
                  <li [class.active]="activeTab === 'general'" (click)="activeTab = 'general'">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>
                    Informations Générales
                  </li>
                  <li [class.active]="activeTab === 'security'" (click)="activeTab = 'security'">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path></svg>
                    Sécurité & Mot de passe
                  </li>
                  <li [class.active]="activeTab === 'privacy'" (click)="activeTab = 'privacy'">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path><path d="M13.73 21a2 2 0 0 1-3.46 0"></path></svg>
                    Confidentialité
                  </li>
                </ul>
              </div>
    
              <div class="sidebar-card quick-links">
                <h3 class="sidebar-title">Réseaux Publics</h3>
                <div class="social-links">
                  @if (profile.lienLinkedin) {
                    <a [href]="profile.lienLinkedin" target="_blank" class="social-btn linkedin">
                      <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M19 0h-14c-2.761 0-5 2.239-5 5v14c0 2.761 2.239 5 5 5h14c2.762 0 5-2.239 5-5v-14c0-2.761-2.238-5-5-5zm-11 19h-3v-11h3v11zm-1.5-12.268c-.966 0-1.75-.79-1.75-1.764s.784-1.764 1.75-1.764 1.75.79 1.75 1.764-.783 1.764-1.75 1.764zm13.5 12.268h-3v-5.604c0-3.368-4-3.113-4 0v5.604h-3v-11h3v1.765c1.396-2.586 7-2.777 7 2.476v6.759z"/></svg>
                    </a>
                  }
                  @if (profile.githubUrl) {
                    <a [href]="profile.githubUrl" target="_blank" class="social-btn github">
                      <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/></svg>
                    </a>
                  }
                  @if (profile.cvUrl) {
                    <a [href]="profile.cvUrl" target="_blank" class="social-btn cv" title="Voir CV">
                      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
                    </a>
                  }
                  @if (!profile.lienLinkedin && !profile.githubUrl && !profile.cvUrl) {
                    <div class="text-muted">
                      <small>Aucun lien configuré</small>
                    </div>
                  }
                </div>
              </div>
            </aside>
    
            <!-- Main Content -->
            <div class="profile-main">
              @if (activeTab === 'general') {
                <app-admin-profile-general 
                  [currentUser]="currentUser" 
                  [profile]="profile"
                  (profileUpdated)="onProfileUpdated($event)">
                </app-admin-profile-general>
              }
    
              @if (activeTab === 'security') {
                <app-admin-profile-security></app-admin-profile-security>
              }
    
              @if (activeTab === 'privacy') {
                <app-admin-profile-privacy></app-admin-profile-privacy>
              }
            </div>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    :host {
      --primary: #4F46E5;
      --primary-hover: #4338CA;
      --primary-light: #EEF2FF;
      --secondary: #10B981;
      --bg-color: #F9FAFB;
      --card-bg: #FFFFFF;
      --text-main: #111827;
      --text-muted: #6B7280;
      --border-color: #E5E7EB;
      --shadow-sm: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
      --shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
      --shadow-lg: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
      --radius-md: 0.5rem;
      --radius-lg: 0.75rem;
      --radius-xl: 1rem;
      display: block;
      background-color: var(--bg-color);
      min-height: 100%;
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    }

    .profile-dashboard { max-width: 1100px; margin: 0 auto; padding: 2rem; }

    /* Premium Hero Header */
    .profile-hero {
      position: relative;
      background: linear-gradient(135deg, #1E1B4B, #4F46E5);
      border-radius: var(--radius-xl);
      padding: 3rem 2.5rem;
      color: white;
      margin-bottom: 2rem;
      overflow: hidden;
      box-shadow: var(--shadow-lg);
    }
    .hero-bg-shapes { position: absolute; top: 0; left: 0; right: 0; bottom: 0; overflow: hidden; z-index: 1; }
    .shape { position: absolute; border-radius: 50%; background: rgba(255, 255, 255, 0.05); }
    .shape-1 { width: 300px; height: 300px; top: -100px; right: -50px; background: radial-gradient(circle, rgba(255,255,255,0.1) 0%, rgba(255,255,255,0) 70%); }
    .shape-2 { width: 200px; height: 200px; bottom: -80px; left: 10%; background: radial-gradient(circle, rgba(255,255,255,0.08) 0%, rgba(255,255,255,0) 70%); }

    .hero-content { position: relative; z-index: 2; display: flex; align-items: center; gap: 2rem; }
    .avatar-wrapper { position: relative; }
    .hero-avatar { width: 100px; height: 100px; border-radius: 50%; background: linear-gradient(135deg, #ffffff, #e0e7ff); color: var(--primary); display: flex; align-items: center; justify-content: center; font-size: 2.5rem; font-weight: 800; box-shadow: 0 0 0 4px rgba(255, 255, 255, 0.2); position: relative; z-index: 2; overflow: hidden; }
    .avatar-img { width: 100%; height: 100%; object-fit: cover; }
    .avatar-ring { position: absolute; top: -6px; left: -6px; right: -6px; bottom: -6px; border-radius: 50%; border: 2px dashed rgba(255,255,255,0.4); animation: rotateRing 20s linear infinite; }
    @keyframes rotateRing { 100% { transform: rotate(360deg); } }

    .hero-info { flex: 1; }
    .hero-title-wrapper { display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.5rem; }
    .hero-name { font-size: 2.25rem; font-weight: 800; margin: 0; letter-spacing: -0.025em; }
    .verified-badge { color: #10B981; display: flex; background: rgba(255,255,255,0.9); border-radius: 50%; padding: 0.25rem; }
    .hero-tags { display: flex; flex-wrap: wrap; gap: 1rem; align-items: center; }
    .hero-role-badge, .hero-email { display: flex; align-items: center; gap: 0.5rem; font-size: 0.95rem; background: rgba(255, 255, 255, 0.15); backdrop-filter: blur(8px); padding: 0.5rem 1rem; border-radius: 9999px; border: 1px solid rgba(255,255,255,0.1); }

    .hero-stats { display: flex; gap: 1.5rem; padding-left: 2rem; border-left: 1px solid rgba(255,255,255,0.2); }
    .stat-box { text-align: center; }
    .stat-value { display: block; font-size: 2rem; font-weight: 800; line-height: 1; margin-bottom: 0.25rem; }
    .stat-label { font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.05em; opacity: 0.8; }

    /* Layout & Sidebar */
    .profile-layout { display: grid; grid-template-columns: 280px 1fr; gap: 2rem; }
    .sidebar-card { background: var(--card-bg); border-radius: var(--radius-lg); padding: 1.5rem; box-shadow: var(--shadow-sm); border: 1px solid var(--border-color); margin-bottom: 1.5rem; }
    .sidebar-title { font-size: 0.9rem; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.05em; margin: 0 0 1rem; padding-bottom: 0.5rem; border-bottom: 1px solid var(--border-color); }
    
    .sidebar-nav { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 0.5rem; }
    .sidebar-nav li { display: flex; align-items: center; gap: 0.75rem; padding: 0.75rem 1rem; border-radius: var(--radius-md); color: var(--text-main); font-weight: 500; font-size: 0.95rem; cursor: pointer; transition: all 0.2s; }
    .sidebar-nav li:hover { background: var(--primary-light); color: var(--primary); }
    .sidebar-nav li.active { background: var(--primary); color: white; }

    .social-links { display: flex; gap: 0.75rem; flex-wrap: wrap; }
    .social-btn { display: flex; align-items: center; justify-content: center; width: 40px; height: 40px; border-radius: 50%; color: white; transition: transform 0.2s, box-shadow 0.2s; }
    .social-btn:hover { transform: translateY(-2px); box-shadow: var(--shadow-md); }
    .social-btn.linkedin { background: #0077b5; }
    .social-btn.github { background: #333; }
    .social-btn.cv { background: var(--primary); }

    .profile-main { display: flex; flex-direction: column; gap: 1.5rem; }

    .loading-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 4rem; color: var(--text-muted); }
    .spinner { width: 40px; height: 40px; border: 3px solid var(--primary-light); border-top-color: var(--primary); border-radius: 50%; animation: spin 1s linear infinite; margin-bottom: 1rem; }
    @keyframes spin { to { transform: rotate(360deg); } }

    @media (max-width: 768px) {
      .profile-layout { grid-template-columns: 1fr; }
      .hero-content { flex-direction: column; text-align: center; }
      .hero-title-wrapper { justify-content: center; }
      .hero-tags { justify-content: center; }
      .hero-stats { border-left: none; padding-left: 0; justify-content: center; width: 100%; border-top: 1px solid rgba(255,255,255,0.2); padding-top: 1rem; margin-top: 1rem; }
    }
  `]
})
export class AdminProfileComponent implements OnInit {
  private authService = inject(AuthService);

  currentUser = this.authService.getCurrentUser();
  profile: ProfileResponse | null = null;
  loading = false;
  activeTab: 'general' | 'security' | 'privacy' = 'general';
  
  photoPreview: string | null = null;

  get initials(): string {
    const u = this.currentUser;
    const prenomInitial = u?.prenom?.charAt(0) || '';
    const nomInitial = u?.nom?.charAt(0) || '';
    const initials = prenomInitial + nomInitial;
    return initials.toUpperCase() || '?';
  }

  ngOnInit(): void {
    if (!this.currentUser?.id) return;
    this.loading = true;
    this.authService.getProfile(this.currentUser.id).subscribe({
      next: (p) => {
        this.profile = p;
        this.loading = false;
      },
      error: () => {
        this.profile = {} as ProfileResponse;
        this.loading = false;
      }
    });
  }

  getPhotoUrl(): string {
    if (this.photoPreview) return this.photoPreview;
    const globalAvatar = this.authService.getAvatarUrl();
    if (globalAvatar) return globalAvatar;
    if (!this.profile?.urlPhoto) return '';
    return this.authService.getAssetUrl(this.profile.urlPhoto);
  }

  onProfileUpdated(event: { profile: ProfileResponse, photoPreview: string | null }): void {
    this.profile = event.profile;
    if (event.photoPreview !== undefined) {
      this.photoPreview = event.photoPreview;
    }
  }
}
