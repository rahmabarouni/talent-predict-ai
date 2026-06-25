import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { 
  Formation,
  FormationRequest, 
  FormationResponse,
  FormationReviewNotesRequest,
  MiniTestSubmissionRequest
} from '../models/formation.model';

@Injectable({
  providedIn: 'root'
})
export class FormationService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/formations`;

  createFormation(userId: number | string, request: FormationRequest): Observable<FormationResponse> {
    return this.http.post<FormationResponse>(
      `${this.baseUrl}/utilisateur/${userId}`,
      request
    );
  }

  getUserFormations(userId: number | string): Observable<FormationResponse[]> {
    return this.http.get<FormationResponse[]>(
      `${this.baseUrl}/utilisateur/${userId}`
    );
  }

  getFormationById(formationId: number | string): Observable<FormationResponse> {
    return this.http.get<FormationResponse>(`${this.baseUrl}/${formationId}`);
  }

  getFormationsByStatus(userId: number | string, statut: string): Observable<FormationResponse[]> {
    return this.http.get<FormationResponse[]>(
      `${this.baseUrl}/utilisateur/${userId}/statut/${statut}`
    );
  }

  updateFormationProgress(formationId: number | string, progression: number): Observable<FormationResponse> {
    const params = new HttpParams().set('progression', String(progression));
    return this.http.put<FormationResponse>(
      `${this.baseUrl}/${formationId}/progression`,
      null,
      { params }
    );
  }

  updateFormationStatus(formationId: number | string, statut: string): Observable<FormationResponse> {
    const params = new HttpParams().set('statut', statut);
    return this.http.put<FormationResponse>(
      `${this.baseUrl}/${formationId}/statut`,
      null,
      { params }
    );
  }

  updateFormationReviewNotes(
    formationId: number | string,
    payload: FormationReviewNotesRequest
  ): Observable<FormationResponse> {
    return this.http.put<FormationResponse>(`${this.baseUrl}/${formationId}/review-notes`, payload);
  }

  submitMiniTest(
    formationId: number | string,
    payload: MiniTestSubmissionRequest
  ): Observable<FormationResponse> {
    return this.http.put<FormationResponse>(`${this.baseUrl}/${formationId}/mini-test`, payload);
  }

  uploadCertificate(
    formationId: number | string,
    file: File
  ): Observable<FormationResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<FormationResponse>(`${this.baseUrl}/${formationId}/certificate`, formData);
  }

  deleteFormation(formationId: number | string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${formationId}`);
  }

  getAllFormations(): Observable<FormationResponse[]> {
    return this.http.get<FormationResponse[]>(this.baseUrl);
  }
}
