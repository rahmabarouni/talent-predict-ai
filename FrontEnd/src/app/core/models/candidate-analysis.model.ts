/**
 * Models for the AI candidate analysis response from the Python AI service.
 */

export interface CandidateSkill {
  name: string;
  level: 'Beginner' | 'Intermediate' | 'Advanced' | 'Expert';
  score: number;
  sources: string[];
}

export interface JobMatch {
  profile: string;
  score: number;
  matched_skills: string[];
  missing_skills: string[];
  recommendations: string[];
}

export interface CandidateAnalysis {
  candidate: string;
  data_sources: string[];
  summary: string;
  skills: CandidateSkill[];
  experience_score: number;
  repositories_analyzed: number;
  top_languages: string[];
  linkedin_analysis?: string;
  job_match: JobMatch;
  raw_analysis?: string;
  cv_warning?: string;
  error?: string;
}
