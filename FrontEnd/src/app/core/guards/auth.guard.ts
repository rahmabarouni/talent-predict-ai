import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from '../../modules/auth/services/auth.service';

/**
 * Protects routes from unauthenticated access.
 * Returns UrlTree redirect instead of imperative navigate (required for zoneless).
 */
export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  // Clear any stale session data
  authService.clearSession();

  // Return UrlTree so the router handles the redirect properly
  return router.createUrlTree(['/auth/login'], {
    queryParams: { returnUrl: state.url }
  });
};
