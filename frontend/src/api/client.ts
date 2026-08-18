import axios from 'axios';

export const apiClient = axios.create({
  baseURL: '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add a request interceptor to include the auth token
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken');
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
}

export interface UrlResponse {
  shortCode: string;
  shortUrl: string;
  originalUrl: string;
}

export interface UrlSummaryResponse {
  id: string;
  shortCode: string;
  shortUrl: string;
  originalUrl: string;
  title: string | null;
  clickCount: number;
  active: boolean;
  createdAt: string;
  expiresAt: string | null;
}

export interface PageResponse<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

export interface ClickHistoryResponse {
  clickedAt: string;
  ipAddress: string;
  device: string;
  browser: string;
  os: string;
  referer: string;
  userAgent: string;
}

export interface AnalyticsResponse {
  totalClicks: number;
  recentClicks: ClickHistoryResponse[];
}

export interface CreateUrlRequest {
  originalUrl: string;
  customAlias?: string;
  title?: string;
  description?: string;
  tags?: string;
  expiresAt?: string;
  password?: string;
  maxClicks?: number;
}
