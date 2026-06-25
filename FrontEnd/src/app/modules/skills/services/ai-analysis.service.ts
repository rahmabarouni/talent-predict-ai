import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { CandidateAnalysis } from '../../../core/models/candidate-analysis.model';

@Injectable({
  providedIn: 'root'
})
export class AiAnalysisService {
  private http = inject(HttpClient);
  // Route through Spring Boot proxy (/api/analysis/analyze-candidate)
  // so that JWT auth headers are automatically attached by the interceptor.
  private baseUrl = `${environment.apiUrl}/analysis`;

  analyzeCandidate(
    github: string,
    portfolio?: string,
    cvFile?: File,
    linkedinUrl?: string,
    linkedinContent?: string
  ): Observable<CandidateAnalysis> {
    const formData = new FormData();
    formData.append('github', github);
    if (portfolio) {
      formData.append('portfolio', portfolio);
    }
    if (cvFile) {
      formData.append('cv_file', cvFile, cvFile.name);
    }
    if (linkedinUrl) {
      formData.append('linkedin_url', linkedinUrl);
    }
    if (linkedinContent) {
      formData.append('linkedin_content', linkedinContent);
    }

    return this.http.post<CandidateAnalysis>(
      `${this.baseUrl}/analyze-candidate`,
      formData
    );
  }
}
