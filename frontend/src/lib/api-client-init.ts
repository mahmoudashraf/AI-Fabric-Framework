import apiClient from './api-client';
import { setupMockInterceptors } from './dev/api-client-mock-interceptor';
import { authConfig } from '@/config/authConfig';

/**
 * Initialize API client with conditional mock support
 * In development mode with mock auth enabled, this adds mock interceptors
 */
export const initializeApiClient = () => {
  if (authConfig.shouldUseMockAuth()) {
    authConfig.log('🎭 [Dev] Initializing API client with mock interceptors');
    setupMockInterceptors(apiClient);
  } else {
    console.log('✅ API client initialized with Supabase authentication');
  }
};

export default apiClient;
