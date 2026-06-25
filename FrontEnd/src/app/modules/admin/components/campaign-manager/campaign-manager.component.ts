import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NotificationService } from '../../../../core/services/notification.service';
import { DashboardService, EmployeeSummary } from '../../../dashboard/services/dashboard.service';
import { CampaignApi, CampaignService, CampaignUpsertRequest } from '../../services/campaign.service';
import { NotificationCenterApiService } from '../../../../core/services/notification-center-api.service';
import { AdminService } from '../../services/admin.service';
import { User } from '../../../auth/models/user.model';
import { catchError, finalize, forkJoin, of } from 'rxjs';

export type CampaignChannel = 'SMS' | 'EMAIL' | 'WHATSAPP' | 'IN_APP' | 'BOTH';
export type CampaignStatus = 'BROUILLON' | 'PLANIFIÉ' | 'ENVOYÉ' | 'ÉCHOUÉ';
export type LogStatus = 'LIVRÉ' | 'ÉCHOUÉ' | 'EN ATTENTE';
export type CampaignTargetGroup =
  | 'ALL_EMPLOYEES'
  | 'ACTIVE_EMPLOYEES'
  | 'PENDING_ASSESSMENT'
  | 'TRAINING_IN_PROGRESS';

export interface TargetGroupOption {
  key: CampaignTargetGroup;
  label: string;
}

export interface MessageTemplate {
  id: string;
  name: string;
  channel: CampaignChannel;
  category: string;
  subject: string;
  body: string;
  variables: string[];
  createdAt: string;
}

export interface Campaign {
  id: string;
  name: string;
  templateId: string;
  templateName: string;
  channel: CampaignChannel;
  targetGroup: CampaignTargetGroup;
  recipientCount: number;
  status: CampaignStatus;
  scheduledAt: string;
  sentCount: number;
  failedCount: number;
  openRate?: number;
  clickRate?: number;
  isPaused?: boolean;
}

export interface DeliveryLog {
  id: string;
  campaignName: string;
  recipient: string;
  channel: CampaignChannel;
  status: LogStatus;
  sentAt: string;
  errorMessage?: string;
}

@Component({
  selector: 'app-campaign-manager',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './campaign-manager.component.html',
  styleUrl: './campaign-manager.component.scss'
})
export class CampaignManagerComponent implements OnInit {
  private notificationService = inject(NotificationService);
  private dashboardService = inject(DashboardService);
  private campaignService = inject(CampaignService);
  private notifApiService = inject(NotificationCenterApiService);
  private adminService = inject(AdminService);

  activeTab = signal<'templates' | 'campaigns' | 'logs' | 'direct'>('campaigns');
  loading = signal(false);
  loadError = signal<string | null>(null);
  lastSync = signal('--');

  // Filters for campaigns
  campaignFilter = signal<'ALL' | 'BROUILLON' | 'PLANIFIÉ' | 'ENVOYÉ' | 'ÉCHOUÉ'>('ALL');

  private sourceEmployees = signal<EmployeeSummary[]>([]);

  targetGroupOptions: TargetGroupOption[] = [
    { key: 'ALL_EMPLOYEES', label: 'Tous les employés' },
    { key: 'ACTIVE_EMPLOYEES', label: 'Employés actifs' },
    { key: 'PENDING_ASSESSMENT', label: 'Évaluation en attente' },
    { key: 'TRAINING_IN_PROGRESS', label: 'Formation en cours' }
  ];

  // ── Template Editor (Templates are still static for now as per audit focus on Campaigns)
  templates = signal<MessageTemplate[]>([
    {
      id: 't1',
      name: 'Invitation au test',
      channel: 'EMAIL',
      category: 'Test & Évaluation',
      subject: 'Votre test TalentPredict',
      body: 'Bonjour {{prenom}}, vous avez été invité à passer le test TalentPredict. Commencez ici: {{lien_test}}',
      variables: ['prenom', 'lien_test'],
      createdAt: '2026-03-15'
    },
    {
      id: 't2',
      name: 'Mise à jour Onboarding',
      channel: 'EMAIL',
      category: 'Onboarding',
      subject: 'Bienvenue chez TalentPredict',
      body: 'Cher(e) {{prenom}} {{nom}},\n\nVotre profil est prêt.\n\nCordialement,\nL\'équipe RH',
      variables: ['prenom', 'nom'],
      createdAt: '2026-03-20'
    },
    {
      id: 't3',
      name: 'Rappel de formation',
      channel: 'IN_APP',
      category: 'Formation',
      subject: 'Rappel: Formation en attente',
      body: 'Bonjour {{prenom}}, n\'oubliez pas de terminer votre formation {{formation}}.',
      variables: ['prenom', 'formation'],
      createdAt: '2026-04-01'
    }
  ]);

  selectedTemplate = signal<MessageTemplate | null>(null);
  isCreatingTemplate = signal(false);

  newTemplate: Partial<MessageTemplate> = {
    name: '',
    channel: 'EMAIL',
    category: 'Général',
    subject: '',
    body: '',
  };

  // ── Campaigns ─────────────────────────────────────────────────
  campaigns = signal<Campaign[]>([]);

  filteredCampaigns = computed(() => {
    const f = this.campaignFilter();
    return f === 'ALL' ? this.campaigns() : this.campaigns().filter(c => c.status === f);
  });

  totalRecipients = computed(() => this.campaigns().reduce((sum, c) => sum + (c.recipientCount || 0), 0));
  totalSent = computed(() => this.campaigns().reduce((sum, c) => sum + (c.sentCount || 0), 0));
  totalFailed = computed(() => this.campaigns().reduce((sum, c) => sum + (c.failedCount || 0), 0));
  scheduledCampaigns = computed(() => this.campaigns().filter(c => c.status === 'PLANIFIÉ').length);
  deliveryRate = computed(() => {
    const recipients = this.totalRecipients();
    if (recipients === 0) return 0;
    return Math.round((this.totalSent() / recipients) * 100);
  });

  isCreatingCampaign = signal(false);
  isEditingCampaign = signal(false);

  newCampaign: Partial<Campaign> = {
    name: '',
    templateId: '',
    channel: 'EMAIL',
    targetGroup: 'ALL_EMPLOYEES',
    scheduledAt: ''
  };

  // ── Delivery Logs ─────────────────────────────────────────────
  deliveryLogs = signal<DeliveryLog[]>([]);

  logSearch = signal('');

  filteredLogs = computed(() => {
    const q = this.logSearch().toLowerCase();
    return q
      ? this.deliveryLogs().filter(l =>
        l.recipient.toLowerCase().includes(q) ||
        l.campaignName.toLowerCase().includes(q)
      )
      : this.deliveryLogs();
  });


  // ── Direct Message ────────────────────────────────────────────
  allUsers = signal<User[]>([]);
  usersLoading = signal(false);
  directSearch = signal('');
  directSelectedUser = signal<User | null>(null);
  directSending = signal(false);
  directSentLog = signal<{ name: string; title: string; sentAt: string }[]>([]);

  directForm = {
    title: '',
    body: '',
    type: 'INFO' as 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR',
    emailAlert: false
  };

  filteredUsers = computed(() => {
    const q = this.directSearch().toLowerCase();
    return q
      ? this.allUsers().filter(u =>
          `${u.firstName} ${u.lastName}`.toLowerCase().includes(q) ||
          u.email.toLowerCase().includes(q) ||
          (u.department ?? '').toLowerCase().includes(q)
        )
      : this.allUsers();
  });

  ngOnInit(): void {
    this.loadCampaigns();
    this.loadUsers();
    this.loadEmployeesContext();
  }

  private loadUsers(): void {
    this.usersLoading.set(true);
    this.adminService.getAllUsers()
      .pipe(finalize(() => this.usersLoading.set(false)))
      .subscribe({
        next: (users) => this.allUsers.set(users),
        error: () => this.notificationService.error('Impossible de charger la liste des utilisateurs.')
      });
  }

  private loadEmployeesContext(): void {
    this.dashboardService.getAdminOverview().subscribe({
      next: (ov) => this.sourceEmployees.set(ov.employees || []),
      error: () => console.error('Failed to load employee context')
    });
  }

  private loadCampaigns(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.campaignService.listCampaigns()
      .pipe(finalize(() => {
        this.loading.set(false);
        this.lastSync.set(this.getNowLabel());
      }))
      .subscribe({
        next: (data) => {
          this.campaigns.set(data.map(c => this.normalizeCampaign(c)));
          this.rebuildLogs();
        },
        error: () => {
          this.loadError.set('Erreur lors du chargement des campagnes.');
          this.notificationService.error('Impossible de récupérer les campagnes.');
        }
      });
  }

  selectDirectUser(user: User): void {
    this.directSelectedUser.set(user);
    this.directSearch.set('');
  }

  clearDirectUser(): void {
    this.directSelectedUser.set(null);
    this.directForm = { title: '', body: '', type: 'INFO', emailAlert: false };
  }

  getUserInitials(user: User): string {
    return `${user.firstName?.charAt(0) ?? ''}${user.lastName?.charAt(0) ?? ''}`.toUpperCase();
  }

  sendDirectMessage(): void {
    const target = this.directSelectedUser();
    if (!target) return;

    const title = this.directForm.title.trim();
    const body = this.directForm.body.trim();
    if (!title || !body) {
      this.notificationService.error('Renseignez un objet et un message.');
      return;
    }

    this.directSending.set(true);
    this.notifApiService.sendDirect({
      targetUserId: target.id,
      title,
      body,
      type: this.directForm.type,
      emailAlert: this.directForm.emailAlert
    })
    .pipe(finalize(() => this.directSending.set(false)))
    .subscribe({
      next: () => {
        this.directSentLog.update(log => [
          { name: `${target.firstName} ${target.lastName}`, title, sentAt: new Date().toLocaleString('fr-FR') },
          ...log
        ]);
        this.directForm = { title: '', body: '', type: 'INFO', emailAlert: false };
        this.notificationService.success(`Message envoyé à ${target.firstName} ${target.lastName}!`);
      },
      error: () => this.notificationService.error('Erreur lors de l\'envoi du message.')
    });
  }

  refreshLiveData(): void {
    this.loadCampaigns();
  }

  setTab(tab: 'templates' | 'campaigns' | 'logs' | 'direct'): void {
    this.activeTab.set(tab);
  }

  // ── Template actions ──────────────────────────────────────────
  selectTemplate(t: MessageTemplate): void {
    this.selectedTemplate.set({ ...t });
    this.isCreatingTemplate.set(false);
  }

  startNewTemplate(): void {
    this.newTemplate = { name: '', channel: 'EMAIL', category: 'Général', subject: '', body: '' };
    this.isCreatingTemplate.set(true);
    this.selectedTemplate.set(null);
  }

  updateTemplateField(field: keyof MessageTemplate, value: any): void {
    if (this.isCreatingTemplate()) {
      (this.newTemplate as any)[field] = value;
    } else {
      const current = this.selectedTemplate();
      if (current) {
        this.selectedTemplate.set({ ...current, [field]: value });
      }
    }
  }

  saveNewTemplate(): void {
    const t: MessageTemplate = {
      id: 't' + Date.now(),
      name: this.newTemplate.name!,
      channel: this.newTemplate.channel as CampaignChannel || 'EMAIL',
      category: this.newTemplate.category || 'Général',
      subject: this.newTemplate.subject || '',
      body: this.newTemplate.body!,
      variables: this.extractVariables(this.newTemplate.body!),
      createdAt: new Date().toISOString().slice(0, 10)
    };
    this.templates.update(ts => [t, ...ts]);
    this.isCreatingTemplate.set(false);
    this.selectedTemplate.set(t);
  }

  saveEditedTemplate(): void {
    const t = this.selectedTemplate();
    if (!t) return;
    this.templates.update(ts => ts.map(x => x.id === t.id ? t : x));
  }

  deleteTemplate(id: string): void {
    this.templates.update(ts => ts.filter(t => t.id !== id));
    if (this.selectedTemplate()?.id === id) this.selectedTemplate.set(null);
  }

  duplicateTemplate(t: MessageTemplate): void {
    const dup = { ...t, id: 't' + Date.now(), name: t.name + ' (copie)' };
    this.templates.update(ts => [dup, ...ts]);
  }

  extractVariables(body: string): string[] {
    const matches = body.match(/\{\{(\w+)\}\}/g) || [];
    return [...new Set(matches.map(m => m.replace(/[{}]/g, '')))];
  }

  // ── Campaign actions ──────────────────────────────────────────
  startNewCampaign(): void {
    this.newCampaign = {
      name: '',
      templateId: '',
      channel: 'EMAIL',
      targetGroup: 'ALL_EMPLOYEES',
      scheduledAt: ''
    };
    this.isCreatingCampaign.set(true);
    this.isEditingCampaign.set(false);
  }

  editCampaign(campaign: Campaign): void {
    this.newCampaign = { ...campaign };
    this.isCreatingCampaign.set(true);
    this.isEditingCampaign.set(true);
  }

  deleteCampaign(id: string): void {
    if (!confirm('Êtes-vous sûr de vouloir supprimer cette campagne ?')) return;

    this.loading.set(true);
    this.campaignService.delete(id)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: () => {
          this.campaigns.update(cs => cs.filter(c => c.id !== id));
          this.notificationService.success('Campagne supprimée.');
        },
        error: () => this.notificationService.error('Erreur lors de la suppression.')
      });
  }

  createCampaign(): void {
    if (!this.newCampaign.name || !this.newCampaign.templateId) {
      this.notificationService.error('Remplissez les champs obligatoires.');
      return;
    }

    const tmpl = this.templates().find(t => t.id === this.newCampaign.templateId);
    const payload: CampaignUpsertRequest = {
      id: this.isEditingCampaign() ? (this.newCampaign.id as string) : undefined,
      name: this.newCampaign.name!,
      templateId: this.newCampaign.templateId!,
      templateName: tmpl?.name || '—',
      channel: this.newCampaign.channel as CampaignChannel || 'EMAIL',
      targetGroup: this.newCampaign.targetGroup as CampaignTargetGroup || 'ALL_EMPLOYEES',
      recipientCount: this.getTargetGroupCount(this.newCampaign.targetGroup as CampaignTargetGroup),
      status: this.newCampaign.scheduledAt ? 'PLANIFIÉ' : 'BROUILLON',
      scheduledAt: this.newCampaign.scheduledAt ? new Date(this.newCampaign.scheduledAt).toISOString() : null,
      sentCount: 0,
      failedCount: 0,
      isPaused: false
    };

    this.loading.set(true);
    const req = this.isEditingCampaign() && payload.id
      ? this.campaignService.update(payload.id, payload)
      : this.campaignService.saveCampaign(payload);

    req.pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (saved) => {
          const normalized = this.normalizeCampaign(saved);
          if (this.isEditingCampaign()) {
             this.campaigns.update(cs => cs.map(c => c.id === normalized.id ? normalized : c));
          } else {
             this.campaigns.update(cs => [normalized, ...cs]);
          }
          this.isCreatingCampaign.set(false);
          this.notificationService.success(this.isEditingCampaign() ? 'Campagne mise à jour.' : 'Campagne créée.');
        },
        error: () => this.notificationService.error('Erreur lors de la sauvegarde.')
      });
  }

  cancelCampaignCreate(): void {
    this.isCreatingCampaign.set(false);
  }

  launchCampaign(campaignId: string): void {
    const campaign = this.campaigns().find(c => c.id === campaignId);
    if (!campaign) return;

    const payload: CampaignUpsertRequest = {
      ...campaign,
      status: 'ENVOYÉ',
      scheduledAt: new Date().toISOString()
    };

    this.loading.set(true);
    this.campaignService.update(campaignId, payload)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (saved) => {
          const normalized = this.normalizeCampaign(saved);
          this.campaigns.update(cs => cs.map(c => c.id === campaignId ? normalized : c));
          this.notificationService.success('Campagne lancée !');
        },
        error: () => this.notificationService.error('Erreur lors du lancement.')
      });
  }

  pauseCampaign(campaignId: string): void {
    const campaign = this.campaigns().find(c => c.id === campaignId);
    if (!campaign) return;

    const payload: CampaignUpsertRequest = {
      ...campaign,
      isPaused: !campaign.isPaused
    };

    this.campaignService.update(campaignId, payload).subscribe({
      next: (saved) => {
        const normalized = this.normalizeCampaign(saved);
        this.campaigns.update(cs => cs.map(c => c.id === campaignId ? normalized : c));
      },
      error: () => this.notificationService.error('Erreur lors de la mise à jour.')
    });
  }

  duplicateCampaign(campaignId: string): void {
    const source = this.campaigns().find(c => c.id === campaignId);
    if (!source) return;
    const payload: CampaignUpsertRequest = {
      ...source,
      id: undefined,
      name: `Copie de ${source.name}`,
      status: 'BROUILLON',
      sentCount: 0,
      failedCount: 0,
      scheduledAt: null
    };

    this.campaignService.saveCampaign(payload).subscribe({
      next: (saved) => {
        const normalized = this.normalizeCampaign(saved);
        this.campaigns.update(cs => [normalized, ...cs]);
        this.notificationService.info('Campagne dupliquée.');
      }
    });
  }

  getCampaignProgress(campaign: Campaign): number {
    if (campaign.status === 'BROUILLON') return 0;
    if (!campaign.recipientCount || campaign.recipientCount <= 0) return 0;
    return Math.min(100, Math.round((campaign.sentCount / campaign.recipientCount) * 100));
  }

  getTargetGroupLabel(group: CampaignTargetGroup): string {
    return this.targetGroupOptions.find(option => option.key === group)?.label || group;
  }

  getTargetGroupCount(group: CampaignTargetGroup): number {
    const employees = this.sourceEmployees();
    if (!employees.length) return 0;
    switch (group) {
      case 'ALL_EMPLOYEES': return employees.length;
      case 'ACTIVE_EMPLOYEES': return employees.filter(e => e.active).length;
      case 'PENDING_ASSESSMENT': return employees.filter(e => e.testCount === 0).length;
      case 'TRAINING_IN_PROGRESS': return employees.filter(e => e.formationCount > 0).length;
      default: return 0;
    }
  }

  getStatusClass(status: CampaignStatus | LogStatus): string {
    const map: Record<string, string> = {
      'ENVOYÉ': 'tag-sent', 'PLANIFIÉ': 'tag-scheduled', 'BROUILLON': 'tag-draft', 'ÉCHOUÉ': 'tag-failed',
      'LIVRÉ': 'tag-sent', 'EN ATTENTE': 'tag-scheduled'
    };
    return map[status] || 'tag-draft';
  }

  getChannelIcon(ch: string): string {
    return ch === 'SMS' ? '📱' : ch === 'EMAIL' ? '📧' : ch === 'IN_APP' ? '🔔' : '💬';
  }

  formatDate(d: string): string {
    if (!d) return '—';
    return new Date(d).toLocaleString('fr-FR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
  }

  getLogCount(status: LogStatus): number {
    return this.deliveryLogs().filter(l => l.status === status).length;
  }

  private rebuildLogs(): void {
    // Delivery logs are still derived from current campaign state as the backend doesn't have a dedicated logs entity yet
    const sentCampaigns = this.campaigns().filter(campaign => campaign.status === 'ENVOYÉ');
    const logs: DeliveryLog[] = [];

    for (const campaign of sentCampaigns) {
       // Mock logs based on real campaign metadata
       logs.push({
          id: `${campaign.id}-dummy`,
          campaignName: campaign.name,
          recipient: 'Multiple Recipients',
          channel: campaign.channel,
          status: 'LIVRÉ',
          sentAt: campaign.scheduledAt,
       });
    }
    this.deliveryLogs.set(logs);
  }

  private normalizeCampaign(campaign: CampaignApi): Campaign {
    return {
      ...(campaign as any),
      id: campaign.id,
      name: campaign.name,
      status: campaign.status as CampaignStatus,
      channel: campaign.channel as CampaignChannel,
      targetGroup: campaign.targetGroup as CampaignTargetGroup,
      recipientCount: campaign.recipientCount || 0,
      sentCount: campaign.sentCount || 0,
      failedCount: campaign.failedCount || 0,
      isPaused: campaign.isPaused || false,
      scheduledAt: campaign.scheduledAt || '',
      openRate: campaign.openRate || 0,
      clickRate: campaign.clickRate || 0
    };
  }

  private getNowLabel(): string {
    return new Date().toLocaleString('fr-FR', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
  }
}
