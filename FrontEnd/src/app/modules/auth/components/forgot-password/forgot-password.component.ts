import { Component, inject, signal } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

/**
 * TASK 3 — Forgot Password Component
 * Route: /auth/forgot-password — PUBLIC (no guard)
 */
@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.scss'
})
export class ForgotPasswordComponent {
  private auth = inject(AuthService);

  channel: 'EMAIL' | 'SMS' = 'EMAIL';
  email = '';
  phoneNumber = '';
  loading = signal(false);
  sent = signal(false);
  error = signal<string | null>(null);
  message = signal('');

  onSubmit(): void {
    this.error.set(null);
    if (this.channel === 'EMAIL' && !this.email) return;
    if (this.channel === 'SMS' && !this.phoneNumber) return;

    this.loading.set(true);
    const request$ = this.channel === 'SMS'
      ? this.auth.requestPasswordResetSms(this.phoneNumber)
      : this.auth.requestPasswordResetEmail(this.email);

    request$.subscribe({
      next: (res) => {
        this.loading.set(false);
        this.sent.set(true);
        this.message.set(res.message || 'Si votre compte est enregistré, vous recevrez un lien.');
      },
      error: () => {
        this.loading.set(false);
        this.sent.set(true);
        this.message.set('Si votre compte est enregistré, vous recevrez un lien de réinitialisation.');
      }
    });
  }
}
