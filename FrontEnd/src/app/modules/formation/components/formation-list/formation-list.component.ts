import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { FormationService } from '../../services/formation.service';
import { FormationResponse, StatutFormation, TypeFormation } from '../../models/formation.model';
import { AuthService } from '../../../auth/services/auth.service';
import { Role } from '../../../auth/models/user.model';
import { SkillsService, SkillResponse } from '../../../skills/services/skills.service';
import { TypeSkill } from '../../../skills/models/skill.model';
import { SoftSkillsService } from '../../../evaluation/services/soft-skills.service';
import { SoftSkillsResult } from '../../../evaluation/models/soft-skills.model';
import {
  CareerLearningPlanResponse,
  CareerService,
  LearningPlanRequest
} from '../../../career/services/career.service';
import { catchError, map, of, forkJoin } from 'rxjs';
import { TestApiService } from '../../../skill-test/services/test-api.service';
import jsPDF from 'jspdf';
import html2canvas from 'html2canvas';
import { environment } from '../../../../../environments/environment';

type DeadlineRiskLevel = 'on-track' | 'at-risk' | 'late';

interface KanbanColumn {
  status: StatutFormation;
  title: string;
}

interface ReviewDraft {
  reviewNote: string;
}

interface QuizTemplateQuestion {
  key: string;
  prompt: string;
  options: string[];
  correctIndex: number;
}

interface MiniQuizQuestion {
  id: string;
  prompt: string;
  options: string[];
  correctIndex: number;
}

// Interfaces for new features
interface LeaderboardEntry {
  userId: string;
  username: string;
  xp: number;
  level: number;
  rank: number;
}

interface CalendarEvent {
  date: Date;
  title: string;
  type: 'course' | 'quiz' | 'milestone';
  formationId?: string;
}

interface CandidateProgressItem {
  test_type?: string;
  overall_score?: number;
  skill_scores?: Record<string, number>;
  taken_at: string;
  passed?: boolean;
}

@Component({
  selector: 'app-formation-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './formation-list.component.html',
  styleUrl: './formation-list.component.scss'
})
export class FormationListComponent implements OnInit {
  private formationService = inject(FormationService);
  private authService = inject(AuthService);
  private skillsService = inject(SkillsService);
  private softSkillsService = inject(SoftSkillsService);
  private careerService = inject(CareerService);
  private testApi = inject(TestApiService);
  private http = inject(HttpClient);
  currentUserId = '';

  readonly kanbanColumns: KanbanColumn[] = [
    { status: StatutFormation.PROPOSEE, title: 'Proposées' },
    { status: StatutFormation.EN_ATTENTE, title: 'En attente' },
    { status: StatutFormation.ACCEPTEE, title: 'Acceptées' },
    { status: StatutFormation.EN_COURS, title: 'En cours' },
    { status: StatutFormation.EN_ATTENTE_VALIDATION, title: 'Validation Admin' },
    { status: StatutFormation.TERMINEE, title: 'Terminées' }
  ];
  
  formations = signal<FormationResponse[]>([]);
  filteredFormations = signal<FormationResponse[]>([]);
  selectedFilter = signal<StatutFormation | 'ALL'>('ALL');
  loading = signal(false);
  error = signal<string | null>(null);

  draggedFormationId = signal<string | null>(null);
  statusActionLoadingId = signal<string | null>(null);

  reviewDrafts = signal<Record<string, ReviewDraft>>({});
  reviewSaving = signal<Record<string, boolean>>({});

  activeQuizFormation = signal<FormationResponse | null>(null);
  miniQuizAnswers = signal<Record<string, number[]>>({});
  miniQuizSubmitting = signal<Record<string, boolean>>({});
  miniQuizMessage = signal<string | null>(null);
  miniQuizError = signal<string | null>(null);
  miniQuizResult = signal<{ score: number; correct: number; total: number; passed: boolean } | null>(null);

  currentUserGamification = signal<{ xp: number; level: number } | null>(null);
  gamificationLoading = signal(false);

  certificateUploadStatus = signal<Record<string, boolean>>({});

  learningPlan = signal<CareerLearningPlanResponse | null>(null);
  learningPlanLoading = signal(false);
  learningPlanError = signal<string | null>(null);
  detectedWeakSkills = signal<NonNullable<LearningPlanRequest['weakSkills']>>([]);
  courseActionLoadingKey = signal<string | null>(null);
  courseActionError = signal<string | null>(null);
  courseActionSuccess = signal<string | null>(null);
  toastMessage = signal<{ text: string; type: 'success' | 'error' | 'info' } | null>(null);
  private softWeakSkillSet = new Set<string>();

  // ── Tabs (Sidebar) ──────────────────────────────────────────────────
  activeTab = signal<'DASHBOARD' | 'ROADMAP' | 'RECOMMANDATIONS' | 'KANBAN' | 'GAMIFICATION'>('DASHBOARD');

  // ── Recommendation filters ──────────────────────────────────────────────
  recoFilterSkill = signal<string>('ALL');
  recoFilterPriority = signal<string>('ALL');
  kanbanTypeFilter = signal<string>('ALL');

  targetRole = 'Software Engineer';
  experienceLevel: 'beginner' | 'junior' | 'mid' | 'senior' = 'junior';
  preferredLanguage: 'en' | 'fr' | 'ar' = 'fr';
  timezone = 'UTC';

  readonly StatutFormation = StatutFormation;
  readonly miniQuizPassingScore = 70;

  // Computed: how many roadmap phases have been completed (based on TERMINEE formations)
  currentRoadmapPhase = computed(() => {
    return this.formations().filter(f => f.statut === StatutFormation.TERMINEE).length;
  });
  
  // Leaderboard data now fetched from backend
  leaderboard = signal<{ userId: string; username: string; xp: number; level: number; rank: number }[]>([]);

  dailyChallenge = signal<{title: string, desc: string, xp: number, completed: boolean}>({
    title: 'Focus sur l\'Architecture',
    desc: 'Terminez 1 module de conception de microservices aujourd\'hui.',
    xp: 50,
    completed: sessionStorage.getItem('daily_challenge_done') === new Date().toDateString()
  });

  private readonly miniQuizTemplates: Record<'tech' | 'soft' | 'certification', QuizTemplateQuestion[]> = {
    tech: [
      { key: 't-1', prompt: 'Pour valider {topic}, quelle action démontre le mieux la maîtrise ?', options: ['Regarder uniquement les vidéos du cours', 'Appliquer les concepts sur un cas concret', 'Lire le résumé final sans pratiquer', 'Installer uniquement les outils'], correctIndex: 1 },
      { key: 't-2', prompt: 'Quel réflexe réduit le plus les erreurs en production sur {topic} ?', options: ['Ne pas tester pour aller plus vite', 'Tester, vérifier les logs et documenter les changements', 'Modifier directement en production', 'Ignorer les conventions de code'], correctIndex: 1 },
      { key: 't-3', prompt: 'Quelle est la meilleure approche pour apprendre une nouvelle technologie comme {topic} ?', options: ['Apprendre par coeur toute la documentation', 'Construire un petit projet "Bac à sable"', 'Demander à quelqu\'un d\'autre de coder', 'Attendre d\'avoir un projet client'], correctIndex: 1 },
      { key: 't-4', prompt: 'Sur {topic}, comment gérez-vous une erreur bloquante complexe ?', options: ['Abandonner le sujet', 'Analyse méthodique, lecture des erreurs et recherche dans la communauté/docs', 'Recoder tout depuis le début sans réfléchir', 'Ignorer l\'erreur si elle n\'est pas visible'], correctIndex: 1 },
      { key: 't-5', prompt: 'Quelle pratique assure la maintenabilité sur le long terme ?', options: ['Code complexe et dense', 'Code propre, commenté et modulaire', 'Pas de commentaires pour gagner du temps', 'Tout mettre dans un seul fichier'], correctIndex: 1 }
    ],
    soft: [
      { key: 's-1', prompt: 'En travaillant sur {topic}, comment réagissez-vous à un feedback critique ?', options: ['Le prendre personnellement', 'L\'écouter, demander des précisions et s\'améliorer', 'Ignorer le feedback', 'Se justifier sans écouter'], correctIndex: 1 },
      { key: 's-2', prompt: 'Quel est l\'ingrédient clé pour exceller en {topic} ?', options: ['Travailler uniquement seul', 'Empathie, écoute active et communication claire', 'Parler le plus fort possible', 'Ne jamais admettre ses erreurs'], correctIndex: 1 },
      { key: 's-3', prompt: 'Comment gérez-vous un conflit d\'équipe sur {topic} ?', options: ['Éviter le sujet', 'Discussion ouverte et recherche d\'un compromis constructif', 'Imposer son point de vue', 'Se plaindre au manager sans parler à l\'intéressé'], correctIndex: 1 },
      { key: 's-4', prompt: 'Quelle est l\'importance de {topic} dans un rôle technique ?', options: ['Secondaire par rapport au code', 'Cruciale pour la collaboration et la réussite du projet', 'Inutile si on est un expert technique', 'Uniquement pour les managers'], correctIndex: 1 },
      { key: 's-5', prompt: 'Comment développez-vous votre {topic} au quotidien ?', options: ['En lisant uniquement des livres', 'Par la pratique consciente et l\'observation des pairs', 'On naît avec, on ne peut pas changer', 'En évitant les interactions sociales'], correctIndex: 1 }
    ],
    certification: [
      { key: 'c-1', prompt: 'Le certificat pour {topic} prouve que vous avez...', options: ['Terminé le temps imparti', 'Validé les acquis théoriques et pratiques essentiels', 'Payé la formation', 'Simplement ouvert tous les modules'], correctIndex: 1 },
      { key: 'c-2', prompt: 'Après avoir obtenu votre certif sur {topic}, quelle est la suite ?', options: ['Tout oublier immédiatement', 'Continuer à pratiquer et partager ses connaissances', 'Ne plus jamais étudier ce sujet', 'Mettre le diplôme au placard'], correctIndex: 1 },
      { key: 'c-3', prompt: 'Pourquoi la validation administrative est-elle nécessaire après l\'upload ?', options: ['Pour perdre du temps', 'Pour assurer l\'authenticité du certificat et la conformité au plan', 'C\'est une erreur système', 'Ce n\'est pas nécessaire'], correctIndex: 1 }
    ]
  };

  ngOnInit(): void {
    const currentUser = this.authService.getCurrentUser();
    if (currentUser?.id) {
      this.currentUserId = String(currentUser.id);
      
      this.initializeDefaults();
      this.loadFormations();
      this.loadLeaderboard();

      this.authService.fetchMyProfile().subscribe({
        next: (profile) => {
          if (profile.xp != null && profile.level != null) {
            this.currentUserGamification.set({ xp: profile.xp, level: profile.level });
            // Sync real XP into leaderboard 'me' row
            this.leaderboard.update(lb =>
              lb.map(e => e.userId === this.currentUserId ? { ...e, xp: profile.xp ?? 0, level: profile.level ?? 0 } : e)
            );
          }
        },
        error: () => {}
      });
    } else {
      this.error.set("Utilisateur non identifié.");
    }
  }



  private loadLeaderboard(): void {
    this.gamificationLoading.set(true);
    this.authService.getLeaderboard().subscribe({
      next: (data) => {
        const mapped = data.map((d, i) => ({
          userId: d.id,
          username: d.username,
          xp: d.xp,
          level: d.level,
          rank: i + 1
        }));
        this.leaderboard.set(mapped);
        this.gamificationLoading.set(false);
      },
      error: (err) => {
        console.error('Leaderboard error', err);
        this.gamificationLoading.set(false);
      }
    });
  }

  loadFormations(): void {
    this.loading.set(true);
    this.formationService.getUserFormations(this.currentUserId).subscribe({
      next: (data) => {
        // Deduplicate by ID AND Title (case-insensitive) to prevent duplicates
        const seenIds = new Set<string>();
        const seenTitles = new Set<string>();
        const unique = data.filter(f => {
          if (seenIds.has(f.id)) return false;
          const normalizedTitle = f.titre.trim().toLowerCase();
          if (seenTitles.has(normalizedTitle)) return false;
          
          seenIds.add(f.id);
          seenTitles.add(normalizedTitle);
          return true;
        });
        this.formations.set(unique);
        this.hydrateReviewDrafts(unique);
        this.applyActiveFilter();
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Error loading formations:', err);
        this.loading.set(false);
      }
    });
  }

  generateLearningPlan(): void {
    if (!this.currentUserId) return;

    this.learningPlanLoading.set(true);
    this.learningPlanError.set(null);

    this.buildWeakSkillsFromUserData(this.currentUserId).subscribe({
      next: ({ weakSkills, softSkillKeys }) => {
        this.softWeakSkillSet = softSkillKeys;
        this.detectedWeakSkills.set(weakSkills);

        const payload: LearningPlanRequest = {
          candidate_id: this.currentUserId,
          targetRole: this.targetRole,
          experienceLevel: this.experienceLevel,
          hoursPerDay: 2,
          preferredLanguage: this.preferredLanguage,
          timezone: this.timezone,
          learningStyle: 'visual',
          weakSkills: weakSkills
        };

        this.careerService.generateLearningPlan(payload).subscribe({
          next: (plan) => {
            this.learningPlan.set(this.normalizeLearningPlan(plan));
            this.learningPlanLoading.set(false);
          },
          error: (err) => {
            this.learningPlanLoading.set(false);
            this.learningPlanError.set('Erreur lors de la génération du plan IA.');
          }
        });
      },
      error: () => {
        this.learningPlanLoading.set(false);
        this.learningPlanError.set('Impossible de récupérer vos faiblesses.');
      }
    });
  }

  completeDailyChallenge() {
    this.dailyChallenge.update(c => ({...c, completed: true}));
    sessionStorage.setItem('daily_challenge_done', new Date().toDateString());
    if (this.currentUserGamification()) {
      this.currentUserGamification.update(g => g ? {...g, xp: g.xp + 50} : null);
    }
    // Sync 'me' row in leaderboard with real XP
    const realXp = this.currentUserGamification()?.xp ?? 0;
    this.leaderboard.update(lb => lb.map(e => e.userId === this.currentUserId ? {...e, xp: realXp} : e));
    this.showToast('🎯 Défi quotidien accompli ! +50 XP', 'success');
  }

  // ── Filters & Computed ──────────────────────────────────────────────

  get filteredRecoFormations() {
    const plan = this.learningPlan();
    if (!plan) return [];
    let result = plan.formations;
    const skill = this.recoFilterSkill();
    const priority = this.recoFilterPriority();
    if (skill !== 'ALL') result = result.filter(f => f.skill.toLowerCase() === skill.toLowerCase());
    if (priority !== 'ALL') result = result.filter(f => f.priority === priority);
    return result;
  }

  recoUniqueSkills(): string[] {
    return [...new Set((this.learningPlan()?.formations || []).map(f => f.skill))];
  }

  priorityLabel(priority: string): string {
    if (priority === 'critical') return '🔴 Critique';
    if (priority === 'high') return '🟠 Haute';
    if (priority === 'medium') return '🟡 Moyenne';
    return '🟢 Faible';
  }

  platformIcon(platform: string): string {
    const p = platform.toLowerCase();
    if (p.includes('udemy')) return 'udemy';
    if (p.includes('coursera')) return 'coursera';
    if (p.includes('linkedin')) return 'linkedin';
    return 'globe';
  }

  formationsEnCoursCount(): number {
    return this.formations().filter(f => f.statut === StatutFormation.EN_COURS || f.statut === StatutFormation.ACCEPTEE).length;
  }

  formationsTermineesCount(): number {
    return this.formations().filter(f => f.statut === StatutFormation.TERMINEE).length;
  }

  overallProgressPct(): number {
    const all = this.formations();
    if (!all.length) return 0;
    const total = all.reduce((sum, f) => sum + (f.progression ?? 0), 0);
    return Math.round(total / all.length);
  }

  formationsByStatusFiltered(status: StatutFormation): FormationResponse[] {
    const typeFilter = this.kanbanTypeFilter();
    return this.formations().filter(f => {
      if (f.statut !== status) return false;
      if (typeFilter === 'ALL') return true;
      return f.type?.toString().includes(typeFilter);
    });
  }

  startCoursePractice(skill: string, course: any): void {
    this.courseActionLoadingKey.set(`${skill}::${course.id}`);
    
    // Ensure duration is a valid integer for backend
    const numericDuration = Math.max(1, Math.round(typeof course.duration_hours === 'number' 
      ? course.duration_hours 
      : (parseFloat(String(course.duration_hours)) || 1)));

    this.formationService.createFormation(this.currentUserId, {
      titre: course.title,
      description: `Cible: ${skill}. ${course.reason}`,
      type: this.resolveFormationType(skill),
      duree: numericDuration,
      fournisseur: course.platform || 'Autre',
      url: course.url || '',
      statut: StatutFormation.PROPOSEE
    }).subscribe({
      next: () => {
        this.courseActionSuccess.set(`Le cours "${course.title}" a été ajouté.`);
        this.showToast(`✅ "${course.title}" ajouté à votre plan !`, 'success');
        this.loadFormations();
      },
      error: (err) => {
        console.error("Erreur ajout cours:", err);
        this.courseActionError.set('Erreur lors de l\'ajout du cours.');
        this.showToast('❌ Erreur lors de l\'ajout du cours.', 'error');
      },
      complete: () => this.courseActionLoadingKey.set(null)
    });
  }

  isCourseLoading(skill: string, course: any): boolean {
    return this.courseActionLoadingKey() === `${skill}::${course.id}`;
  }

  // ── PDF Export ───────────────────────────────────────────────────────
  isExportingPdf = signal(false);

  exportToPdf(): void {
    this.isExportingPdf.set(true);
    const element = document.querySelector('.main-content') as HTMLElement;
    if (!element) return;

    html2canvas(element, { scale: 2 }).then(canvas => {
      const pdf = new jsPDF('p', 'mm', 'a4');
      const imgData = canvas.toDataURL('image/png');
      const imgWidth = 210; 
      const pageHeight = 297; 
      const imgHeight = (canvas.height * imgWidth) / canvas.width;
      let heightLeft = imgHeight;
      let position = 0;

      pdf.addImage(imgData, 'PNG', 0, position, imgWidth, imgHeight);
      heightLeft -= pageHeight;
      while (heightLeft >= 0) {
        position = heightLeft - imgHeight;
        pdf.addPage();
        pdf.addImage(imgData, 'PNG', 0, position, imgWidth, imgHeight);
        heightLeft -= pageHeight;
      }
      pdf.save('Mes_Formations_Roadmap.pdf');
      this.isExportingPdf.set(false);
    });
  }

  // ── Kanban Logic ─────────────────────────────────────────────────────

  statusCount(status: StatutFormation): number {
    return this.formationsByStatusFiltered(status).length;
  }

  onCardDragStart(event: DragEvent, formation: FormationResponse): void {
    if (event.dataTransfer) {
      this.draggedFormationId.set(formation.id);
      event.dataTransfer.effectAllowed = 'move';
      event.dataTransfer.setData('text', formation.id);
    }
  }

  onCardDragEnd(): void {
    this.draggedFormationId.set(null);
  }

  allowDrop(event: DragEvent): void {
    event.preventDefault();
  }

  onDrop(event: DragEvent, targetStatus: StatutFormation): void {
    event.preventDefault();
    const formationId = event.dataTransfer?.getData('text') || this.draggedFormationId();
    this.draggedFormationId.set(null);
    if (!formationId || formationId.startsWith('virtual_')) return;
    
    const formation = this.formations().find(f => f.id === formationId);
    if (formation && formation.statut !== targetStatus) {
      this.moveFormationToStatus(formation, targetStatus);
    }
  }

  moveToNextStatus(formation: FormationResponse): void {
    const flow = [StatutFormation.PROPOSEE, StatutFormation.EN_ATTENTE, StatutFormation.ACCEPTEE, StatutFormation.EN_COURS, StatutFormation.TERMINEE];
    const idx = flow.indexOf(formation.statut);
    if (idx >= 0 && idx < flow.length - 1) {
      this.moveFormationToStatus(formation, flow[idx + 1]);
    }
  }

  canMoveToNextStatus(formation: FormationResponse): boolean {
    return formation.statut !== StatutFormation.TERMINEE && formation.statut !== StatutFormation.ANNULEE;
  }

  moveFormationToStatus(formation: FormationResponse, targetStatus: StatutFormation): void {
    this.statusActionLoadingId.set(formation.id);
    this.formationService.updateFormationStatus(formation.id, targetStatus).subscribe({
      next: (updated) => this.upsertUpdatedFormation(updated),
      complete: () => this.statusActionLoadingId.set(null)
    });
  }

  updateProgressionFromInput(formation: FormationResponse, rawValue: string | number): void {
    const progression = Math.max(0, Math.min(100, Number(rawValue)));
    if (formation.progression !== progression) {
      this.statusActionLoadingId.set(formation.id);
      this.formationService.updateFormationProgress(formation.id, progression).subscribe({
        next: (updated) => this.upsertUpdatedFormation(updated),
        complete: () => this.statusActionLoadingId.set(null)
      });
    }
  }

  isStatusActionLoading(id: string): boolean {
    return this.statusActionLoadingId() === id;
  }

  deleteFormation(id: string): void {
    if (confirm('Voulez-vous vraiment supprimer cette formation ?')) {
      this.statusActionLoadingId.set(id);
      this.formationService.deleteFormation(id).subscribe({
        next: () => {
          this.formations.update(rows => rows.filter(r => r.id !== id));
          this.showToast('✅ Formation supprimée', 'success');
        },
        error: () => this.showToast('❌ Erreur lors de la suppression', 'error'),
        complete: () => this.statusActionLoadingId.set(null)
      });
    }
  }

  // ── Risk & Deadlines ─────────────────────────────────────────────────
  deadlineRiskLevel(formation: FormationResponse): DeadlineRiskLevel {
    if (formation.statut === StatutFormation.TERMINEE || (formation.progression ?? 0) >= 100) return 'on-track';
    const now = new Date().getTime();
    const startDate = formation.dateDebut ? new Date(formation.dateDebut).getTime() : 0;
    const endDate = formation.dateFin ? new Date(formation.dateFin).getTime() : 0;
    if (endDate && now > endDate) return 'late';
    if (startDate && endDate && now > startDate) {
      const progress = (now - startDate) / (endDate - startDate) * 100;
      if ((formation.progression ?? 0) + 20 < progress) return 'at-risk';
    }
    return 'on-track';
  }

  deadlineRiskLabel(formation: FormationResponse): string {
    const lvl = this.deadlineRiskLevel(formation);
    return lvl === 'late' ? 'En Retard' : lvl === 'at-risk' ? 'À Risque' : 'Dans les temps';
  }

  deadlineRiskClass(formation: FormationResponse): string {
    const lvl = this.deadlineRiskLevel(formation);
    return lvl === 'late' ? 'k-badge critical' : lvl === 'at-risk' ? 'k-badge high' : 'k-badge low';
  }

  lateCount(): number { return this.formations().filter(f => this.deadlineRiskLevel(f) === 'late').length; }
  atRiskCount(): number { return this.formations().filter(f => this.deadlineRiskLevel(f) === 'at-risk').length; }
  onTrackCount(): number { return this.formations().filter(f => this.deadlineRiskLevel(f) === 'on-track').length; }

  // ── Mini Quiz (Modal) ────────────────────────────────────────────────
  
  isMiniQuizEligible(formation: FormationResponse): boolean {
    return formation.statut === StatutFormation.TERMINEE || (formation.progression ?? 0) >= 100;
  }

  openMiniQuiz(formation: FormationResponse): void {
    if (this.isMiniQuizEligible(formation)) {
      this.activeQuizFormation.set(formation);
      const qCount = this.miniQuizQuestions(formation).length;
      this.miniQuizAnswers.set({ [formation.id]: Array(qCount).fill(-1) });
      this.miniQuizMessage.set(null);
      this.miniQuizError.set(null);
      this.miniQuizResult.set(null);
    }
  }

  closeMiniQuiz(): void {
    this.activeQuizFormation.set(null);
  }

  miniQuizQuestions(formation: FormationResponse): MiniQuizQuestion[] {
    const family = this.resolveFormationType(formation.titre) === TypeFormation.SOFT_SKILL ? 'soft' : 'tech';
    return this.miniQuizTemplates[family].map((t, i) => ({
      id: `${formation.id}-${t.key}`,
      prompt: t.prompt.replace('{topic}', formation.titre),
      options: t.options,
      correctIndex: t.correctIndex
    }));
  }

  miniQuizAnswerAt(fid: string, idx: number): number | null {
    return this.miniQuizAnswers()[fid]?.[idx] ?? null;
  }

  setMiniQuizAnswer(fid: string, qIdx: number, oIdx: number): void {
    this.miniQuizAnswers.update(state => {
      const arr = [...(state[fid] || [])];
      arr[qIdx] = oIdx;
      return { ...state, [fid]: arr };
    });
  }

  submitMiniQuiz(formation: FormationResponse): void {
    const answers = this.miniQuizAnswers()[formation.id] || [];
    if (answers.some(a => a < 0)) {
      this.miniQuizError.set('Veuillez répondre à toutes les questions.');
      return;
    }

    const questions = this.miniQuizQuestions(formation);
    const correct = questions.reduce((acc, q, i) => acc + (answers[i] === q.correctIndex ? 1 : 0), 0);
    const score = Math.round((correct / questions.length) * 100);
    const passed = score >= this.miniQuizPassingScore;

    this.miniQuizSubmitting.set({ [formation.id]: true });
    this.miniQuizError.set(null);

    this.formationService.submitMiniTest(formation.id, { score, correctAnswers: correct, totalQuestions: questions.length, passingScore: 70 }).subscribe({
      next: (res) => {
        this.upsertUpdatedFormation(res);
        this.miniQuizResult.set({ score, correct, total: questions.length, passed });
        if (passed) {
          this.showToast(`🎉 Test réussi avec ${score}% !`, 'success');
          if (this.currentUserGamification()) {
            this.currentUserGamification.update(g => g ? { ...g, xp: g.xp + 100 } : null);
          }
        } else {
          this.showToast(`❌ Échec : ${score}%. Réessayez !`, 'error');
        }
      },
      error: () => {
        this.miniQuizError.set('Erreur serveur.');
        this.miniQuizResult.set(null);
      },
      complete: () => this.miniQuizSubmitting.set({ [formation.id]: false })
    });
  }

  retryMiniQuiz(formation: FormationResponse): void {
    this.miniQuizResult.set(null);
    this.miniQuizError.set(null);
    const qCount = this.miniQuizQuestions(formation).length;
    this.miniQuizAnswers.set({ [formation.id]: Array(qCount).fill(-1) });
  }

  isMiniQuizSubmitting(id: string) { return !!this.miniQuizSubmitting()[id]; }

  canUploadCertificate(f: FormationResponse) { return f.miniTestPassed === true; }

  validateCertificate(formation: FormationResponse): void {
    if (!formation.certificateUrl) return;
    this.formationService.updateFormationStatus(formation.id, StatutFormation.EN_ATTENTE_VALIDATION).subscribe({
      next: (res) => {
        this.upsertUpdatedFormation(res);
        this.activeQuizFormation.set(null);
        this.showToast('📋 Certificat envoyé à l\'administration pour validation !', 'info');
      }
    });
  }

  onCertificateSelected(event: any, formation: FormationResponse): void {
    const file = event.target.files[0];
    if (file) {
      this.uploadCertificate(formation.id, file);
    }
  }

  private uploadCertificate(formationId: string, file: File): void {
    this.certificateUploadStatus.update(s => ({ ...s, [formationId]: true }));
    this.formationService.uploadCertificate(formationId, file).subscribe({
      next: (res) => {
        this.upsertUpdatedFormation(res);
        this.certificateUploadStatus.update(s => ({ ...s, [formationId]: false }));
      },
      error: () => {
        this.certificateUploadStatus.update(s => ({ ...s, [formationId]: false }));
      }
    });
  }

  isCertificateUploading(id: string) { return !!this.certificateUploadStatus()[id]; }

  // ── Admin / Review Notes ─────────────────────────────────────────────
  
  canEditReviewNotes(): boolean {
    const role = this.authService.getCurrentUser()?.role;
    return role === Role.ADMIN;
  }

  getReviewDraft(id: string) { return this.reviewDrafts()[id] || { reviewNote: '' }; }
  
  updateReviewDraft(id: string, field: keyof ReviewDraft, val: string) {
    this.reviewDrafts.update(d => ({ ...d, [id]: { ...this.getReviewDraft(id), [field]: val } }));
  }

  saveReviewNotes(formation: FormationResponse): void {
    if (this.canEditReviewNotes()) {
      this.formationService.updateFormationReviewNotes(formation.id, { reviewNote: this.getReviewDraft(formation.id).reviewNote }).subscribe(res => {
         this.upsertUpdatedFormation(res);
         this.hydrateReviewDrafts(this.formations());
      });
    }
  }

  private hydrateReviewDrafts(rows: FormationResponse[]) {
    const drafts: Record<string, ReviewDraft> = {};
    rows.forEach(r => drafts[r.id] = { reviewNote: r.reviewNote || '' });
    this.reviewDrafts.set(drafts);
  }

  // ── Internal Helpers ─────────────────────────────────────────────────
  
  weakSkillBadgeClass(name: string) { return this.resolveFormationType(name) === TypeFormation.SOFT_SKILL ? 'soft' : 'tech'; }

  // Dynamically compute readiness gain based on course duration vs skill gap
  courseReadinessGain(skill: string, course: any): number {
    const plan = this.learningPlan();
    const gap = plan?.skill_gap_analysis?.breakdown?.find(
      (b: any) => b.skill?.toLowerCase() === skill?.toLowerCase()
    );
    if (!gap) return 2;
    const deficit = Math.max(0, (gap.required_level || 10) - (gap.current_level || 0));
    const hours = course.duration_hours || 1;
    return Math.min(25, Math.round((deficit / 10) * 15 + Math.log(hours + 1) * 2));
  }

  courseExpectedLevelAfter(s: string, c: any) { return 8; }
  
  private applyActiveFilter() {
    this.filteredFormations.set(this.selectedFilter() === 'ALL' ? this.formations() : this.formations().filter(f => f.statut === this.selectedFilter()));
  }

  private upsertUpdatedFormation(f: FormationResponse) {
    this.formations.update(rows => rows.map(r => r.id === f.id ? f : r));
    if (this.activeQuizFormation()?.id === f.id) this.activeQuizFormation.set(f);
  }

  private resolveFormationType(skill: string): TypeFormation {
    return this.softWeakSkillSet.has(skill.toLowerCase()) ? TypeFormation.SOFT_SKILL : TypeFormation.TECH_SKILL;
  }

  private buildTechWeakFromProgress(progress: CandidateProgressItem[]): NonNullable<LearningPlanRequest['weakSkills']> {
    if (!progress?.length) return [];
    const latest = progress[0];
    const scores = latest?.skill_scores;
    if (!scores || typeof scores !== 'object') return [];

    return Object.entries(scores)
      .filter(([name, value]) => !name.startsWith('_') && !isNaN(Number(value)))
      .map(([name, value]) => {
        const raw = Number(value);
        const score100 = Math.max(0, Math.min(100, Math.round(Number.isFinite(raw) ? raw : 10)));
        return { name, score: score100, required_level: 8 };
      })
      .filter(row => row.name.trim().length > 0)
      .sort((a, b) => a.score - b.score)
      .slice(0, 3);
  }

  private initializeDefaults() {
    this.timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
    this.authService.getProfile(this.currentUserId).subscribe(p => {
      this.targetRole = p.titreProfessionnel || this.targetRole;
      this.experienceLevel = (p.experienceAns || 0) <= 2 ? 'junior' : 'senior';
    });
  }

  private buildWeakSkillsFromUserData(userId: string) {
    return forkJoin({
      skills: this.skillsService.getUserSkills(userId).pipe(catchError(() => of([]))),
      soft: this.softSkillsService.getLastAnalysis().pipe(catchError(() => of(null))),
      progress: this.http.get<CandidateProgressItem[]>(`${environment.apiUrl}/candidates/${userId}/progress`).pipe(
        catchError(() => of([]))
      )
    }).pipe(
      map(({ skills, soft, progress }: { skills: SkillResponse[], soft: any, progress: CandidateProgressItem[] }) => {
        const techWeakFromProgress = this.buildTechWeakFromProgress(progress);
        const techWeakFromSkills = skills.filter((s: SkillResponse) => s.type === TypeSkill.TECH)
          .sort((a: SkillResponse, b: SkillResponse) => (a.niveau || 1) - (b.niveau || 1))
          .slice(0, 3)
          .map((s: SkillResponse) => ({ name: s.nom, score: (s.niveau || 1) * 20, required_level: 8 }));
        const techWeak = techWeakFromProgress.length > 0 ? techWeakFromProgress : techWeakFromSkills;
        const softWeak = (Object.entries(soft?.mergedSoftSkills || {}) as [string, number][])
          .sort((a, b) => a[1] - b[1])
          .slice(0, 3)
          .map(([k, v]) => ({ name: k, score: v * 10, required_level: 8 }));
        
        const softKeys = new Set(softWeak.map(s => s.name.toLowerCase()));
        if (!softKeys.size) softKeys.add('communication');

        return { weakSkills: [...techWeak, ...softWeak].slice(0, 6), softSkillKeys: softKeys };
      })
    );
  }

  private normalizeLearningPlan(plan: CareerLearningPlanResponse): CareerLearningPlanResponse {
    const raw = plan as any;
    const skillGap = raw.skill_gap_analysis || {};
    const roadmap = raw.roadmap || [];
    const formations = raw.formations || [];
    return {
      meta: { estimated_ready_date: raw.meta?.estimated_ready_date || raw.generated_at || new Date().toISOString() },
      summary: { overall_readiness_pct: raw.summary?.overall_readiness_pct || 40, profile_evaluation: raw.summary?.profile_evaluation || 'Analyse IA en attente...' },
      skill_gap_analysis: { breakdown: (skillGap.breakdown || skillGap.target_role_requirements || []).map((b:any) => ({ skill: b.skill, current_level: b.current_level||0, required_level: b.required_level||10 })) },
      roadmap: roadmap.map((r:any) => ({ phase: r.phase || 1, title: r.title || `Phase ${r.phase}`, duration_weeks: r.duration_weeks || r.duration || 2, focus_skills: r.focus_skills || r.focus || [], goals: r.goals || [] })),
      formations: formations.map((f:any) => ({ skill: f.skill, priority: f.priority, courses: (f.courses||[]).map((c:any) => ({ id: c.id || Math.random().toString(36).substr(2, 9), title: c.title, platform: c.platform, url: c.url, duration_hours: c.duration_hours || c.duration || 0, level: c.level || 'Beginner', reason: c.reason || 'Recommandé par IA' })) }))
    } as any;
  }

  private showToast(text: string, type: 'success' | 'error' | 'info'): void {
    this.toastMessage.set({ text, type });
    setTimeout(() => this.toastMessage.set(null), 3500);
  }
}
