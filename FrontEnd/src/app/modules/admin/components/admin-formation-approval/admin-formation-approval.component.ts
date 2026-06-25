import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FormationService } from '../../../formation/services/formation.service';
import { FormationResponse, StatutFormation, TypeFormation } from '../../../formation/models/formation.model';

type TabKey = 'pending' | 'in_progress' | 'history';

@Component({
  selector: 'app-admin-formation-approval',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-formation-approval.html',
  styleUrl: './admin-formation-approval.scss',
})
export class AdminFormationApproval implements OnInit {
  private formationService = inject(FormationService);

  formations = signal<FormationResponse[]>([]);
  isLoading = signal(false);
  errorMsg = signal('');

  // Panel state
  selectedFormationId = signal<string | null>(null);
  rejectionReason = signal('');
  adminNote = signal('');

  // UI controls
  activeTab = signal<TabKey>('pending');
  searchQuery = signal('');
  typeFilter = signal<string>('ALL');
  sortField = signal<'titre' | 'candidatName' | 'requestedAt' | 'duree'>('requestedAt');
  sortDir = signal<'asc' | 'desc'>('desc');

  readonly tabs: { key: TabKey; label: string; icon: string }[] = [
    { key: 'pending',     label: 'En attente',  icon: '⏳' },
    { key: 'in_progress', label: 'En cours',    icon: '🔄' },
    { key: 'history',     label: 'Historique',  icon: '📋' },
  ];

  readonly typeOptions = [
    { value: 'ALL',          label: 'Tous les types' },
    { value: TypeFormation.TECH_SKILL,     label: 'Tech Skill' },
    { value: TypeFormation.SOFT_SKILL,     label: 'Soft Skill' },
    { value: TypeFormation.CERTIFICATION,  label: 'Certification' },
    { value: TypeFormation.WORKSHOP,       label: 'Workshop' },
    { value: TypeFormation.TECHNIQUE,      label: 'Technique (legacy)' },
    { value: TypeFormation.MANAGEMENT,     label: 'Management (legacy)' },
  ];

  // ── Stats ────────────────────────────────────────────────────────────────────
  stats = computed(() => {
    const all = this.formations();
    return {
      total:       all.length,
      pending:     all.filter(f => f.statut === StatutFormation.EN_ATTENTE || f.statut === StatutFormation.EN_ATTENTE_VALIDATION).length,
      inProgress:  all.filter(f => f.statut === StatutFormation.EN_COURS || f.statut === StatutFormation.ACCEPTEE).length,
      completed:   all.filter(f => f.statut === StatutFormation.TERMINEE).length,
      rejected:    all.filter(f => f.statut === StatutFormation.REJETEE).length,
    };
  });

  // ── Filtered lists ────────────────────────────────────────────────────────────
  private filtered = computed(() => {
    const q = this.searchQuery().toLowerCase();
    const type = this.typeFilter();
    return this.formations().filter(f => {
      const matchQ = !q
        || f.titre.toLowerCase().includes(q)
        || (f.candidatName ?? '').toLowerCase().includes(q)
        || (f.fournisseur ?? '').toLowerCase().includes(q);
      const matchType = type === 'ALL' || f.type === type;
      return matchQ && matchType;
    });
  });

  private sorted = computed(() => {
    const field = this.sortField();
    const dir   = this.sortDir();
    return [...this.filtered()].sort((a, b) => {
      let av: any = a[field] ?? '';
      let bv: any = b[field] ?? '';
      if (field === 'requestedAt') {
        av = a.requestedAt ? new Date(a.requestedAt).getTime() : (a.dateProposition ? new Date(a.dateProposition).getTime() : 0);
        bv = b.requestedAt ? new Date(b.requestedAt).getTime() : (b.dateProposition ? new Date(b.dateProposition).getTime() : 0);
      }
      if (typeof av === 'string') av = av.toLowerCase();
      if (typeof bv === 'string') bv = bv.toLowerCase();
      if (av < bv) return dir === 'asc' ? -1 : 1;
      if (av > bv) return dir === 'asc' ?  1 : -1;
      return 0;
    });
  });

  pendingFormations  = computed(() => this.sorted().filter(f =>
    f.statut === StatutFormation.EN_ATTENTE || f.statut === StatutFormation.EN_ATTENTE_VALIDATION));

  inProgressFormations = computed(() => this.sorted().filter(f =>
    f.statut === StatutFormation.EN_COURS || f.statut === StatutFormation.ACCEPTEE));

  historyFormations  = computed(() => this.sorted().filter(f =>
    f.statut === StatutFormation.TERMINEE   ||
    f.statut === StatutFormation.REJETEE    ||
    f.statut === StatutFormation.ANNULEE    ||
    f.statut === StatutFormation.PROPOSEE   ||
    f.statut === StatutFormation.PROPOSEE_ADMIN));

  activeList = computed(() => {
    switch (this.activeTab()) {
      case 'pending':     return this.pendingFormations();
      case 'in_progress': return this.inProgressFormations();
      case 'history':     return this.historyFormations();
    }
  });

  // ── Lifecycle ────────────────────────────────────────────────────────────────
  ngOnInit(): void {
    this.loadFormations();
  }

  loadFormations(): void {
    this.isLoading.set(true);
    this.errorMsg.set('');
    this.formationService.getAllFormations().subscribe({
      next: (res) => {
        this.formations.set(res);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error(err);
        this.errorMsg.set('Erreur lors du chargement des formations.');
        this.isLoading.set(false);
      }
    });
  }

  // ── Actions ──────────────────────────────────────────────────────────────────
  approveFormation(f: FormationResponse): void {
    this.isLoading.set(true);
    this.formationService.updateFormationStatus(f.id, StatutFormation.ACCEPTEE).subscribe({
      next: () => {
        const note = this.adminNote().trim();
        if (note) {
          this.formationService.updateFormationReviewNotes(f.id, { reviewNote: note }).subscribe(() => this.loadFormations());
        } else {
          this.loadFormations();
        }
        this.closePanel();
      },
      error: () => this.isLoading.set(false)
    });
  }

  rejectFormation(f: FormationResponse): void {
    this.isLoading.set(true);
    this.formationService.updateFormationStatus(f.id, StatutFormation.REJETEE).subscribe({
      next: () => {
        const reason = this.rejectionReason().trim();
        if (reason) {
          this.formationService.updateFormationReviewNotes(f.id, { reviewNote: 'REJETÉ: ' + reason }).subscribe(() => this.loadFormations());
        } else {
          this.loadFormations();
        }
        this.closePanel();
      },
      error: () => this.isLoading.set(false)
    });
  }

  togglePanel(id: string): void {
    if (this.selectedFormationId() === id) {
      this.closePanel();
    } else {
      this.selectedFormationId.set(id);
      this.rejectionReason.set('');
      this.adminNote.set('');
    }
  }

  closePanel(): void {
    this.selectedFormationId.set(null);
    this.rejectionReason.set('');
    this.adminNote.set('');
  }

  // ── Sorting ──────────────────────────────────────────────────────────────────
  setSort(field: 'titre' | 'candidatName' | 'requestedAt' | 'duree'): void {
    if (this.sortField() === field) {
      this.sortDir.set(this.sortDir() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortField.set(field as any);
      this.sortDir.set('asc');
    }
  }

  sortIcon(field: string): string {
    if (this.sortField() !== field) return '↕';
    return this.sortDir() === 'asc' ? '↑' : '↓';
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────
  tabCount(key: TabKey): number {
    switch (key) {
      case 'pending':     return this.pendingFormations().length;
      case 'in_progress': return this.inProgressFormations().length;
      case 'history':     return this.historyFormations().length;
    }
  }

  getStatutClass(statut: StatutFormation): string {
    const map: Record<StatutFormation, string> = {
      [StatutFormation.EN_ATTENTE]:            'badge-warning',
      [StatutFormation.EN_ATTENTE_VALIDATION]: 'badge-warning',
      [StatutFormation.ACCEPTEE]:              'badge-info',
      [StatutFormation.EN_COURS]:              'badge-info',
      [StatutFormation.TERMINEE]:              'badge-success',
      [StatutFormation.REJETEE]:               'badge-danger',
      [StatutFormation.ANNULEE]:               'badge-danger',
      [StatutFormation.PROPOSEE]:              'badge-primary',
      [StatutFormation.PROPOSEE_ADMIN]:        'badge-primary',
    };
    return map[statut] ?? 'badge-primary';
  }

  getStatutLabel(statut: StatutFormation): string {
    const labels: Record<StatutFormation, string> = {
      [StatutFormation.EN_ATTENTE]:            'En attente',
      [StatutFormation.EN_ATTENTE_VALIDATION]: 'Validation requise',
      [StatutFormation.ACCEPTEE]:              'Acceptée',
      [StatutFormation.EN_COURS]:              'En cours',
      [StatutFormation.TERMINEE]:              'Terminée',
      [StatutFormation.REJETEE]:               'Rejetée',
      [StatutFormation.ANNULEE]:               'Annulée',
      [StatutFormation.PROPOSEE]:              'Proposée',
      [StatutFormation.PROPOSEE_ADMIN]:        'Proposée (Admin)',
    };
    return labels[statut] ?? statut;
  }

  getTypeLabel(type: TypeFormation): string {
    const labels: Record<TypeFormation, string> = {
      [TypeFormation.TECH_SKILL]:    '💻 Tech Skill',
      [TypeFormation.SOFT_SKILL]:    '🤝 Soft Skill',
      [TypeFormation.SOFT_SKILLS]:   '🤝 Soft Skills',
      [TypeFormation.CERTIFICATION]: '🏆 Certification',
      [TypeFormation.WORKSHOP]:      '🔧 Workshop',
      [TypeFormation.TECHNIQUE]:     '⚙️ Technique',
      [TypeFormation.MANAGEMENT]:    '📊 Management',
      [TypeFormation.LANGUES]:       '🌐 Langues',
    };
    return labels[type] ?? type;
  }

  getProgressColor(progression: number): string {
    if (progression >= 80) return '#22c55e';
    if (progression >= 50) return '#3b82f6';
    if (progression >= 20) return '#f59e0b';
    return '#94a3b8';
  }

  getFormationDate(f: FormationResponse): Date | null {
    return f.requestedAt ? new Date(f.requestedAt) : (f.dateProposition ? new Date(f.dateProposition) : null);
  }

  setAdminNote(v: string): void { this.adminNote.set(v); }
  setRejectionReason(v: string): void { this.rejectionReason.set(v); }
  setSearch(v: string): void { this.searchQuery.set(v); }
  setTypeFilter(v: string): void { this.typeFilter.set(v); }
}
