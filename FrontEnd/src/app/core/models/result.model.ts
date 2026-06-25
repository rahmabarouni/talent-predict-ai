/**
 * Test result metadata
 */
export interface TestResult {
  id: number;
  userId: number;
  testType: 'PCM' | 'SKILLS';
  score: number;
  percentile: number;
  completedAt: Date;
  expiresAt?: Date;
  metadata?: Record<string, any>;
}

/**
 * User performance statistics
 */
export interface UserPerformance {
  userId: number;
  totalTestsTaken: number;
  averageScore: number;
  lastTestDate: Date;
  improvementRate: number;
  testHistory: TestResult[];
}

/**
 * Comparison result between users
 */
export interface ComparisonResult {
  userId1: number;
  userId2: number;
  similarity: number; // 0-100
  matchedTraits: string[];
  differenceAreas: string[];
  recommendations: string[];
}
