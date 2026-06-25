import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { AdminService, UserSummary } from '../../services/admin.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { User, Role, UserRequest } from '../../../auth/models/user.model';
import { SkillsService } from '../../../skills/services/skills.service';
import { SkillResponse } from '../../../skills/models/skill.model';
import { AuthService } from '../../../auth/services/auth.service';
import { RouterModule } from '@angular/router';
import { FormationService } from '../../../formation/services/formation.service';
import { FormationResponse, StatutFormation } from '../../../formation/models/formation.model';

type DrawerTab = 'profil' | 'resultats' | 'formations' | 'prediction' | 'activite';
type SortField = 'name' | 'department' | 'role' | 'formations' | 'predictions' | 'createdAt';
type SortDir = 'asc' | 'desc';

const PAGE_SIZE = 15;

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './user-management.component.html',
  styleUrl: './user-management.component.scss'
})
export class UserManagementComponent implements OnInit {
  private adminService   = inject(AdminService);
  private notifService   = inject(NotificationService);
  private skillsService  = inject(SkillsService);
  private authService    = inject(AuthService);
  private formationSvc   = inject(FormationService);
  private fb             = inject(FormBuilder);

  // ── Data ──────────────────────────────────────────────────────────────────
  users        = signal<User[]>([]);
  summaries    = signal<Map<string, UserSummary>>(new Map());
  loading      = signal(false);
  error        = signal<string | null>(null);

  // ── Drawer data ───────────────────────────────────────────────────────────
  userSkills        = signal<SkillResponse[]>([]);
  skillsLoading     = signal(false);
  skillsError       = signal<string | null>(null);
  validatingSkillIds= signal<Set<string>>(new Set());

  userFormations    = signal<FormationResponse[]>([]);
  formationsLoading = signal(false);
  formationsError   = signal<string | null>(null);

  drawerSummary    = signal<UserSummary | null>(null);
  summaryLoading   = signal(false);

  // ── Drawer / Modal state ──────────────────────────────────────────────────
  showRoleConfirm       = signal(false);
  pendingRoleChange     = signal<{ userId: string; newRole: string } | null>(null);
  showDeleteConfirm     = signal(false);
  selectedUserForDelete = signal<User | null>(null);
  isDrawerOpen          = signal(false);
  selectedUserForDrawer = signal<User | null>(null);
  activeDrawerTab       = signal<DrawerTab>('profil');
  showUserModal         = signal(false);
  isEditing             = signal(false);
  submitting            = signal(false);

  // ── Filters / Search ──────────────────────────────────────────────────────
  searchTerm       = signal('');
  filterStatut     = signal('');
  filterRole       = signal('');
  filterDepartement= signal('');

  // ── Sorting ───────────────────────────────────────────────────────────────
  sortField  = signal<SortField>('name');
  sortDir    = signal<SortDir>('asc');

  // ── Pagination ────────────────────────────────────────────────────────────
  currentPage  = signal(0);
  readonly pageSize = PAGE_SIZE;

  // ── Bulk selection ────────────────────────────────────────────────────────
  selectedUserIds = signal<Set<string>>(new Set());

  // ── Reactive Forms ────────────────────────────────────────────────────────
  userForm!: FormGroup;

  // ── Expose enums to template ──────────────────────────────────────────────
  readonly Role = Role;
  readonly StatutFormation = StatutFormation;

  // ── Computed ──────────────────────────────────────────────────────────────
  stats = computed(() => {
    const all = this.users();
    const sums = this.summaries();
    return {
      total:          all.length,
      actifs:         all.filter(u => u.isActive).length,
      inactifs:       all.filter(u => !u.isActive).length,
      avecPrediction: [...sums.values()].filter(s => (s.predictionsCount ?? 0) > 0).length,
      avecFormation:  [...sums.values()].filter(s => (s.formationsTotal ?? 0) > 0).length,
      sansFormation:  all.length - [...sums.values()].filter(s => (s.formationsTotal ?? 0) > 0).length
    };
  });

  filteredSortedUsers = computed(() => {
    let list = this.users();
    const term = this.searchTerm().toLowerCase();

    if (term) {
      list = list.filter(u =>
        u.firstName?.toLowerCase().includes(term) ||
        u.lastName?.toLowerCase().includes(term)  ||
        u.email?.toLowerCase().includes(term)     ||
        u.department?.toLowerCase().includes(term) ||
        u.position?.toLowerCase().includes(term)
      );
    }
    if (this.filterStatut()) {
      const active = this.filterStatut() === 'actif';
      list = list.filter(u => !!u.isActive === active);
    }
    if (this.filterRole()) {
      list = list.filter(u => u.role === this.filterRole());
    }
    if (this.filterDepartement()) {
      list = list.filter(u => u.department === this.filterDepartement());
    }

    // Sorting
    const field = this.sortField();
    const dir   = this.sortDir() === 'asc' ? 1 : -1;
    const sums  = this.summaries();

    list = [...list].sort((a, b) => {
      let va: any, vb: any;
      switch (field) {
        case 'name':        va = `${a.firstName} ${a.lastName}`; vb = `${b.firstName} ${b.lastName}`; break;
        case 'department':  va = a.department ?? ''; vb = b.department ?? ''; break;
        case 'role':        va = a.role; vb = b.role; break;
        case 'formations':  va = sums.get(a.id)?.formationsTotal ?? 0; vb = sums.get(b.id)?.formationsTotal ?? 0; break;
        case 'predictions': va = sums.get(a.id)?.predictionsCount ?? 0; vb = sums.get(b.id)?.predictionsCount ?? 0; break;
        case 'createdAt':   va = a.createdAt ?? ''; vb = b.createdAt ?? ''; break;
        default:            return 0;
      }
      if (va < vb) return -dir;
      if (va > vb) return  dir;
      return 0;
    });

    return list;
  });

  paginatedUsers = computed(() =>
    this.filteredSortedUsers().slice(
      this.currentPage() * this.pageSize,
      (this.currentPage() + 1) * this.pageSize
    )
  );

  totalPages = computed(() =>
    Math.max(1, Math.ceil(this.filteredSortedUsers().length / this.pageSize))
  );

  pageNumbers = computed(() =>
    Array.from({ length: this.totalPages() }, (_, i) => i)
  );

  departments = computed(() =>
    [...new Set(this.users().map(u => u.department).filter(Boolean))] as string[]
  );

  drawerFormations = computed(() => {
    const all = this.userFormations();
    return {
      total:   all.length,
      enCours: all.filter(f => f.statut === StatutFormation.EN_COURS).length,
      terminees: all.filter(f => f.statut === StatutFormation.TERMINEE).length,
      attente: all.filter(f => f.statut === StatutFormation.EN_ATTENTE || f.statut === StatutFormation.EN_ATTENTE_VALIDATION).length,
    };
  });

  ngOnInit(): void {
    this.buildForm();
    this.loadUsers();
  }

  // ── Form ──────────────────────────────────────────────────────────────────
  private buildForm(user?: User): void {
    this.userForm = this.fb.group({
      firstName:  [user?.firstName  ?? '', [Validators.required, Validators.minLength(2)]],
      lastName:   [user?.lastName   ?? '', [Validators.required, Validators.minLength(2)]],
      email:      [user?.email      ?? '', [Validators.required, Validators.email]],
      password:   ['', this.isEditing() ? [] : [Validators.required, Validators.minLength(8)]],
      department: [user?.department ?? ''],
      position:   [user?.position   ?? ''],
      role:       [user?.role       ?? Role.USER, Validators.required],
      isActive:   [user?.isActive   ?? true]
    });
  }

  fieldError(name: string): string | null {
    const c = this.userForm.get(name);
    if (!c || !c.invalid || !c.touched) return null;
    if (c.errors?.['required'])  return 'Ce champ est requis.';
    if (c.errors?.['email'])     return 'Format d\'email invalide.';
    if (c.errors?.['minlength']) return `Minimum ${c.errors['minlength'].requiredLength} caractères.`;
    return 'Valeur invalide.';
  }

  // ── Data loading ──────────────────────────────────────────────────────────
  isAdmin(): boolean { return this.authService.isAdmin(); }

  loadUsers(): void {
    this.loading.set(true);
    this.error.set(null);
    this.adminService.getAllUsers().subscribe({
      next: (data) => {
        this.users.set(data);
        this.loading.set(false);
        // Load summaries in parallel (one request per user, batched quietly)
        this.loadAllSummaries(data.map(u => u.id));
      },
      error: () => {
        this.error.set('Erreur lors du chargement des utilisateurs');
        this.loading.set(false);
      }
    });
  }

  private loadAllSummaries(ids: string[]): void {
    ids.forEach(id => {
      this.adminService.getUserSummary(id).subscribe({
        next: (s) => {
          this.summaries.update(map => {
            const next = new Map(map);
            next.set(id, s);
            return next;
          });
        },
        error: () => { /* silently ignore per-user summary errors */ }
      });
    });
  }

  private loadUserSkills(userId: string): void {
    this.skillsLoading.set(true);
    this.skillsError.set(null);
    this.skillsService.getUserSkills(userId)
      .pipe(finalize(() => this.skillsLoading.set(false)))
      .subscribe({
        next: (s: SkillResponse[]) => this.userSkills.set(s),
        error: () => {
          this.userSkills.set([]);
          this.skillsError.set('Erreur lors du chargement des compétences');
        }
      });
  }

  private loadUserFormations(userId: string): void {
    this.formationsLoading.set(true);
    this.formationsError.set(null);
    this.formationSvc.getUserFormations(userId)
      .pipe(finalize(() => this.formationsLoading.set(false)))
      .subscribe({
        next: (f) => this.userFormations.set(f),
        error: () => {
          this.userFormations.set([]);
          this.formationsError.set('Erreur lors du chargement des formations');
        }
      });
  }

  private loadDrawerSummary(userId: string): void {
    this.summaryLoading.set(true);
    this.adminService.getUserSummary(userId)
      .pipe(finalize(() => this.summaryLoading.set(false)))
      .subscribe({
        next: (s) => this.drawerSummary.set(s),
        error: () => this.drawerSummary.set(null)
      });
  }

  // ── Skills ────────────────────────────────────────────────────────────────
  validateSkill(skillId: string, event?: Event): void {
    if (event) event.stopPropagation();
    if (!this.isAdmin()) return;
    this.setSkillValidating(skillId, true);
    this.skillsService.validateSkill(skillId)
      .pipe(finalize(() => this.setSkillValidating(skillId, false)))
      .subscribe({
        next: (updated: SkillResponse) => {
          this.userSkills.update(list => list.map(s => s.id === updated.id ? updated : s));
          this.notifService.success('Compétence validée.');
        },
        error: () => this.notifService.error('Erreur lors de la validation.')
      });
  }

  isSkillValidating(skillId: string): boolean { return this.validatingSkillIds().has(skillId); }

  private setSkillValidating(skillId: string, validating: boolean): void {
    const next = new Set(this.validatingSkillIds());
    validating ? next.add(skillId) : next.delete(skillId);
    this.validatingSkillIds.set(next);
  }

  // ── Formatting ────────────────────────────────────────────────────────────
  formatScore(score: number | null | undefined): string {
    if (score == null) return '—';
    const pct = score <= 1 ? score * 100 : score;
    return `${Math.round(pct)}%`;
  }

  formatRelativeTime(dateStr: string | null | undefined): string {
    if (!dateStr) return 'Jamais';
    const diff = Math.floor((Date.now() - new Date(dateStr).getTime()) / 86_400_000);
    if (diff === 0) return 'Aujourd\'hui';
    if (diff === 1) return 'Hier';
    if (diff < 30)  return `Il y a ${diff} j`;
    if (diff < 365) return `Il y a ${Math.floor(diff / 30)} mois`;
    return `Il y a ${Math.floor(diff / 365)} an(s)`;
  }

  formatDate(date: string | Date | undefined | null): string {
    if (!date) return '—';
    return new Date(date).toLocaleDateString('fr-FR', { year: 'numeric', month: 'short', day: 'numeric' });
  }

  getStatutLabel(statut: StatutFormation): string {
    const labels: Record<string, string> = {
      EN_COURS: 'En cours', TERMINEE: 'Terminée', EN_ATTENTE: 'En attente',
      EN_ATTENTE_VALIDATION: 'À valider', ANNULEE: 'Annulée',
      PROPOSEE: 'Proposée', PROPOSEE_ADMIN: 'Proposée (Admin)',
      ACCEPTEE: 'Acceptée', REJETEE: 'Rejetée'
    };
    return labels[statut] ?? statut;
  }

  getStatutClass(statut: StatutFormation): string {
    const map: Record<string, string> = {
      EN_COURS: 'info', TERMINEE: 'success', EN_ATTENTE: 'warning',
      EN_ATTENTE_VALIDATION: 'warning', ANNULEE: 'danger',
      PROPOSEE: 'neutral', PROPOSEE_ADMIN: 'neutral', REJETEE: 'danger', ACCEPTEE: 'success'
    };
    return map[statut] ?? 'neutral';
  }

  getSummary(userId: string): UserSummary | null {
    return this.summaries().get(userId) ?? null;
  }

  // ── Sorting & Pagination ──────────────────────────────────────────────────
  toggleSort(field: SortField): void {
    if (this.sortField() === field) {
      this.sortDir.update(d => d === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortField.set(field);
      this.sortDir.set('asc');
    }
    this.currentPage.set(0);
  }

  sortIcon(field: SortField): string {
    if (this.sortField() !== field) return '↕';
    return this.sortDir() === 'asc' ? '↑' : '↓';
  }

  goToPage(page: number): void {
    const clamped = Math.max(0, Math.min(page, this.totalPages() - 1));
    this.currentPage.set(clamped);
  }

  onFilterChange(): void { this.currentPage.set(0); }

  // ── Role management ───────────────────────────────────────────────────────
  initiateRoleChange(userId: string, event: Event): void {
    const newRole = (event.target as HTMLSelectElement).value;
    const user = this.users().find(u => u.id === userId);
    if (!user || user.role === newRole) return;
    this.pendingRoleChange.set({ userId, newRole });
    this.showRoleConfirm.set(true);
    (event.target as HTMLSelectElement).value = user.role;
  }

  confirmRoleChange(): void {
    const pending = this.pendingRoleChange();
    if (!pending) return;
    this.adminService.updateUserRole(pending.userId, pending.newRole).subscribe({
      next: () => {
        this.users.update(all => all.map(u => u.id === pending.userId ? { ...u, role: pending.newRole as Role } : u));
        this.notifService.success('Rôle mis à jour.');
        this.cancelRoleChange();
      },
      error: () => {
        this.notifService.error('Erreur lors de la mise à jour du rôle.');
        this.cancelRoleChange();
      }
    });
  }

  cancelRoleChange(): void {
    this.pendingRoleChange.set(null);
    this.showRoleConfirm.set(false);
  }

  // ── Create / Edit ─────────────────────────────────────────────────────────
  openAddModal(): void {
    this.isEditing.set(false);
    this.selectedUserForDelete.set(null);
    this.buildForm();
    this.showUserModal.set(true);
  }

  openEditModal(user: User, event?: Event): void {
    if (event) event.stopPropagation();
    this.isEditing.set(true);
    this.selectedUserForDelete.set(user);
    this.buildForm(user);
    // Remove password validators for edit
    this.userForm.get('password')?.clearValidators();
    this.userForm.get('password')?.updateValueAndValidity();
    this.showUserModal.set(true);
  }

  closeUserModal(): void { this.showUserModal.set(false); }

  saveUser(): void {
    this.userForm.markAllAsTouched();
    if (this.userForm.invalid) return;

    const formVal = this.userForm.value;
    this.submitting.set(true);

    if (this.isEditing()) {
      const userId = this.selectedUserForDelete()?.id;
      if (!userId) return;
      const updateData: any = { ...formVal };
      if (!updateData.password) delete updateData.password;

      this.adminService.updateUser(userId, updateData).subscribe({
        next: (updated) => {
          this.users.update(all => all.map(u => u.id === userId ? { ...u, ...updated } : u));
          this.notifService.success('Utilisateur mis à jour.');
          this.closeUserModal();
          this.submitting.set(false);
        },
        error: () => {
          this.notifService.error('Erreur lors de la mise à jour.');
          this.submitting.set(false);
        }
      });
    } else {
      const username = formVal.email.split('@')[0] + Math.floor(Math.random() * 1000);
      this.adminService.createUser({ ...formVal, username } as UserRequest).subscribe({
        next: (newUser) => {
          this.users.update(all => [newUser, ...all]);
          this.notifService.success('Utilisateur créé.');
          this.closeUserModal();
          this.submitting.set(false);
        },
        error: () => {
          this.notifService.error('Erreur lors de la création.');
          this.submitting.set(false);
        }
      });
    }
  }

  // ── Delete ────────────────────────────────────────────────────────────────
  confirmDelete(user: User, event?: Event): void {
    if (event) event.stopPropagation();
    this.selectedUserForDelete.set(user);
    this.showDeleteConfirm.set(true);
  }

  cancelDelete(): void {
    this.selectedUserForDelete.set(null);
    this.showDeleteConfirm.set(false);
  }

  deleteUser(): void {
    const user = this.selectedUserForDelete();
    if (!user) return;
    this.adminService.deleteUser(user.id).subscribe({
      next: () => {
        this.users.update(all => all.filter(u => u.id !== user.id));
        this.cancelDelete();
        this.notifService.success('Utilisateur supprimé.');
      },
      error: () => {
        this.notifService.error('Erreur lors de la suppression.');
        this.cancelDelete();
      }
    });
  }

  // ── Drawer ────────────────────────────────────────────────────────────────
  openDrawer(user: User): void {
    this.selectedUserForDrawer.set(user);
    this.activeDrawerTab.set('profil');
    this.isDrawerOpen.set(true);
    this.userSkills.set([]);
    this.userFormations.set([]);
    this.drawerSummary.set(null);
    document.body.style.overflow = 'hidden';

    this.loadUserSkills(user.id);
    this.loadUserFormations(user.id);
    this.loadDrawerSummary(user.id);
  }

  closeDrawer(): void {
    this.isDrawerOpen.set(false);
    setTimeout(() => this.selectedUserForDrawer.set(null), 300);
    this.userSkills.set([]);
    this.userFormations.set([]);
    this.skillsError.set(null);
    document.body.style.overflow = '';
  }

  setDrawerTab(tab: DrawerTab): void { this.activeDrawerTab.set(tab); }

  // ── Bulk actions ──────────────────────────────────────────────────────────
  toggleAllSelection(event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.selectedUserIds.set(checked ? new Set(this.paginatedUsers().map(u => u.id)) : new Set());
  }

  toggleSelection(userId: string): void {
    const s = new Set(this.selectedUserIds());
    s.has(userId) ? s.delete(userId) : s.add(userId);
    this.selectedUserIds.set(s);
  }

  isAllSelected(): boolean {
    const pg = this.paginatedUsers();
    return pg.length > 0 && pg.every(u => this.selectedUserIds().has(u.id));
  }

  bulkAction(action: string): void {
    const count = this.selectedUserIds().size;
    this.notifService.success(`Action "${action}" exécutée sur ${count} utilisateurs.`);
    this.selectedUserIds.set(new Set());
  }

  exportData(): void {
    this.notifService.success('Export en cours de génération...');
  }

  filterFromStats(type: string): void {
    this.filterStatut.set('');
    this.filterRole.set('');
    this.filterDepartement.set('');
    if (type === 'actifs')   this.filterStatut.set('actif');
    if (type === 'inactifs') this.filterStatut.set('inactif');
    this.currentPage.set(0);
  }
}
