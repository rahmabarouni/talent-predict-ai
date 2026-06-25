import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { PredictionResponse } from '../models/prediction.model';

// Test summary (matches DashboardDto.TestSummaryDto)
export interface TestSummary {
  id: string;
  dateTest: Date;
  personalityType?: string;
  overallScore?: number;
  softSkillsScores?: Record<string, number>;
  summary?: string;
}

export interface DashboardSkill {
  id: string;
  nom: string;
  type: string;
  niveau: number;
  description?: string;
}

export interface DashboardFormation {
  id: string;
  titre: string;
  statut: string;
  progression: number;
  dateProposition?: Date | string;
  dateDebut?: Date | string;
}

// Employee dashboard response (matches DashboardDto.Response)
export interface EmployeeDashboardResponse {
  userId: string;
  nomComplet: string;
  firstName: string;
  lastName: string;
  nombreTests: number;
  nombreSkillsSoft: number;
  nombreSkillsTech: number;
  nombreFormationsTotal: number;
  nombreFormationsEnCours: number;
  nombreFormationsTerminees: number;
  scoreEvaluationMoyen: number;
  topSkills: DashboardSkill[];
  formationsRecentes: DashboardFormation[];
  testsRecents: TestSummary[];
  dernierePrediction: PredictionResponse | null;
}

// Admin overview response (matches DashboardDto.AdminOverviewDto)
export interface AdminOverviewResponse {
  totalEmployees: number;
  totalFormationsEnCours: number;
  totalTestsCompleted: number;
  totalPredictions: number;
  employees: EmployeeSummary[];
}

export interface EmployeeSummary {
  id: string;
  firstName: string;
  lastName: string;
  position: string;
  department: string;
  email: string;
  formationCount: number;
  testCount: number;
  personalityType?: string;
  active: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private http = inject(HttpClient);
  private dashboardUrl = `${environment.apiUrl}/dashboard`;
  private predictionsUrl = `${environment.apiUrl}/predictions`;

  /**
   * TASK 2: Employee dashboard — GET /api/dashboard/users/{userId}
   */
  getEmployeeDashboard(userId: string): Observable<EmployeeDashboardResponse> {
    return this.http.get<EmployeeDashboardResponse>(`${this.dashboardUrl}/users/${userId}`);
  }

  /**
   * TASK 2: Admin overview — GET /api/dashboard/admin/overview
   */
  getAdminOverview(): Observable<AdminOverviewResponse> {
    return this.http.get<AdminOverviewResponse>(`${this.dashboardUrl}/admin/overview`);
  }

  /**
   * Prediction entity integration — POST /api/predictions/users/{userId}/generer
   */
  generatePrediction(userId: string): Observable<PredictionResponse> {
    return this.http.post<PredictionResponse>(`${this.predictionsUrl}/users/${userId}/generer`, null);
  }

  /**
   * Prediction entity integration — GET /api/predictions/users/{userId}
   */
  getPredictions(userId: string): Observable<PredictionResponse[]> {
    return this.http.get<PredictionResponse[]>(`${this.predictionsUrl}/users/${userId}`);
  }

  /**
   * Prediction entity integration — GET /api/predictions/users/{userId}/derniere
   */
  getLatestPrediction(userId: string): Observable<PredictionResponse | null> {
    return this.http.get<PredictionResponse | null>(`${this.predictionsUrl}/users/${userId}/derniere`);
  }

  /**
   * Reporting — GET /api/reporting/talent-passport/{userId}
   */
  getTalentPassport(userId: string): Observable<Blob> {
    return this.http.get(`${environment.apiUrl}/reporting/talent-passport/${userId}`, { responseType: 'blob' });
  }

  /**
   * Global HR Report — GET /api/reporting/hr-global-report
   */
  getHrGlobalReport(): Observable<Blob> {
    return this.http.get(`${environment.apiUrl}/reporting/hr-global-report`, { responseType: 'blob' });
  }
}
