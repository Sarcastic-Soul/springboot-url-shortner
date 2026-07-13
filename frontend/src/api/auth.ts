import { apiClient, type AuthResponse } from './client';

export const authApi = {
  login: async (data: any): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/auth/login', data);
    return response.data;
  },
  register: async (data: any): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/auth/register', data);
    return response.data;
  },
  logout: () => {
    localStorage.removeItem('accessToken');
  }
};
