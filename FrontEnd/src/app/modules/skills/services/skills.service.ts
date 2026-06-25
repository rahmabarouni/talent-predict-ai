import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { SkillResponse } from '../models/skill.model';
// Re-export so components importing SkillResponse from this service still work.
export type { SkillResponse } from '../models/skill.model';

@Injectable({ providedIn: 'root' })
export class SkillsService {
  private http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/skills`;

  getUserSkills(userId: string): Observable<SkillResponse[]> {
    return this.http.get<SkillResponse[]>(`${this.baseUrl}/accounts/${userId}`);
  }

  getById(id: string): Observable<SkillResponse> {
    return this.http.get<SkillResponse>(`${this.baseUrl}/${id}`);
  }

  validateSkill(id: string): Observable<SkillResponse> {
    return this.http.put<SkillResponse>(`${this.baseUrl}/${id}/valider`, {});
  }

  deleteSkill(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
