import { Component, OnInit, OnDestroy, inject, ChangeDetectorRef } from '@angular/core';

import { FormBuilder, FormGroup, ReactiveFormsModule, FormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../../auth/services/auth.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { ProfileCompletenessComponent } from '../../../../shared/components/profile-completeness/profile-completeness.component';
import { ProfileResponse } from '../../../auth/models/user.model';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-user-profile',
  standalone: true,
  imports: [ReactiveFormsModule, FormsModule, RouterModule, ProfileCompletenessComponent],
  templateUrl: './user-profile.component.html',
  styleUrl: './user-profile.component.scss'
})
export class UserProfileComponent implements OnInit, OnDestroy {
  private authService = inject(AuthService);
  private notificationService = inject(NotificationService);
  private fb = inject(FormBuilder);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);

  profile: ProfileResponse | null = null;
  loading = true;
  saving = false;
  error: string | null = null;
  generatingBio = false;
  lastUpdated: string | null = null;

  // File upload state
  photoFile: File | null = null;
  photoPreview: string | null = null;
  cvFile: File | null = null;
  cvFileName: string | null = null;

  // Crop modal state
  showCropModal = false;
  cropImageSrc: string | null = null;

  readonly DEPARTMENTS = ['Engineering', 'Product', 'Design', 'Data', 'RH', 'Finance', 'Marketing', 'Operations'];

  profileForm!: FormGroup;
  private profileSubscription?: Subscription;

  ngOnInit(): void {
    this.profileForm = this.fb.group({
      titreProfessionnel: [''],
      description: ['', [Validators.minLength(20)]],
      experienceAns: [null],
      niveauEtudes: [''],
      lienLinkedin: [''],
      githubUrl: [''],
      portfolioUrl: [''],
      poste: [''],
      departementEditable: [''],
      ville: ['']
    });

    setTimeout(() => {
      if (this.loading) {
        this.loading = false;
        this.error = 'Délai d\'attente dépassé. Vérifiez que le backend est démarré.';
        this.cdr.detectChanges();
      }
    }, 8000);

    this.loadProfile();
  }

  loadProfile(): void {
    this.loading = true;
    this.error = null;
    const user = this.authService.getCurrentUser();
    if (!user) {
      this.error = 'Utilisateur non connecté.';
      this.loading = false;
      return;
    }

    if (this.profileSubscription) {
      this.profileSubscription.unsubscribe();
    }

    const userId = user.id != null ? String(user.id).trim() : '';
    if (!userId) {
      this.error = 'Session invalide (identifiant manquant). Reconnectez-vous.';
      this.loading = false;
      return;
    }

    this.profileSubscription = this.authService.getProfile(userId).subscribe({
      next: (profile) => {
        try {
          this.profile = profile ?? ({} as ProfileResponse);
          this.patchForm(this.profile);
          this._cacheProfileUrls(this.profile);
          this.lastUpdated = (profile as any).updatedAt || null;
          this.loading = false;
          this.error = null;
          this.cdr.detectChanges();
        } catch {
          this.error = 'Erreur lors du traitement du profil.';
          this.loading = false;
          this.cdr.detectChanges();
        }
      },
      error: (err) => {
        const msg = err?.error?.message ?? err?.error?.error ?? err?.message;
        this.error = msg && typeof msg === 'string' ? msg : 'Impossible de charger le profil. Vérifiez que le backend est démarré (port 8081).';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  private patchForm(profile: ProfileResponse | any): void {
    if (!profile) profile = {};
    this.profileForm.patchValue({
      titreProfessionnel: profile.titreProfessionnel ?? '',
      description: profile.description ?? '',
      experienceAns: profile.experienceAns ?? null,
      niveauEtudes: profile.niveauEtudes ?? '',
      lienLinkedin: profile.lienLinkedin ?? '',
      githubUrl: profile.githubUrl ?? '',
      portfolioUrl: profile.portfolioUrl ?? '',
      poste: profile.poste ?? '',
      departementEditable: profile.departementEditable ?? '',
      ville: profile.ville ?? ''
    });
  }

  // ========== Photo handling ==========

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
      this.cropImageSrc = reader.result as string;
      this.showCropModal = true;
      this.cdr.detectChanges();
    };
    reader.readAsDataURL(file);
    this.photoFile = file;
  }

  confirmCrop(): void {
    this.photoPreview = this.cropImageSrc;
    this.showCropModal = false;
    this.cdr.detectChanges();
  }

  cancelCrop(): void {
    this.showCropModal = false;
    this.cropImageSrc = null;
    this.photoFile = null;
    this.cdr.detectChanges();
  }

  removePhoto(): void {
    this.photoFile = null;
    this.photoPreview = null;
    this.cropImageSrc = null;
    // Optionally clear the profile photo URL for display
    if (this.profile) {
      this.profile = { ...this.profile, urlPhoto: '' };
    }
    this.authService.setAvatarUrl('');
    this.cdr.detectChanges();
  }

  // ========== CV handling ==========

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
    this.cdr.detectChanges();
  }

  // ========== Photo URL ==========

  getPhotoUrl(): string {
    if (this.photoPreview) return this.photoPreview;
    const globalAvatar = this.authService.getAvatarUrl();
    if (globalAvatar) return globalAvatar;
    if (!this.profile?.urlPhoto) return '';
    return this.authService.getAssetUrl(this.profile.urlPhoto);
  }

  getCvUrl(): string {
    if (!this.profile?.cvUrl) return '';
    return this.authService.getAssetUrl(this.profile.cvUrl);
  }

  // ========== Initials avatar ==========

  get initials(): string {
    const user = this.authService.getCurrentUser();
    if (!user) return '?';
    return `${user.prenom?.charAt(0) || ''}${user.nom?.charAt(0) || ''}`.toUpperCase();
  }

  get initialsColor(): string {
    const user = this.authService.getCurrentUser();
    const name = `${user?.prenom || ''}${user?.nom || ''}`;
    const colors = ['#0f766e', '#1d4ed8', '#7c3aed', '#b45309', '#be123c', '#0ea5e9'];
    let hash = 0;
    for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash);
    return colors[Math.abs(hash) % colors.length];
  }


  // ========== Bio counter ==========

  get bioLength(): number {
    return (this.profileForm.get('description')?.value || '').length;
  }

  get bioTooShort(): boolean {
    const val = this.profileForm.get('description')?.value || '';
    return val.length > 0 && val.length < 20;
  }

  // ========== AI Bio generator ==========

  generateBio(): void {
    const titre = this.profileForm.get('titreProfessionnel')?.value || '';
    const exp = this.profileForm.get('experienceAns')?.value || 0;
    const poste = this.profileForm.get('poste')?.value || '';
    this.generatingBio = true;

    // Simulate AI generation (replace with real API call when available)
    setTimeout(() => {
      const bio = `Professionnel passionné avec ${exp} ans d'expérience en tant que ${titre || poste}. ` +
        `Je suis à la recherche d'opportunités permettant de combiner expertise technique et impact business. ` +
        `Orienté résultats, je m'investis dans chaque projet avec rigueur et créativité.`;
      this.profileForm.patchValue({ description: bio });
      this.generatingBio = false;
      this.cdr.detectChanges();
    }, 1200);
  }

  // ========== URL normalization ==========

  private normalizeUrl(url: string): string {
    if (!url) return '';
    if (url.startsWith('http://') || url.startsWith('https://')) return url;
    return 'https://' + url;
  }



  // ========== Form actions ==========

  resetForm(): void {
    if (!this.profile) return;
    this.patchForm(this.profile);
    this.photoFile = null;
    this.photoPreview = null;
    this.cvFile = null;
    this.cvFileName = null;
    this.cdr.detectChanges();
  }

  saveProfile(): void {
    if (this.bioTooShort) {
      this.notificationService.error('La bio doit contenir au moins 20 caractères.');
      return;
    }

    const user = this.authService.getCurrentUser();
    if (!user) return;
    this.saving = true;
    const userId = String(user.id).trim();

    // Normalize URLs before saving
    const formVal = { ...this.profileForm.value };
    formVal.lienLinkedin = this.normalizeUrl(formVal.lienLinkedin);
    formVal.githubUrl = this.normalizeUrl(formVal.githubUrl);
    formVal.portfolioUrl = this.normalizeUrl(formVal.portfolioUrl);

    this.authService.updateProfile(userId, formVal).subscribe({
      next: (updatedProfile) => {
        this.profile = updatedProfile;
        this.lastUpdated = new Date().toISOString();
        this.doFileUploads(userId);
      },
      error: (err) => {
        this.saving = false;
        this.notificationService.error(err?.error?.message || 'Erreur lors de la mise à jour du profil.');
        this.cdr.detectChanges();
      }
    });
  }

  private doFileUploads(userId: string): void {
    const uploadPhoto = (next: () => void) => {
      if (this.photoFile) {
        this.authService.uploadProfilePhoto(userId, this.photoFile).subscribe({
          next: (p) => {
            this.profile = p;
            this.photoFile = null;
            this.photoPreview = null;
            // avatarUrl$ is updated inside the service
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
        this.authService.uploadCv(userId, this.cvFile!).subscribe({
          next: (res) => {
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
        this.authService.getProfile(userId).subscribe({
          next: (p) => {
            this.profile = p;
            this.patchForm(p);
            this._cacheProfileUrls(p);
            this.lastUpdated = new Date().toISOString();
            this.saving = false;
            this.notificationService.success('Profil mis à jour avec succès !');
            this.cdr.detectChanges();
          },
          error: () => {
            this.saving = false;
            this.notificationService.success('Profil mis à jour avec succès !');
            this.cdr.detectChanges();
          }
        });
      });
    });
  }

  publishProfile(): void {
    const user = this.authService.getCurrentUser();
    if (!user) return;
    const userId = String(user.id).trim();

    this.authService.publishProfile(userId).subscribe({
      next: (profile) => {
        this.profile = profile;
        this.notificationService.success('Votre profil est maintenant public !');
        this.cdr.detectChanges();
      },
      error: () => {
        this.notificationService.error('Erreur lors de la publication du profil.');
      }
    });
  }

  goBack(): void {
    this.router.navigate([this.authService.isAdmin() ? '/admin/dashboard' : '/dashboard']);
  }

  goToCompetences(): void {
    this.router.navigate(['/competences']);
  }

  goToSecurity(): void {
    this.router.navigate(['/security']);
  }

  formatLastUpdated(): string {
    if (!this.lastUpdated) return '';
    const date = new Date(this.lastUpdated);
    if (isNaN(date.getTime())) return '';
    return date.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  private _cacheProfileUrls(profile: any): void {
    if (!profile) return;
    try {
      sessionStorage.setItem('userProfileUrls', JSON.stringify({
        linkedinUrl: profile.lienLinkedin ?? '',
        githubUrl: profile.githubUrl ?? '',
        portfolioUrl: profile.portfolioUrl ?? '',
        titreProfessionnel: profile.titreProfessionnel ?? ''
      }));
    } catch { }
  }

  ngOnDestroy(): void {
    this.profileSubscription?.unsubscribe();
  }
}
