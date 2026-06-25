import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { AuthService } from '../../../auth/services/auth.service';
import { NotificationService } from '../../../../core/services/notification.service';

@Component({
  selector: 'app-admin-profile-security',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  template: `
    <div class="profile-form">
      <div class="form-card">
        <div class="card-header">
          <h2><span class="emoji">🔑</span> Sécurité du compte</h2>
          <p class="subtitle">Gérez votre mot de passe et l'accès à votre compte</p>
        </div>
        <div class="card-body">
          <button type="button" class="btn-secondary-outline" (click)="showPasswordChange = !showPasswordChange">
            {{ showPasswordChange ? 'Annuler' : 'Modifier le mot de passe' }}
          </button>

          @if (showPasswordChange) {
            <form [formGroup]="passwordForm" (ngSubmit)="changePassword()" class="mt-4">
              <div class="form-group">
                <label>Mot de passe actuel</label>
                <input type="password" formControlName="currentPassword" placeholder="Entrez votre mot de passe actuel" autocomplete="current-password" />
              </div>
              <div class="form-group mt-3">
                <label>Nouveau mot de passe</label>
                <input type="password" formControlName="newPassword" placeholder="Minimum 8 caractères" autocomplete="new-password" (input)="onNewPasswordChange()" />
                @if (passwordStrength > 0) {
                  <div class="strength-bar">
                    <div class="strength-fill" [style.width.%]="passwordStrength * 25"
                      [class.weak]="passwordStrength <= 1"
                      [class.medium]="passwordStrength === 2 || passwordStrength === 3"
                    [class.strong]="passwordStrength === 4"></div>
                  </div>
                  <span class="strength-label">{{ passwordStrengthLabel }}</span>
                }
              </div>
              <div class="form-group mt-3">
                <label>Confirmer le mot de passe</label>
                <input type="password" formControlName="confirmPassword" placeholder="Répétez le nouveau mot de passe" autocomplete="new-password" />
              </div>
              <div class="mt-4">
                <button type="submit" class="btn-save-premium" [disabled]="saving || passwordForm.invalid">
                  <span class="btn-content">Mettre à jour le mot de passe</span>
                </button>
              </div>
            </form>
          }
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
    
    .form-group { display: flex; flex-direction: column; gap: 0.5rem; }
    .form-group label { font-size: 0.85rem; font-weight: 600; color: #374151; }
    .form-group input { width: 100%; padding: 0.75rem 1rem; border: 1px solid #E5E7EB; border-radius: 0.5rem; font-size: 0.95rem; color: #111827; background: #F9FAFB; transition: all 0.2s; }
    .form-group input:focus { background: white; border-color: #4F46E5; box-shadow: 0 0 0 4px #EEF2FF; outline: none; }
    
    .mt-3 { margin-top: 1rem; }
    .mt-4 { margin-top: 1.5rem; }
    
    .btn-secondary-outline { background: transparent; border: 1px solid #4F46E5; color: #4F46E5; padding: 0.5rem 1rem; border-radius: 0.5rem; font-weight: 600; cursor: pointer; transition: all 0.2s; }
    .btn-secondary-outline:hover { background: #EEF2FF; }
    
    .btn-save-premium { background: #4F46E5; color: white; border: none; padding: 0.75rem 2rem; border-radius: 0.5rem; font-size: 1rem; font-weight: 600; cursor: pointer; transition: all 0.2s; }
    .btn-save-premium:hover:not(:disabled) { background: #4338CA; transform: translateY(-1px); }
    .btn-save-premium:disabled { opacity: 0.7; cursor: not-allowed; }
    .btn-content { display: flex; align-items: center; justify-content: center; gap: 0.5rem; }

    .strength-bar { height: 4px; background: #e5e7eb; border-radius: 2px; margin-top: 0.5rem; overflow: hidden; }
    .strength-fill { height: 100%; transition: width 0.3s, background-color 0.3s; }
    .strength-fill.weak { background: #ef4444; }
    .strength-fill.medium { background: #f59e0b; }
    .strength-fill.strong { background: #10b981; }
    .strength-label { font-size: 0.75rem; color: #6B7280; margin-top: 0.25rem; display: block; }
  `]
})
export class AdminProfileSecurityComponent {
  private authService = inject(AuthService);
  private notificationService = inject(NotificationService);
  private fb = inject(FormBuilder);

  saving = false;
  showPasswordChange = false;
  passwordStrength = 0;
  passwordStrengthLabel = '';

  passwordForm = this.fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', Validators.required]
  });

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
    if (this.passwordForm.invalid) return;
    const { currentPassword, newPassword, confirmPassword } = this.passwordForm.getRawValue();
    if (newPassword !== confirmPassword) {
      this.notificationService.error('Les mots de passe ne correspondent pas.');
      return;
    }
    this.saving = true;
    this.authService.changePassword({ currentPassword, newPassword }).subscribe({
      next: (res: any) => {
        this.notificationService.success(res.message || 'Mot de passe modifié avec succès.');
        this.showPasswordChange = false;
        this.passwordForm.reset();
        this.passwordStrength = 0;
        this.saving = false;
      },
      error: (err: any) => {
        this.notificationService.error(err.error?.message || 'Impossible de modifier le mot de passe.');
        this.saving = false;
      }
    });
  }
}
