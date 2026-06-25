import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./modules/home/home.component').then(m => m.HomeComponent),
    pathMatch: 'full'
  },
  {
    path: 'auth',
    children: [
      { path: 'login', loadComponent: () => import('./modules/auth/components/login/login.component').then(m => m.LoginComponent) },
      { path: 'register', loadComponent: () => import('./modules/auth/components/register/register.component').then(m => m.RegisterComponent) },
      { path: 'forgot-password', loadComponent: () => import('./modules/auth/components/forgot-password/forgot-password.component').then(m => m.ForgotPasswordComponent) },
      { path: 'reset-password', loadComponent: () => import('./modules/auth/components/reset-password/reset-password.component').then(m => m.ResetPasswordComponent) },
      { path: 'verify-email', loadComponent: () => import('./modules/auth/components/verify-email/verify-email.component').then(m => m.VerifyEmailComponent) },
    ]
  },
  { path: 'dashboard', canActivate: [authGuard], loadComponent: () => import('./modules/dashboard/components/user-dashboard/user-dashboard.component').then(m => m.UserDashboardComponent) },
  { path: 'profile', canActivate: [authGuard], loadComponent: () => import('./modules/dashboard/components/user-profile/user-profile.component').then(m => m.UserProfileComponent) },
  {
    path: 'security',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./modules/dashboard/components/security-privacy-dashboard/security-privacy-dashboard.component').then(
        m => m.SecurityPrivacyDashboardComponent
      )
  },
  {
    path: 'admin',
    canActivate: [authGuard, roleGuard(['ADMIN'])],
    children: [
      { path: 'dashboard', loadComponent: () => import('./modules/admin/components/executive-dashboard/executive-dashboard.component').then(m => m.ExecutiveDashboardComponent) },
      { path: 'campaigns', loadComponent: () => import('./modules/admin/components/campaign-manager/campaign-manager.component').then(m => m.CampaignManagerComponent) },
      { path: 'profile', loadComponent: () => import('./modules/dashboard/components/admin-profile/admin-profile.component').then(m => m.AdminProfileComponent) },
      { path: 'users', loadComponent: () => import('./modules/admin/components/user-management/user-management.component').then(m => m.UserManagementComponent) },
      { path: 'formations', loadComponent: () => import('./modules/admin/components/admin-formation-approval/admin-formation-approval').then(m => m.AdminFormationApproval) }
    ]
  },
  {
    path: 'evaluation',
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'intro', pathMatch: 'full' },
      { path: 'intro', loadComponent: () => import('./modules/evaluation/components/pcm-intro/pcm-intro.component').then(m => m.PcmIntroComponent) },
      { path: 'test', loadComponent: () => import('./modules/evaluation/components/pcm-test/pcm-test.component').then(m => m.PcmTestComponent) },
      { path: 'results', loadComponent: () => import('./modules/evaluation/components/test-results/test-results.component').then(m => m.TestResultsComponent) },
      { path: 'results/:id', loadComponent: () => import('./modules/evaluation/components/test-results/test-results.component').then(m => m.TestResultsComponent) },

      {
        path: 'scenario',
        loadComponent: () =>
          import('./modules/skill-test/components/scenario-simulator/scenario-simulator.component').then(
            m => m.ScenarioSimulatorComponent
          )
      }
    ]
  },
  {
    path: 'competences',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./modules/competences/components/competences-intake/competences-intake.component').then(
            m => m.CompetencesIntakeComponent
          )
      },
      {
        path: 'test',
        loadComponent: () =>
          import('./modules/skill-test/components/skill-test-quiz/skill-test-quiz.component').then(
            m => m.SkillTestQuizComponent
          )
      },

      {
        path: 'code',
        loadComponent: () =>
          import('./modules/skill-test/components/skill-code-challenge/skill-code-challenge.component').then(
            m => m.SkillCodeChallengeComponent
          )
      },
      {
        path: 'results',
        loadComponent: () =>
          import('./modules/competences/components/tech-results/tech-results.component').then(
            m => m.TechResultsComponent
          )
      }
    ]
  },
  {
    path: 'mes-resultats',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./modules/resultats/components/mes-resultats/mes-resultats.component').then(
            m => m.MesResultatsComponent
          )
      },
      {
        path: 'progress',
        loadComponent: () =>
          import('./modules/skill-test/components/skill-progress/skill-progress.component').then(
            m => m.SkillProgressComponent
          )
      }
    ]
  },
  { path: 'formations', canActivate: [authGuard], loadComponent: () => import('./modules/formation/components/formation-list/formation-list.component').then(m => m.FormationListComponent) },
  {
    path: 'skill-test',
    canActivate: [authGuard],
    children: [
    ]
  },
  { path: '**', redirectTo: '/' }
];
