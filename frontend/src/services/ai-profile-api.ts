import axios from 'axios';
import { AIProfile, AIProfileData, GenerateProfileRequest } from '@/types/ai-profile';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add auth token to requests
apiClient.interceptors.request.use((config) => {
  const token = typeof window !== 'undefined' ? localStorage.getItem('authToken') : null;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export const aiProfileApi = {
  /**
   * Generate AI profile from CV content
   */
  generateProfile: async (request: GenerateProfileRequest): Promise<AIProfile> => {
    const response = await apiClient.post('/api/ai-profile/generate', request);
    return response.data;
  },

  /**
   * Get AI profile by ID
   */
  getProfileById: async (profileId: string): Promise<AIProfile> => {
    const response = await apiClient.get(`/api/ai-profile/${profileId}`);
    return response.data;
  },

  /**
   * Get latest AI profile for current user
   */
  getLatestProfile: async (): Promise<AIProfile> => {
    const response = await apiClient.get('/api/ai-profile/latest');
    return response.data;
  },

  /**
   * Get all AI profiles for current user
   */
  getAllProfiles: async (): Promise<AIProfile[]> => {
    const response = await apiClient.get('/api/ai-profile/all');
    return response.data;
  },

  /**
   * Parse AI attributes JSON string to AIProfileData object
   */
  parseAiAttributes: (aiAttributesJson: string): AIProfileData => {
    try {
      return JSON.parse(aiAttributesJson) as AIProfileData;
    } catch (error) {
      console.error('Error parsing AI attributes:', error);
      throw new Error('Failed to parse AI profile data');
    }
  },
};
