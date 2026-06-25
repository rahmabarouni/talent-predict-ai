import { Component, OnInit, inject } from '@angular/core';

import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../../../core/services/notification.service';

@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [ReactiveFormsModule, RouterModule],
  templateUrl: './verify-email.component.html',
  styleUrl: './verify-email.component.scss'
})
export class VerifyEmailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private notificationService = inject(NotificationService);

  resendForm: FormGroup = this.fb.group({
    email: ['', [Validators.required, Validators.email]]
  });

  loading = false;
  resending = false;
  verificationSuccess = false;
  verificationError = false;
  message = '';

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      const token = params.get('token');
      const email = params.get('email') || '';
      const sent = params.get('sent') === '1';

      if (email) {
        this.resendForm.patchValue({ email });
      }

      if (token) {
        this.verifyToken(token);
        return;
      }

      if (sent) {
        this.verificationSuccess = false;
        this.verificationError = false;
        this.message = 'Un e-mail de vérification a été envoyé. Ouvrez votre boîte de réception et cliquez sur le lien.';
      }
    });
  }

  onResendVerification(): void {
    if (this.resendForm.invalid) {
      this.resendForm.markAllAsTouched();
      return;
    }

    this.resending = true;
    const email = this.resendForm.get('email')?.value;
    this.authService.resendVerificationEmail(email).subscribe({
      next: (response) => {
        this.message = response.message || 'Lien de vérification renvoyé.';
        this.verificationError = false;
        this.verificationSuccess = false;
        this.notificationService.success('E-mail de vérification renvoyé.');
      },
      error: (error) => {
        this.verificationError = true;
        this.verificationSuccess = false;
        this.message = error.error?.message || 'Impossible de renvoyer le lien de vérification.';
      },
      complete: () => {
        this.resending = false;
      }
    });
  }

  private verifyToken(token: string): void {
    this.loading = true;
    this.authService.verifyEmail(token).subscribe({
      next: (response) => {
        this.verificationSuccess = true;
        this.verificationError = false;
        this.message = response.message || 'Adresse e-mail vérifiée avec succès. Vous pouvez vous connecter.';
      },
      error: (error) => {
        this.verificationSuccess = false;
        this.verificationError = true;
        this.message = error.error?.message || 'Lien de vérification invalide ou expiré.';
      },
      complete: () => {
        this.loading = false;
      }
    });
  }
}
