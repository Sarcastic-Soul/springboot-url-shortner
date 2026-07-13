import { apiClient, type CreateUrlRequest, type UrlResponse, type UrlSummaryResponse, type PageResponse } from './client';

export const urlApi = {
  createUrl: async (data: CreateUrlRequest): Promise<UrlResponse> => {
    const response = await apiClient.post<UrlResponse>('/urls', data);
    return response.data;
  },

  getMyUrls: async (page = 0, size = 10, search = ''): Promise<PageResponse<UrlSummaryResponse>> => {
    const response = await apiClient.get<PageResponse<UrlSummaryResponse>>('/urls', {
      params: { page, size, search }
    });
    return response.data;
  },

  updateUrl: async (id: string, data: any): Promise<UrlSummaryResponse> => {
    const response = await apiClient.patch<UrlSummaryResponse>(`/urls/${id}`, data);
    return response.data;
  },

  deleteUrl: async (id: string): Promise<void> => {
    await apiClient.delete(`/urls/${id}`);
  }
};
