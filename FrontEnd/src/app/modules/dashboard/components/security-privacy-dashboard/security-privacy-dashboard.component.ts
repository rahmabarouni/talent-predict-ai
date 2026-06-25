import { Component, OnInit, inject } from '@angular/core';

import { Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { AuthService } from '../../../auth/services/auth.service';
import { NotificationService } from '../../../../core/services/notification.service';
import {
  PrivacySettingsResponse,
  SecurityDashboardResponse,
  SecurityPrivacyService
} from '../../../../core/services/security-privacy.service';

// French labels for event types
const EVENT_LABELS: Record<string, string> = {
  LOGIN_SUCCESS: 'Connexion réussie',
  LOGIN_FAILED: 'Tentative de connexion échouée',
  LOGOUT: 'Déconnexion',
  PASSWORD_CHANGED: 'Mot de passe modifié',

  EMAIL_VERIFIED: 'Email vérifié',
  ALL_SESSIONS_REVOKED: 'Toutes les sessions révoquées',
  SESSION_REVOKED: 'Session révoquée',
  ACCOUNT_DELETED: 'Compte supprimé',
  PROFILE_UPDATED: 'Profil mis à jour',
  DATA_EXPORTED: 'Données exportées',
  DELETE_REQUESTED: 'Demande de suppression',
};

@Component({
  selector: 'app-security-privacy-dashboard',
  standalone: true,
  imports: [ReactiveFormsModule, FormsModule],
  templateUrl: './security-privacy-dashboard.component.html',
  styleUrl: './security-privacy-dashboard.component.scss'
})
export class SecurityPrivacyDashboardComponent implements OnInit {
  private service = inject(SecurityPrivacyService);
  private notificationService = inject(NotificationService);
  private authService = inject(AuthService);
  private router = inject(Router);
  private fb = inject(FormBuilder);

  loading = true;
  processing = false;



  // Password change
  showPasswordChange = false;
  passwordStrength = 0;
  passwordStrengthLabel = '';

  // Security score
  securityScore = 0;
  securityItems: { label: string; done: boolean; action?: string }[] = [];

  // Deletion countdown
  deletionCountdownDays: number | null = null;

  // Export format
  exportFormat: 'json' | 'pdf' = 'json';

  // Active tab
  activeTab: 'security' | 'sessions' | 'privacy' | 'gdpr' = 'security';

  dashboard: SecurityDashboardResponse | null = null;
  privacySettings: PrivacySettingsResponse | null = null;

  privacyForm = this.fb.nonNullable.group({
    marketingEmailsConsent: false,
    analyticsConsent: true,
    profileVisibilityConsent: true,
    dataProcessingConsent: true,
    dataRetentionDays: [365, [Validators.required, Validators.min(30), Validators.max(3650)]],
    consentVersion: 'v1',
    // Notification preferences
    notifNewLogin: true,
    notifPasswordChange: true,

    notifExportRequest: false,
    notifAdminView: false,
  });



  passwordForm = this.fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', Validators.required]
  });

  deleteForm = this.fb.nonNullable.group({
    confirmPhrase: ['', Validators.required]
  });

  ngOnInit(): void {
    this.loadAll();
  }

  goBack(): void {
    this.router.navigate(['/profile']);
  }

  setTab(tab: 'security' | 'sessions' | 'privacy' | 'gdpr'): void {
    this.activeTab = tab;
  }

  loadAll(): void {
    this.loading = true;

    forkJoin({
      dashboard: this.service.getSecurityDashboard(),
      privacy: this.service.getPrivacySettings()
    }).subscribe({
      next: ({ dashboard, privacy }) => {
        this.dashboard = dashboard;
        this.privacySettings = privacy;
        this.patchPrivacyForm(privacy);
        this.computeSecurityScore(dashboard, privacy);
        this.computeDeletionCountdown(privacy);
      },
      error: (error) => {
        this.notificationService.error(error.error?.message || 'Impossible de charger les paramètres de sécurité.');
      },
      complete: () => {
        this.loading = false;
      }
    });
  }



  // ========== Password ==========

  onNewPasswordChange(): void {
    const pwd = this.passwordForm.get('newPassword')?.value || '';
    let score = 0;
    if (pwd.length >= 8) score++;
    if (/[A-Z]/.test(pwd)) score++;
    if (/[0-9]/.test(pwd)) score++;
    if (/[^A-Za-z0-9]/.test(pwd)) score++;
    this.passwordStrength = score;
    const labels = ['Très faible', 'Faible', 'Moyen', 'Fort', 'Très fort'];
    this.passwordStrengthLabel = labels[score] || '';
  }

  changePassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    const { currentPassword, newPassword, confirmPassword } = this.passwordForm.getRawValue();
    if (newPassword !== confirmPassword) {
      this.notificationService.error('Les mots de passe ne correspondent pas.');
      return;
    }
    this.processing = true;
    this.authService.changePassword({ currentPassword, newPassword }).subscribe({
      next: (res) => {
        this.notificationService.success(res.message || 'Mot de passe modifié avec succès.');
        this.showPasswordChange = false;
        this.passwordForm.reset();
        this.passwordStrength = 0;
      },
      error: (err) => {
        this.notificationService.error(err.error?.message || 'Impossible de modifier le mot de passe.');
      },
      complete: () => { this.processing = false; }
    });
  }

  // ========== Sessions ==========

  revokeSession(sessionId: string): void {
    this.processing = true;
    this.service.revokeSession(sessionId).subscribe({
      next: (response) => {
        this.notificationService.success(response.message || 'Session révoquée.');
        this.reloadDashboard();
      },
      error: (error) => {
        this.notificationService.error(error.error?.message || 'Impossible de révoquer cette session.');
      },
      complete: () => { this.processing = false; }
    });
  }

  revokeAllSessions(): void {
    this.processing = true;
    this.service.revokeAllSessions().subscribe({
      next: (response) => {
        this.notificationService.success(response.message || 'Toutes les sessions ont été révoquées.');
        this.reloadDashboard();
      },
      error: (error) => {
        this.notificationService.error(error.error?.message || 'Impossible de révoquer les sessions.');
      },
      complete: () => { this.processing = false; }
    });
  }

  // ========== Email verification ==========

  resendVerificationEmail(): void {
    const email = this.authService.getCurrentUser()?.email;
    if (!email) {
      this.notificationService.error('Adresse e-mail introuvable.');
      return;
    }
    this.processing = true;
    this.authService.resendVerificationEmail(email).subscribe({
      next: (response) => {
        this.notificationService.info(response.message || 'Lien de vérification renvoyé.');
      },
      error: (error) => {
        this.notificationService.error(error.error?.message || 'Impossible de renvoyer le lien de vérification.');
      },
      complete: () => { this.processing = false; }
    });
  }

  // ========== Privacy ==========

  savePrivacySettings(): void {
    if (this.privacyForm.invalid) {
      this.privacyForm.markAllAsTouched();
      return;
    }
    this.processing = true;
    this.service.updatePrivacySettings(this.privacyForm.getRawValue()).subscribe({
      next: (response) => {
        this.privacySettings = response;
        this.patchPrivacyForm(response);
        this.notificationService.success('Paramètres de confidentialité sauvegardés.');
        this.computeSecurityScore(this.dashboard, response);
      },
      error: (error) => {
        this.notificationService.error(error.error?.message || 'Impossible de sauvegarder les paramètres.');
      },
      complete: () => { this.processing = false; }
    });
  }

  // ========== GDPR ==========

  exportData(): void {
    this.processing = true;
    this.service.exportDataDownload().subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `talentpredict-mes-donnees.${this.exportFormat}`;
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(url);
        this.notificationService.success('Export de données téléchargé.');
      },
      error: (error) => {
        this.notificationService.error(error.error?.message || 'Impossible d\'exporter les données.');
      },
      complete: () => { this.processing = false; }
    });
  }

  applyRetention(): void {
    this.processing = true;
    this.service.applyRetention().subscribe({
      next: (response) => {
        this.notificationService.info(response.message);
      },
      error: (error) => {
        this.notificationService.error(error.error?.message || 'Impossible d\'appliquer la rétention.');
      },
      complete: () => { this.processing = false; }
    });
  }

  requestDeletion(): void {
    this.processing = true;
    this.service.requestDeletion().subscribe({
      next: (response) => {
        this.notificationService.warning(response.message || 'Demande de suppression enregistrée.');
        this.loadAll();
      },
      error: (error) => {
        this.notificationService.error(error.error?.message || 'Impossible d\'enregistrer la demande de suppression.');
      },
      complete: () => { this.processing = false; }
    });
  }

  deleteAccount(): void {
    if (this.deleteForm.invalid) {
      this.deleteForm.markAllAsTouched();
      return;
    }
    this.processing = true;
    const phrase = this.deleteForm.get('confirmPhrase')?.value || '';
    this.service.deleteAccount(phrase).subscribe({
      next: (response) => {
        this.notificationService.warning(response.message || 'Compte anonymisé.');
        this.authService.clearSession();
        this.router.navigate(['/auth/login']);
      },
      error: (error) => {
        this.notificationService.error(error.error?.message || 'Suppression impossible. Vérifiez la phrase de confirmation.');
      },
      complete: () => { this.processing = false; }
    });
  }

  // ========== Helpers ==========

  translateEventType(eventType: string): string {
    return EVENT_LABELS[eventType] || eventType;
  }

  maskIp(ip: string | null | undefined): string {
    if (!ip) return 'Inconnue';
    if (ip === '127.0.0.1' || ip === '::1' || ip === 'localhost') return 'Session locale';
    // Mask last octet: 192.168.1.xxx → 192.168.1.***
    const parts = ip.split('.');
    if (parts.length === 4) {
      return `${parts[0]}.${parts[1]}.${parts[2]}.*`;
    }
    return ip;
  }

  getDeviceLabel(deviceId: string | null | undefined): string {
    if (!deviceId) return 'Appareil inconnu';
    if (deviceId.toLowerCase().includes('mobile')) return '📱 Mobile';
    if (deviceId.toLowerCase().includes('tablet')) return '📋 Tablette';
    return '💻 Ordinateur';
  }

  getStatusBadgeClass(ok: boolean): string {
    return ok ? 'badge-success' : 'badge-warning';
  }

  getEventBadgeClass(eventType: string): string {
    if (eventType.includes('FAILED') || eventType.includes('DELETE')) return 'badge-danger';
    if (eventType.includes('REVOKED') || eventType.includes('DISABLED')) return 'badge-warning';
    return 'badge-success';
  }

  formatDate(value: string | null | undefined): string {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '—';
    return date.toLocaleString('fr-FR');
  }

  formatDateShort(value: string | null | undefined): string {
    if (!value) return '—';
    const date = new Date(value);
    if (isNaN(date.getTime())) return '—';
    return date.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  private computeSecurityScore(
    dashboard: SecurityDashboardResponse | null,
    privacy: PrivacySettingsResponse | null
  ): void {
    const items: { label: string; done: boolean; action?: string }[] = [
      { label: 'Email vérifié', done: !!dashboard?.emailVerified, action: 'Vérifier' },

      { label: 'Mot de passe fort (8+ caractères)', done: true }, // Assumed if logged in
      { label: 'Profil visible aux recruteurs configuré', done: privacy?.profileVisibilityConsent !== undefined },
      { label: 'Consentement RGPD accepté', done: !!privacy?.dataProcessingConsent, action: 'Configurer' },
    ];
    const done = items.filter(i => i.done).length;
    this.securityScore = Math.round((done / items.length) * 100);
    this.securityItems = items;
  }

  private computeDeletionCountdown(privacy: PrivacySettingsResponse | null): void {
    if (!privacy?.deleteRequestedAt) {
      this.deletionCountdownDays = null;
      return;
    }
    const requestDate = new Date(privacy.deleteRequestedAt);
    const deleteDate = new Date(requestDate);
    deleteDate.setDate(deleteDate.getDate() + 28);
    const diff = deleteDate.getTime() - Date.now();
    this.deletionCountdownDays = Math.max(0, Math.ceil(diff / (1000 * 60 * 60 * 24)));
  }

  private reloadDashboard(): void {
    this.service.getSecurityDashboard().subscribe({
      next: dashboard => {
        this.dashboard = dashboard;
        this.computeSecurityScore(dashboard, this.privacySettings);
      }
    });
  }

  private patchPrivacyForm(settings: PrivacySettingsResponse): void {
    this.privacyForm.patchValue({
      marketingEmailsConsent: settings.marketingEmailsConsent,
      analyticsConsent: settings.analyticsConsent,
      profileVisibilityConsent: settings.profileVisibilityConsent,
      dataProcessingConsent: settings.dataProcessingConsent,
      dataRetentionDays: settings.dataRetentionDays,
      consentVersion: settings.consentVersion
    });
  }
}
