/**
 * Generic API response wrapper
 * All backend responses should follow this format
 */
export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  error?: string;
  timestamp?: string;
  path?: string;
}

/**
 * Paginated response for list endpoints
 */
export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

/**
 * Error response from backend
 */
export interface ErrorResponse {
  message: string;
  status: number;
  timestamp: string;
  path: string;
  details?: string;
}
