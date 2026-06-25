import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from '../../modules/auth/services/auth.service';
import { NotificationService } from '../services/notification.service';

/**
 * Factory that returns a CanActivateFn restricting access to specific roles.
 * Returns UrlTree redirect instead of imperative navigate (required for zoneless).
 */
export const roleGuard = (allowedRoles: string[]): CanActivateFn => {
  return (route, state) => {
    const authService = inject(AuthService);
    const router = inject(Router);
    const notificationService = inject(NotificationService);

    const currentUser = authService.getCurrentUser();
    
    if (currentUser && allowedRoles.includes(currentUser.role)) {
      return true;
    }

    notificationService.warning('Accès réservé.');
    return router.createUrlTree([authService.getRedirectUrl()]);
  };
};
