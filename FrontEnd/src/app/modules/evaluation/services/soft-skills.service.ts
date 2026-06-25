import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap, catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  SoftSkillsAnalysisRequest,
  SoftSkillsResult,
  SoftSkillsProgress
} from '../models/soft-skills.model';

@Injectable({ providedIn: 'root' })
export class SoftSkillsService {

  private readonly api = `${environment.apiUrl}/soft-skills`;

  constructor(private http: HttpClient) {
  }

  analyze(request: SoftSkillsAnalysisRequest): Observable<SoftSkillsResult> {
    const url = `${this.api}/analyze`;
    return this.http.post<SoftSkillsResult>(url, request).pipe(
      tap(res => console.log('[SoftSkillsService] POST Response:', res)),
      catchError(err => {
        console.error('[SoftSkillsService] POST Error:', err.status, err.message);
        return throwError(() => err);
      })
    );
  }

  reevaluate(request: SoftSkillsAnalysisRequest): Observable<SoftSkillsResult> {
    const url = `${this.api}/reevaluate`;
    return this.http.post<SoftSkillsResult>(url, request).pipe(
      tap(res => console.log('[SoftSkillsService] POST Response:', res)),
      catchError(err => {
        console.error('[SoftSkillsService] POST Error:', err.status, err.message);
        return throwError(() => err);
      })
    );
  }

  getProgress(): Observable<SoftSkillsProgress[]> {
    const url = `${this.api}/progress`;
    return this.http.get<SoftSkillsProgress[]>(url).pipe(
      tap(res => console.log('[SoftSkillsService] GET Response:', res)),
      catchError(err => {
        console.error('[SoftSkillsService] GET Error:', err.status, err.message);
        return throwError(() => err);
      })
    );
  }

  getLastAnalysis(): Observable<SoftSkillsResult> {
    const url = `${this.api}/last`;
    return this.http.get<SoftSkillsResult>(url).pipe(
      tap(res => console.log('[SoftSkillsService] GET Response:', res)),
      catchError(err => {
        console.error('[SoftSkillsService] GET Error:', err.status, err.message);
        return throwError(() => err);
      })
    );
  }

  analyzeSoftSkillsWithExtractedText(payload: {
    full_name: string;
    email: string;
    extracted_cv_text: string;
    q1: number; q2: number; q3: number; q4: number; q5: number; q6: number;
    q7: number; q8: number; q9: number; q10: number; q11: number; q12: number;
    q13: number; q14: number; q15: number; q16: number; q17: number; q18: number;
    github_username: string;
  }): Observable<any> {
    const url = `${this.api}/analyze`;
    const requestBody: SoftSkillsAnalysisRequest = {
      fullName: payload.full_name,
      email: payload.email,
      githubUsername: payload.github_username,
      cvText: payload.extracted_cv_text,
      q1: payload.q1,
      q2: payload.q2,
      q3: payload.q3,
      q4: payload.q4,
      q5: payload.q5,
      q6: payload.q6,
      q7: payload.q7,
      q8: payload.q8,
      q9: payload.q9,
      q10: payload.q10,
      q11: payload.q11,
      q12: payload.q12,
      q13: payload.q13,
      q14: payload.q14,
      q15: payload.q15,
      q16: payload.q16,
      q17: payload.q17,
      q18: payload.q18,
    };

    return this.http.post<any>(url, requestBody, {
      headers: { 'Content-Type': 'application/json' },
    }).pipe(
      catchError(err => {
        console.error('[SoftSkillsService] POST Error:', err.status, err.message);
        return throwError(() => err);
      })
    );
  }

  saveScenarioResult(evaluation: any): Observable<void> {
    const url = `${this.api}/scenario/save`;
    return this.http.post<void>(url, evaluation);
  }
}
