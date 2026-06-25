import { HttpClient } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';

import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../../auth/services/auth.service';
import { CvExtractorService } from '../../../../core/services/cv-extractor.service';
import { NotificationService } from '../../../../core/services/notification.service';

@Component({
  selector: 'app-pcm-intro',
  standalone: true,
  imports: [ReactiveFormsModule, RouterModule],
  templateUrl: './pcm-intro.component.html',
  styleUrl: './pcm-intro.component.scss'
})
export class PcmIntroComponent implements OnInit {
  profileForm!: FormGroup;
  selectedFile: File | null = null;
  fileError: string | null = null;
  extractionError: string | null = null;
  isExtracting = false;
  extractedCvText = '';
  currentUser: any;

  constructor(
    private fb: FormBuilder,
    private router: Router,
    private authService: AuthService,
    private cvExtractorService: CvExtractorService,
    private notificationService: NotificationService,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    this.currentUser = this.authService.getCurrentUser();
    
    // Construct full name from current user as fallback
    const initialName = this.currentUser 
      ? `${this.currentUser.prenom || ''} ${this.currentUser.nom || ''}`.trim() 
      : '';

    this.profileForm = this.fb.group({
      fullName: [initialName, Validators.required],
      email: [this.currentUser?.email || '', [Validators.required, Validators.email]],
      githubUsername: [''],
    });

    // 1. Try loading from session cache first for instant UX
    this.loadFromCache();

    // 2. Fetch fresh profile data to ensure all fields are correctly auto-filled
    if (this.currentUser?.id) {
      this.authService.getProfile(this.currentUser.id).subscribe({
        next: (profile) => {
          if (profile) {
            const name = `${profile.firstName || ''} ${profile.lastName || ''}`.trim();
            if (name) this.profileForm.patchValue({ fullName: name });
            if (profile.githubUrl) this.profileForm.patchValue({ githubUsername: profile.githubUrl });
            
            // Auto-fill CV if present in profile
            if (profile.cvUrl) {
              this.loadCvFromUrl(profile.cvUrl);
            }
          }
        },
        error: () => {
          // If profile fetch fails, we still have the session cache and initial name
        }
      });
    }
  }

  private loadFromCache(): void {
    try {
      const cached = sessionStorage.getItem('userProfileUrls');
      if (cached) {
        const urls = JSON.parse(cached);
        if (urls.githubUrl) this.profileForm.patchValue({ githubUsername: urls.githubUrl });
      }
    } catch {}
  }

  private loadCvFromUrl(cvUrl: string): void {
    const absoluteUrl = this.authService.getAssetUrl(cvUrl);
    this.isExtracting = true;
    this.http.get(absoluteUrl, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const filename = cvUrl.substring(cvUrl.lastIndexOf('/') + 1) || 'cv.pdf';
        const file = new File([blob], filename, { type: blob.type || 'application/pdf' });
        this.selectedFile = file;
        this.isExtracting = false;

        this.cvExtractorService.extractFromFile(file).then(extracted => {
          this.extractedCvText = extracted.text || '';
        }).catch(err => {
          console.warn('Extraction of preloaded CV failed:', err);
        });
      },
      error: (err) => {
        console.warn('Failed to load CV from profile:', err);
        this.isExtracting = false;
      }
    });
  }

  async onFileSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;

    const file = input.files[0];
    this.extractionError = null;
    this.extractedCvText = '';

    const lowerName = file.name.toLowerCase();
    const isPdf = file.type === 'application/pdf' || lowerName.endsWith('.pdf');
    const isTxt = file.type === 'text/plain' || lowerName.endsWith('.txt');

    if (!isPdf && !isTxt) {
      this.fileError = 'Seuls les fichiers PDF ou TXT sont acceptés.';
      this.selectedFile = null;
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      this.fileError = 'Fichier trop volumineux (max 5MB).';
      this.selectedFile = null;
      return;
    }

    this.fileError = null;
    this.selectedFile = file;

    this.isExtracting = true;
    try {
      const extracted = await this.cvExtractorService.extractFromFile(file);
      this.extractedCvText = extracted.text || '';
      if (!this.extractedCvText.trim()) {
        this.extractionError = 'Le CV est lisible mais aucun texte exploitable n\'a été extrait.';
      } else {
        // Auto-save CV to user profile
        if (this.currentUser?.id) {
          this.authService.uploadCv(String(this.currentUser.id), file).subscribe({
            next: () => {
              this.notificationService.success('CV sauvegardé dans votre profil !');
            },
            error: (err) => {
              console.warn('Failed to auto-save CV to profile:', err);
            }
          });
        }
      }
    } catch (error) {
      const details = error instanceof Error ? error.message : String(error);
      this.extractionError = `Extraction CV impossible: ${details}`;
      this.extractedCvText = '';
    } finally {
      this.isExtracting = false;
    }
  }

  startTest(): void {
    if (this.profileForm.invalid) return;

    if (this.selectedFile && !this.extractedCvText.trim()) {
      this.fileError = 'Veuillez attendre la fin de l\'extraction ou choisir un autre CV.';
      return;
    }

    const githubUsername = this.normalizeGithubUsername(
      this.profileForm.value.githubUsername || ''
    );

    const profileData = {
      ...this.profileForm.value,
      githubUsername,
      userId: this.currentUser?.id,
      cvFile: this.selectedFile ? this.selectedFile.name : null,
      cvText: this.extractedCvText
    };
    sessionStorage.setItem('softSkillsProfile', JSON.stringify(profileData));

    this.router.navigate(['/evaluation/test'], {
      state: { profileData, cvFile: this.selectedFile }
    });
  }

  hasGithub(): boolean {
    return !!this.profileForm.get('githubUsername')?.value?.trim();
  }

  clearSelectedFile(event?: Event): void {
    if (event) event.stopPropagation();
    this.selectedFile = null;
    this.extractedCvText = '';
    this.extractionError = null;
    this.fileError = null;
  }

  private normalizeGithubUsername(input: string): string {
    const raw = (input || '').trim();
    if (!raw) return '';

    const cleaned = raw.replace(/^(https?:\/\/)?(www\.)?github\.com\//i, '');
    return cleaned.split('/')[0].replace(/^@/, '').trim();
  }

  personalityTypes = [
    {
      name: 'Empathique',
      icon: '❤️',
      color: '#ec4899',
      description: 'Chaleureux, sensible et compatissant. Excelle dans la communication émotionnelle.',
      traits: ['Écoute', 'Compassion', 'Harmonie']
    },
    {
      name: 'Travaillomane',
      icon: '🎯',
      color: '#3b82f6',
      description: 'Logique, organisé et responsable. Se distingue par sa rigueur et sa fiabilité.',
      traits: ['Organisation', 'Logique', 'Fiabilité']
    },
    {
      name: 'Persévérant',
      icon: '🛡️',
      color: '#22c55e',
      description: 'Engagé, observateur et dévoué. Guidé par des valeurs fortes et un sens du devoir.',
      traits: ['Valeurs', 'Engagement', 'Observation']
    },
    {
      name: 'Promoteur',
      icon: '⚡',
      color: '#f59e0b',
      description: 'Charismatique, adaptable et orienté action. Excelle dans le leadership.',
      traits: ['Leadership', 'Action', 'Charisme']
    },
    {
      name: 'Rebelle',
      icon: '🎨',
      color: '#8b5cf6',
      description: 'Créatif, spontané et ludique. Apporte énergie et originalité.',
      traits: ['Créativité', 'Spontanéité', 'Énergie']
    },
    {
      name: 'Rêveur',
      icon: '🌙',
      color: '#06b6d4',
      description: 'Calme, imaginatif et introspectif. Fort en réflexion profonde.',
      traits: ['Imagination', 'Calme', 'Réflexion']
    }
  ];
}
