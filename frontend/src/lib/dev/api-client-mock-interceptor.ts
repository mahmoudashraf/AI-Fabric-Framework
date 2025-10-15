import { AxiosInstance } from 'axios';
import { authConfig } from '@/config/authConfig';

/**
 * Dev-only API client mock interceptor
 * Injects mock token into API requests when mock auth is enabled
 * This should ONLY be used in development mode
 */
export const setupMockInterceptors = (apiClient: AxiosInstance) => {
  // Only setup in development mode with mock auth enabled
  if (!authConfig.shouldUseMockAuth()) {
    return;
  }

  // Request interceptor to inject mock token
  const requestInterceptor = apiClient.interceptors.request.use(
    async (config: any) => {
      try {
        // Check for mock token first (development mode)
        const mockToken = localStorage.getItem('mock_token');
        if (mockToken && authConfig.shouldUseMockAuth()) {
          config.headers.Authorization = `Bearer ${mockToken}`;
          authConfig.log('🔑 Mock interceptor: Using mock token for API call:', config.url);
          return config;
        }
      } catch (error) {
        authConfig.log('Error in mock request interceptor:', error);
      }

      return config;
    },
    (error) => {
      return Promise.reject(error);
    }
  );

  // Response interceptor for mock auth error handling
  const responseInterceptor = apiClient.interceptors.response.use(
    (response) => response,
    async (error) => {
      if (error.response?.status === 401) {
        // Check if we're using mock authentication
        const mockToken = localStorage.getItem('mock_token');
        if (mockToken && authConfig.shouldUseMockAuth()) {
          // For mock auth, just redirect to login (no refresh needed)
          authConfig.log('Mock auth 401 error, clearing mock credentials');
          if (typeof window !== 'undefined') {
            localStorage.removeItem('mock_token');
            localStorage.removeItem('mock_user');
            window.location.href = '/login';
          }
          return Promise.reject(error);
        }
      }

      return Promise.reject(error);
    }
  );

  // Return cleanup function
  return () => {
    apiClient.interceptors.request.eject(requestInterceptor);
    apiClient.interceptors.response.eject(responseInterceptor);
  };
};
