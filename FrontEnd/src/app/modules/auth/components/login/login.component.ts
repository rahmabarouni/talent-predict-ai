import { Component, inject, OnInit, ApplicationRef } from '@angular/core';

import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterModule, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { HttpErrorResponse } from '@angular/common/http';
import { environment } from '../../../../../environments/environment';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent implements OnInit {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private notificationService = inject(NotificationService);
  private appRef = inject(ApplicationRef);

  loginForm: FormGroup = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],

  });

  loading = false;


  ngOnInit(): void {
    // If already authenticated, redirect to appropriate dashboard
    if (this.authService.isAuthenticated()) {
      const url = this.authService.getRedirectUrl();
      this.router.navigateByUrl(url).then(() => this.appRef.tick());
      return;
    }

    // Show session expired message if redirected from interceptor
    const reason = this.route.snapshot.queryParams['reason'];
    if (reason === 'session_expired') {
      this.notificationService.warning('Votre session a expiré. Veuillez vous reconnecter.');
    }
  }

  onSubmit(): void {
    if (!this.loginForm.valid) {
      return;
    }



    if (this.loginForm.valid) {
      this.loading = true;
      this.authService.login(this.loginForm.value).subscribe({
        next: (response) => {

          this.notificationService.success('Connexion réussie !');
          // TASK 1: Role-based redirect using backend's redirectUrl
          const redirectUrl = response.redirectUrl || this.authService.getRedirectUrl();
          this.router.navigateByUrl(redirectUrl).then(() => this.appRef.tick());
        },
        error: (error: HttpErrorResponse) => {
          this.loading = false;
          const message = error.error?.message || error.error?.error || '';
          const normalizedMessage = String(message).toLowerCase();



          if (error.status === 403 && normalizedMessage.includes('verify your email')) {
            this.notificationService.warning('Veuillez vérifier votre e-mail avant de vous connecter.');
            this.router.navigate(['/auth/verify-email'], {
              queryParams: {
                email: this.loginForm.get('email')?.value || ''
              }
            }).then(() => this.appRef.tick());
            return;
          }

          if (error.status === 401) {
            this.notificationService.error('Email ou mot de passe incorrect.');
          } else if (error.status === 0) {
            this.notificationService.error('Impossible de contacter le serveur.');
          } else {
            this.notificationService.error(
              message || 'Une erreur est survenue. Veuillez réessayer.'
            );
          }
        },
        complete: () => {
          this.loading = false;
        }
      });
    }
  }

  showPassword = false;
}
