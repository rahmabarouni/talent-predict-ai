import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject, ApplicationRef } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError, switchMap } from 'rxjs';
import { AuthService } from '../../modules/auth/services/auth.service';
import { NotificationService } from '../services/notification.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const notificationService = inject(NotificationService);
  const appRef = inject(ApplicationRef);
  const token = authService.getToken();

  // Always send credentials (cookies) for cross-site refresh/clear
  const withCreds = { withCredentials: true } as const;

  if (token) {
    req = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      },
      ...withCreds
    });
  } else {
    req = req.clone({ ...withCreds });
  }

  // These endpoints must NEVER trigger the 401 retry/refresh logic
  // to avoid infinite loops
  const isAuthEndpoint =
    req.url.includes('/api/auth/login') ||
    req.url.includes('/api/auth/register') ||
    req.url.includes('/api/auth/oauth/') ||
    req.url.includes('/api/auth/verify-email') ||
    req.url.includes('/api/auth/resend-verification') ||
    req.url.includes('/api/auth/refresh-token') ||
    req.url.includes('/api/auth/logout') ||
    req.url.includes('/api/auth/forgot-password') ||
    req.url.includes('/api/auth/reset-password') ||
    req.url.includes('/api/public/');
  const isDirectAiEndpoint =
    req.url.includes('/analyze-candidate') ||
    req.url.includes('localhost:8000');
  const isCandidateReportEndpoint =
    req.url.includes('/api/candidates/') &&
    (req.url.includes('/generate-report') || req.url.includes('/progress'));

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // Skip global handling for auth/public endpoints
      if (isAuthEndpoint) {
        return throwError(() => error);
      }

      switch (error.status) {
        case 401:
          // Token is expired — attempt a single refresh
          if (authService.getToken() && !authService.isRefreshInProgress()) {
            return authService.refreshAccessToken().pipe(
              switchMap(response => {
                // Retry original request with new token
                const newReq = req.clone({
                  setHeaders: {
                    Authorization: `Bearer ${response.accessToken}`
                  }
                });
                return next(newReq);
              }),
              catchError(refreshError => {
                // Refresh failed — clear session locally and redirect
                // Do NOT call logout() here (would loop through interceptor)
                authService.clearSession();
                notificationService.error('Session expirée. Veuillez vous reconnecter.');
                router.navigateByUrl('/auth/login').then(() => appRef.tick());
                return throwError(() => refreshError);
              })
            );
          } else {
            // No token or refresh already in progress — clear and redirect
            authService.clearSession();
            notificationService.error('Session expirée. Veuillez vous reconnecter.');
            router.navigateByUrl('/auth/login').then(() => appRef.tick());
            return throwError(() => error);
          }

        case 403:
          notificationService.error('Accès refusé. Vous n\'avez pas les permissions nécessaires.');
          router.navigateByUrl('/dashboard').then(() => appRef.tick());
          break;

        case 404:
          // Don't show global error for 404 — let components handle it
          break;

        case 0:
          // Let feature-level handlers manage direct AI service outages.
          if (!isDirectAiEndpoint) {
            notificationService.error('Impossible de contacter le serveur. Vérifiez votre connexion.');
          }
          break;

        case 500:
        case 502:
        case 503:
          if (!isCandidateReportEndpoint) {
            notificationService.error('Erreur serveur. Veuillez réessayer plus tard.');
          }
          break;
      }

      return throwError(() => error);
    })
  );
};
