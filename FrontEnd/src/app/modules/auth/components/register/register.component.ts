import { Component, inject, ApplicationRef } from '@angular/core';

import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../../../core/services/notification.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterModule],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss'
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private notificationService = inject(NotificationService);
  private appRef = inject(ApplicationRef);

  /** Password policy must match backend: 8+ chars with uppercase, lowercase, digit, special char */
  private passwordPattern = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/;

  registerForm: FormGroup = this.fb.group({
    nom: ['', [Validators.required]],
    prenom: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    phoneNumber: ['', [Validators.pattern(/^\+?[0-9]{7,15}$/)]],
    password: ['', [
      Validators.required,
      Validators.minLength(8),
      Validators.pattern(this.passwordPattern)
    ]]
  });

  loading = false;

  showPassword = false;

  hasUpperCase(): boolean { return /[A-Z]/.test(this.registerForm.get('password')?.value || ''); }
  hasLowerCase(): boolean { return /[a-z]/.test(this.registerForm.get('password')?.value || ''); }
  hasDigit(): boolean { return /\d/.test(this.registerForm.get('password')?.value || ''); }
  hasSpecial(): boolean { return /[^A-Za-z0-9]/.test(this.registerForm.get('password')?.value || ''); }

  onSubmit(): void {
    if (this.registerForm.valid) {
      this.loading = true;
      const formValue = {
        ...this.registerForm.value,
        role: 'USER'
      };
      this.authService.register(formValue).subscribe({
        next: (response) => {
          const email = this.registerForm.get('email')?.value || '';
          const needsVerification = response?.emailVerified === false || !response?.token;
          if (needsVerification) {
            this.notificationService.success('Compte créé. Vérifiez votre e-mail pour activer votre accès.');
            this.router.navigate(['/auth/verify-email'], {
              queryParams: { email }
            }).then(() => this.appRef.tick());
            return;
          }

          this.notificationService.success('Bienvenue ! Votre compte a été créé avec succès.');
          // Redirect using backend's URL or default to dashboard
          const redirectUrl = response.redirectUrl || '/dashboard';
          this.router.navigateByUrl(redirectUrl).then(() => this.appRef.tick());
        },
        error: (error) => {
          if (error.status === 409) {
            const msg = error.error?.message || 'Cet email ou numéro de téléphone est déjà utilisé.';
            this.notificationService.error(msg);
            return;
          } else if (error.status === 400) {
            const msg = error.error?.message || error.error?.error || 'Données invalides. Vérifiez le formulaire.';
            this.notificationService.error(msg);
          } else {
            const msg = error?.error?.message || 'Erreur lors de l\'inscription. Réessayez.';
            this.notificationService.error(msg);
          }
          this.loading = false;
        },
        complete: () => {
          this.loading = false;
        }
      });
    }
  }


}
