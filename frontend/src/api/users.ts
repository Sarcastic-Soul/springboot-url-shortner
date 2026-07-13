import { apiClient } from './client';

export interface UserProfileResponse {
  id: string;
  username: string;
  email: string;
  emailVerified: boolean;
  createdAt: string;
  totalUrls: number;
}

export const userApi = {
  getMe: async (): Promise<UserProfileResponse> => {
    const response = await apiClient.get<UserProfileResponse>('/users/me');
    return response.data;
  }
};
