import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface CampaignApi {
  id: string;
  name: string;
  templateId: string;
  templateName: string;
  channel: string;
  targetGroup: string;
  recipientCount: number;
  status: string;
  scheduledAt: string | null;
  sentCount: number;
  failedCount: number;
  openRate?: number;
  clickRate?: number;
  isPaused?: boolean;
}

export type CampaignUpsertRequest = Omit<CampaignApi, 'id'> & { id?: string };

@Injectable({ providedIn: 'root' })
export class CampaignService {
  private http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/admin/campaigns`;

  listCampaigns(): Observable<CampaignApi[]> {
    return this.http.get<CampaignApi[]>(this.baseUrl);
  }

  getById(id: string): Observable<CampaignApi> {
    return this.http.get<CampaignApi>(`${this.baseUrl}/${id}`);
  }

  saveCampaign(payload: CampaignUpsertRequest): Observable<CampaignApi> {
    return this.http.post<CampaignApi>(this.baseUrl, payload);
  }

  update(id: string, data: CampaignUpsertRequest): Observable<CampaignApi> {
    return this.http.put<CampaignApi>(`${this.baseUrl}/${id}`, data);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
