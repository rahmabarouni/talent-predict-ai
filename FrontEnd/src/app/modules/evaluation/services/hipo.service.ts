import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { HiPoDto } from '../models/hipo.model';

@Injectable({
  providedIn: 'root'
})
export class HiPoService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/evaluation/hipo`;

  getUserHiPo(userId: string): Observable<HiPoDto> {
    return this.http.get<HiPoDto>(`${this.apiUrl}/user/${userId}`);
  }

  getAllUsersHiPo(): Observable<HiPoDto[]> {
    return this.http.get<HiPoDto[]>(`${this.apiUrl}/all`);
  }
}
