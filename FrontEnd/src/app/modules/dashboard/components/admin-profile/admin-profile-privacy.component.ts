import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder } from '@angular/forms';
import { Router } from '@angular/router';
import { SecurityPrivacyService, PrivacySettingsResponse } from '../../../../core/services/security-privacy.service';
import { NotificationService } from '../../../../core/services/notification.service';

@Component({
  selector: 'app-admin-profile-privacy',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  template: `
    <div class="profile-form">
      <div class="form-card">
        <div class="card-header">
          <h2><span class="emoji">🔕</span> Préférences de confidentialité (Admin)</h2>
          <p class="subtitle">Gérez les alertes et les traces d'activité de votre compte administrateur</p>
        </div>
        <div class="card-body">
          <form [formGroup]="privacyForm" (ngSubmit)="savePrivacySettings()">
            <div class="toggle-item">
              <div class="toggle-info">
                <span class="toggle-label">Masquer mon activité d'administration</span>
                <span class="toggle-desc">Votre activité n'apparaîtra pas dans les flux publics des employés.</span>
              </div>
              <label class="toggle-switch">
                <input type="checkbox" formControlName="profileVisibilityConsent" />
                <span class="toggle-slider"></span>
              </label>
            </div>

            <div class="toggle-item mt-4">
              <div class="toggle-info">
                <span class="toggle-label">Recevoir les alertes de sécurité par e-mail</span>
                <span class="toggle-desc">Être notifié en cas d'actions suspectes ou d'activités inhabituelles.</span>
              </div>
              <label class="toggle-switch">
                <input type="checkbox" formControlName="notifNewLogin" />
                <span class="toggle-slider"></span>
              </label>
            </div>

            <div class="toggle-item mt-4">
              <div class="toggle-info">
                <span class="toggle-label">Notifications de consultation</span>
                <span class="toggle-desc">Être notifié lorsqu'un rapport d'audit est généré.</span>
              </div>
              <label class="toggle-switch">
                <input type="checkbox" formControlName="notifExportRequest" />
                <span class="toggle-slider"></span>
              </label>
            </div>

            <div class="toggle-item mt-4">
              <div class="toggle-info">
                <span class="toggle-label">Logs d'audit</span>
                <span class="toggle-desc">Conserver une trace de toutes les actions d'administration (Requis).</span>
              </div>
              <label class="toggle-switch">
                <input type="checkbox" formControlName="dataProcessingConsent" />
                <span class="toggle-slider"></span>
              </label>
            </div>

            <div class="mt-5" style="display: flex; gap: 1rem; align-items: center;">
              <button type="submit" class="btn-save-premium" [disabled]="saving || privacyForm.invalid">
                <span class="btn-content">Sauvegarder</span>
              </button>
              <button type="button" class="btn-ghost" (click)="goToFullSecurityDashboard()">
                Voir le tableau de bord complet
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .profile-form { display: flex; flex-direction: column; gap: 1.5rem; }
    .form-card { background: #FFFFFF; border-radius: 0.75rem; box-shadow: 0 1px 2px 0 rgba(0,0,0,0.05); border: 1px solid #E5E7EB; overflow: hidden; }
    .card-header { padding: 1.5rem 2rem; border-bottom: 1px solid #E5E7EB; background: #FAFAFA; }
    .card-header h2 { margin: 0 0 0.25rem; font-size: 1.25rem; color: #111827; display: flex; align-items: center; gap: 0.5rem; }
    .card-header .emoji { font-size: 1.5rem; }
    .card-header .subtitle { margin: 0; color: #6B7280; font-size: 0.9rem; }
    .card-body { padding: 2rem; }
    
    .mt-4 { margin-top: 1.5rem; }
    .mt-5 { margin-top: 2rem; }
    
    .toggle-item { display: flex; align-items: center; justify-content: space-between; }
    .toggle-info { display: flex; flex-direction: column; }
    .toggle-label { font-weight: 600; color: #111827; font-size: 0.95rem; }
    .toggle-desc { font-size: 0.85rem; color: #6B7280; margin-top: 0.25rem; }

    .toggle-switch { position: relative; display: inline-block; width: 44px; height: 24px; }
    .toggle-switch input { opacity: 0; width: 0; height: 0; }
    .toggle-slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background-color: #ccc; transition: .4s; border-radius: 24px; }
    .toggle-slider:before { position: absolute; content: ""; height: 18px; width: 18px; left: 3px; bottom: 3px; background-color: white; transition: .4s; border-radius: 50%; }
    input:checked + .toggle-slider { background-color: #10B981; }
    input:checked + .toggle-slider:before { transform: translateX(20px); }

    .btn-save-premium { background: #4F46E5; color: white; border: none; padding: 0.75rem 2rem; border-radius: 0.5rem; font-size: 1rem; font-weight: 600; cursor: pointer; transition: all 0.2s; }
    .btn-save-premium:hover:not(:disabled) { background: #4338CA; transform: translateY(-1px); }
    .btn-save-premium:disabled { opacity: 0.7; cursor: not-allowed; }
    
    .btn-ghost { background: transparent; border: none; color: #6B7280; font-weight: 500; cursor: pointer; }
    .btn-ghost:hover { color: #4F46E5; text-decoration: underline; }
  `]
})
export class AdminProfilePrivacyComponent implements OnInit {
  private fb = inject(FormBuilder);
  private securityService = inject(SecurityPrivacyService);
  private notificationService = inject(NotificationService);
  private router = inject(Router);

  saving = false;
  privacySettings: PrivacySettingsResponse | null = null;

  privacyForm = this.fb.nonNullable.group({
    profileVisibilityConsent: false,
    notifNewLogin: true,
    notifExportRequest: false,
    dataProcessingConsent: true
  });

  ngOnInit(): void {
    this.securityService.getPrivacySettings().subscribe({
      next: (privacy: any) => {
        this.privacySettings = privacy;
        this.privacyForm.patchValue(privacy);
      }
    });
  }

  savePrivacySettings(): void {
    if (this.privacyForm.invalid) return;
    this.saving = true;
    this.securityService.updatePrivacySettings(this.privacyForm.getRawValue()).subscribe({
      next: (response: any) => {
        this.privacySettings = response;
        this.notificationService.success('Préférences de confidentialité sauvegardées.');
        this.saving = false;
      },
      error: (error: any) => {
        this.notificationService.error(error.error?.message || 'Impossible de sauvegarder les paramètres.');
        this.saving = false;
      }
    });
  }

  goToFullSecurityDashboard(): void {
    this.router.navigate(['/security']);
  }
}
