import { Component, inject, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../auth/services/auth.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { ProfileResponse, ProfileUpdateRequest, AuthUser } from '../../../auth/models/user.model';

@Component({
  selector: 'app-admin-profile-general',
  standalone: true,
  imports: [CommonModule, FormsModule],
  providers: [DatePipe],
  template: `
    <form class="profile-form" (ngSubmit)="onSave()" #profileForm="ngForm">
      <div class="form-card">
        <div class="card-header">
          <h2><span class="emoji">💼</span> Profil Professionnel</h2>
          <p class="subtitle">Mettez à jour vos informations de base et votre expertise</p>
        </div>
        <div class="card-body">
          <div class="form-grid">
            <div class="form-group">
              <label>Titre professionnel</label>
              <div class="input-with-icon">
                <svg class="input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>
                <input type="text" [(ngModel)]="form.titreProfessionnel" name="titreProfessionnel" placeholder="Directeur des Ressources Humaines">
              </div>
            </div>

            <div class="form-group">
              <label>Années d'expérience</label>
              <div class="input-with-icon">
                <svg class="input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
                <input type="number" [(ngModel)]="form.experienceAns" name="experienceAns" min="0" max="60" placeholder="Ex: 5">
              </div>
            </div>

            <div class="form-group full-width">
              <label>Niveau d'études</label>
              <div class="input-with-icon">
                <svg class="input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path></svg>
                <input type="text" [(ngModel)]="form.niveauEtudes" name="niveauEtudes" placeholder="Master RH, MBA...">
              </div>
            </div>

            <div class="form-group full-width">
              <label>Bio & Parcours</label>
              <textarea [(ngModel)]="form.description" name="description" rows="4"
              placeholder="Décrivez votre parcours, vos passions et votre vision du recrutement..."></textarea>
            </div>
          </div>
        </div>
      </div>

      <div class="form-card">
        <div class="card-header">
          <h2><span class="emoji">🔗</span> Liens & Réseaux</h2>
          <p class="subtitle">Vos présences en ligne pour un profil complet</p>
        </div>
        <div class="card-body">
          <div class="form-grid">
            <div class="form-group">
              <label>Profil LinkedIn</label>
              <div class="input-with-icon">
                <svg class="input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 8a6 6 0 0 1 6 6v7h-4v-7a2 2 0 0 0-2-2 2 2 0 0 0-2 2v7h-4v-7a6 6 0 0 1 6-6z"></path><rect x="2" y="9" width="4" height="12"></rect><circle cx="4" cy="4" r="2"></circle></svg>
                <input type="url" [(ngModel)]="form.lienLinkedin" name="lienLinkedin" placeholder="https://linkedin.com/in/...">
              </div>
            </div>

            <div class="form-group">
              <label>Profil GitHub</label>
              <div class="input-with-icon">
                <svg class="input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 19c-5 1.5-5-2.5-7-3m14 6v-3.87a3.37 3.37 0 0 0-.94-2.61c3.14-.35 6.44-1.54 6.44-7A5.44 5.44 0 0 0 20 4.77 5.07 5.07 0 0 0 19.91 1S18.73.65 16 2.48a13.38 13.38 0 0 0-7 0C6.27.65 5.09 1 5.09 1A5.07 5.07 0 0 0 5 4.77a5.44 5.44 0 0 0-1.5 3.78c0 5.42 3.3 6.61 6.44 7A3.37 3.37 0 0 0 9 18.13V22"></path></svg>
                <input type="url" [(ngModel)]="form.githubUrl" name="githubUrl" placeholder="https://github.com/...">
              </div>
            </div>

            <div class="form-group">
              <label>Curriculum Vitae (Fichier PDF)</label>
              <div class="file-upload-wrapper">
                <label for="cvInput" class="btn-upload-file">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path><polyline points="13 2 13 9 20 9"></polyline></svg>
                  {{ cvFile ? cvFileName : (profile?.cvUrl ? 'CV enregistré (changer)' : 'Choisir un fichier PDF') }}
                </label>
                <input id="cvInput" type="file" accept=".pdf" (change)="onCvSelected($event)" hidden />
              </div>
            </div>

            <div class="form-group">
              <label>Photo de profil (Image)</label>
              <div class="file-upload-wrapper">
                <label for="photoInput" class="btn-upload-file">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline></svg>
                  {{ photoFile ? photoFile.name : (profile?.urlPhoto ? 'Photo enregistrée (changer)' : 'Choisir une image') }}
                </label>
                <input id="photoInput" type="file" accept="image/*" (change)="onPhotoSelected($event)" hidden />
                @if (profile?.urlPhoto || photoPreview) {
                  <button type="button" class="btn-remove-photo" title="Supprimer la photo" (click)="removePhoto()">🗑️</button>
                }
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="form-actions-floating">
        <div class="action-info">
          @if (success) {
            <span class="last-saved">Enregistré à {{ currentTime | date:'HH:mm' }}</span>
          }
        </div>
        <button type="submit" class="btn-save-premium" [disabled]="saving">
          @if (!saving) {
            <span class="btn-content">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"></path><polyline points="17 21 17 13 7 13 7 21"></polyline><polyline points="7 3 7 8 15 8"></polyline></svg>
              Mettre à jour le profil
            </span>
          }
          @if (saving) {
            <span class="btn-content">
              <div class="mini-spinner"></div>
              Enregistrement...
            </span>
          }
        </button>
      </div>
    </form>
  `,
  styles: [`
    .profile-form { display: flex; flex-direction: column; gap: 1.5rem; }
    .form-card { background: #FFFFFF; border-radius: 0.75rem; box-shadow: 0 1px 2px 0 rgba(0,0,0,0.05); border: 1px solid #E5E7EB; overflow: hidden; }
    .card-header { padding: 1.5rem 2rem; border-bottom: 1px solid #E5E7EB; background: #FAFAFA; }
    .card-header h2 { margin: 0 0 0.25rem; font-size: 1.25rem; color: #111827; display: flex; align-items: center; gap: 0.5rem; }
    .card-header .emoji { font-size: 1.5rem; }
    .card-header .subtitle { margin: 0; color: #6B7280; font-size: 0.9rem; }
    .card-body { padding: 2rem; }
    
    .form-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 1.5rem; }
    .form-group { display: flex; flex-direction: column; gap: 0.5rem; }
    .form-group.full-width { grid-column: 1 / -1; }
    .form-group label { font-size: 0.85rem; font-weight: 600; color: #374151; }
    
    .input-with-icon { position: relative; display: flex; align-items: center; }
    .input-icon { position: absolute; left: 1rem; color: #9CA3AF; pointer-events: none; }
    .form-group input, .form-group textarea { width: 100%; padding: 0.75rem 1rem; border: 1px solid #E5E7EB; border-radius: 0.5rem; font-size: 0.95rem; color: #111827; background: #F9FAFB; transition: all 0.2s; font-family: inherit; }
    .form-group input { padding-left: 2.75rem; }
    .form-group textarea { resize: vertical; min-height: 100px; }
    .form-group input:focus, .form-group textarea:focus { background: white; border-color: #4F46E5; box-shadow: 0 0 0 4px #EEF2FF; outline: none; }
    .form-group input:focus + .input-icon { color: #4F46E5; }
    
    .file-upload-wrapper { display: flex; align-items: center; gap: 0.5rem; }
    .btn-upload-file { display: flex; align-items: center; gap: 0.5rem; background: #F9FAFB; border: 1px dashed #E5E7EB; padding: 0.75rem 1rem; border-radius: 0.5rem; font-size: 0.95rem; color: #111827; cursor: pointer; flex: 1; transition: all 0.2s; }
    .btn-upload-file:hover { background: #EEF2FF; border-color: #4F46E5; }
    .btn-remove-photo { background: #FEF2F2; border: 1px solid #FCA5A5; padding: 0.75rem; border-radius: 0.5rem; cursor: pointer; transition: all 0.2s; }
    .btn-remove-photo:hover { background: #FEE2E2; }
    
    .form-actions-floating { display: flex; align-items: center; justify-content: space-between; background: white; padding: 1rem 2rem; border-radius: 0.75rem; border: 1px solid #E5E7EB; box-shadow: 0 10px 15px -3px rgba(0,0,0,0.05); position: sticky; bottom: 1rem; z-index: 10; }
    .action-info { font-size: 0.85rem; color: #10B981; font-weight: 500; }
    
    .btn-save-premium { background: #4F46E5; color: white; border: none; padding: 0.75rem 2rem; border-radius: 0.5rem; font-size: 1rem; font-weight: 600; cursor: pointer; transition: all 0.2s; }
    .btn-save-premium:hover:not(:disabled) { background: #4338CA; transform: translateY(-1px); }
    .btn-save-premium:disabled { opacity: 0.7; cursor: not-allowed; }
    
    .mini-spinner { width: 18px; height: 18px; border: 2px solid rgba(255,255,255,0.3); border-top-color: white; border-radius: 50%; animation: spin 1s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }
    
    @media (max-width: 768px) {
      .form-grid { grid-template-columns: 1fr; }
      .form-actions-floating { flex-direction: column; gap: 1rem; position: relative; bottom: 0; }
      .btn-save-premium { width: 100%; justify-content: center; }
    }
  `]
})
export class AdminProfileGeneralComponent implements OnInit {
  @Input() currentUser!: AuthUser | null;
  @Input() profile!: ProfileResponse | null;
  @Output() profileUpdated = new EventEmitter<{ profile: ProfileResponse, photoPreview: string | null }>();

  private authService = inject(AuthService);
  private notificationService = inject(NotificationService);

  saving = false;
  success = false;
  currentTime = new Date();

  form: ProfileUpdateRequest = {
    titreProfessionnel: '',
    description: '',
    experienceAns: undefined,
    niveauEtudes: '',
    lienLinkedin: '',
    githubUrl: '',
    cvUrl: '',
    urlPhoto: ''
  };

  photoFile: File | null = null;
  photoPreview: string | null = null;
  cvFile: File | null = null;
  cvFileName: string | null = null;

  ngOnInit(): void {
    if (this.profile) {
      this.form = {
        titreProfessionnel: this.profile.titreProfessionnel || '',
        description: this.profile.description || '',
        experienceAns: this.profile.experienceAns ?? undefined,
        niveauEtudes: this.profile.niveauEtudes || '',
        lienLinkedin: this.profile.lienLinkedin || '',
        githubUrl: this.profile.githubUrl || '',
        cvUrl: this.profile.cvUrl || '',
        urlPhoto: this.profile.urlPhoto || ''
      };
    }
  }

  onPhotoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      this.notificationService.error('Seules les images sont acceptées (JPG, PNG, WebP…)');
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      this.photoPreview = reader.result as string;
    };
    reader.readAsDataURL(file);
    this.photoFile = file;
  }

  removePhoto(): void {
    this.photoFile = null;
    this.photoPreview = null;
    if (this.profile) {
      this.profile = { ...this.profile, urlPhoto: '' };
    }
    this.authService.setAvatarUrl('');
    this.profileUpdated.emit({ profile: this.profile as ProfileResponse, photoPreview: null });
  }

  onCvSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (!file.name.toLowerCase().endsWith('.pdf')) {
      this.notificationService.error('Seuls les fichiers PDF sont acceptés pour le CV.');
      return;
    }
    this.cvFile = file;
    this.cvFileName = file.name;
  }

  onSave(): void {
    if (!this.currentUser?.id || this.saving) return;
    this.saving = true;
    this.success = false;
    this.currentTime = new Date();

    const userId = String(this.currentUser.id).trim();

    this.authService.updateProfile(userId, this.form).subscribe({
      next: (updated: any) => {
        this.profile = updated;
        this.doFileUploads(userId);
      },
      error: (err: any) => {
        this.saving = false;
        this.notificationService.error('Erreur lors de la mise à jour.');
      }
    });
  }

  private doFileUploads(userId: string): void {
    const uploadPhoto = (next: () => void) => {
      if (this.photoFile) {
        this.authService.uploadProfilePhoto(userId, this.photoFile).subscribe({
          next: (p: any) => {
            this.profile = p;
            this.photoFile = null;
            // keep photoPreview so it displays instantly
            next();
          },
          error: () => next()
        });
      } else {
        next();
      }
    };

    const uploadCv = (next: () => void) => {
      if (this.cvFile) {
        this.authService.uploadCv(userId, this.cvFile).subscribe({
          next: (res: any) => {
            this.cvFile = null;
            this.cvFileName = null;
            this.notificationService.success(res.message || 'CV analysé avec succès !');
            next();
          },
          error: () => next()
        });
      } else {
        next();
      }
    };

    uploadPhoto(() => {
      uploadCv(() => {
        this.saving = false;
        this.success = true;
        this.profileUpdated.emit({ profile: this.profile as ProfileResponse, photoPreview: this.photoPreview });
        this.notificationService.success('Profil mis à jour avec succès !');
        setTimeout(() => this.success = false, 4000);
      });
    });
  }
}
