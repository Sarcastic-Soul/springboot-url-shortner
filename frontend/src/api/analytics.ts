import { apiClient, type AnalyticsResponse } from './client';

export const analyticsApi = {
  getAnalytics: async (urlId: string): Promise<AnalyticsResponse> => {
    const response = await apiClient.get<AnalyticsResponse>(`/analytics/${urlId}`);
    return response.data;
  }
};
