import { Component, OnInit, inject, signal } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

/**
 * TASK 3 — Reset Password Component
 * Route: /auth/reset-password?token=... — PUBLIC (no guard)
 */
@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.scss'
})
export class ResetPasswordComponent implements OnInit {
  private auth = inject(AuthService);
    private router = inject(Router);
    private route = inject(ActivatedRoute);

    token: string | null = null;
    newPassword = '';
    confirmPassword = '';
    showPwd = false;
    mismatch = false;

    loading = signal(false);
    success = signal(false);
    error = signal<string | null>(null);

    ngOnInit(): void {
        this.token = this.route.snapshot.queryParams['token'] || null;
    }

    onSubmit(): void {
        this.mismatch = false;
        this.error.set(null);

        if (this.newPassword !== this.confirmPassword) {
            this.mismatch = true;
            return;
        }
        if (this.newPassword.length < 6) {
            this.error.set('Le mot de passe doit comporter au moins 6 caractères.');
            return;
        }

        this.loading.set(true);
        this.auth.resetPassword(this.token!, this.newPassword).subscribe({
            next: () => {
                this.loading.set(false);
                this.success.set(true);
                // Auto-redirect to login after 2 seconds
                setTimeout(() => this.router.navigateByUrl('/auth/login'), 2000);
            },
            error: (err) => {
                this.loading.set(false);
                this.error.set(err?.error?.message || 'Lien invalide ou expiré. Veuillez refaire une demande.');
            }
        });
    }
}
