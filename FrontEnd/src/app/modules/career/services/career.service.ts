import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface WeakSkill {
  name: string;
  score: number;
  required_level: number;
}

export interface LearningPlanRequest {
  candidate_id: string;
  targetRole?: string;
  experienceLevel: string;
  hoursPerDay: number;
  preferredLanguage: string;
  learningStyle: string;
  timezone: string;
  weakSkills: WeakSkill[];
}

export interface CareerLearningPlanResponse {
  generated_at?: string;
  candidate_id?: string;
  target_role?: string;
  language?: string;
  meta?: any;
  summary: {
    profile_evaluation?: string;
    main_gaps: string[];
    estimated_time_to_ready?: string;
    strengths?: string[];
    overall_readiness_pct?: number;
    time_management_strategy?: string;
  };
  skill_gap_analysis: {
    target_role_requirements?: any[];
    readiness_score: number;
    critical_blockers?: string[];
    breakdown: any[]; 
    estimated_weeks_to_ready?: number;
  };
  roadmap: {
    phase: string | number;
    title?: string;
    duration?: string;
    duration_weeks?: number;
    start_week?: number;
    end_week?: number;
    focus?: string[];
    focus_skills?: string[];
    goals?: string[];
    success_criteria?: string[];
    exit_criteria?: string[];
    time_management_tips?: string;
  }[];
  formations: {
    skill: string;
    priority: string;
    required_level: number;
    current_level: number;
    gap: number;
    courses: {
      id?: string;
      platform: string;
      provider: string;
      title: string;
      reason?: string;
      duration_hours?: string | number;
      duration?: string | number;
      url: string;
      level?: string;
      phase_ref?: number;
    }[];
  }[];
  reinforcement: any[];
  project_plan: any;
  daily_plan: any[];
  milestones: any[];
  assessments: any[];
  re_evaluation: any;
  mentor_profile: any;
  market_alignment: any;
  weekly_checkins: any[];
  progress?: any;
  localization?: any;
}

@Injectable({
  providedIn: 'root'
})
export class CareerService {
  private http = inject(HttpClient);
  // Routing through the Spring Boot proxy
  private apiUrl = `${environment.apiUrl}/career`;

  generateLearningPlan(payload: LearningPlanRequest): Observable<CareerLearningPlanResponse> {
    return this.http.post<CareerLearningPlanResponse>(`${this.apiUrl}/learning-plan`, payload);
  }
}
