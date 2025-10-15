'use client';

import { useEffect } from 'react';
import { authConfig } from '@/config/authConfig';

/**
 * Dev-only hook that intercepts auth context to inject mock authentication
 * This hook should ONLY be used in development mode
 */
export const useMockAuthInterceptor = (
  setUser: (user: any) => void,
  setIsLoading: (loading: boolean) => void
) => {
  useEffect(() => {
    // Only run in development mode with mock auth enabled
    if (!authConfig.shouldUseMockAuth()) {
      return;
    }

    const initMockAuth = async () => {
      try {
        const mockToken = localStorage.getItem('mock_token');
        const mockUser = localStorage.getItem('mock_user');

        if (mockToken && mockUser) {
          try {
            const userData = JSON.parse(mockUser);
            authConfig.log('🔍 Mock auth interceptor: Found mock user', userData);
            setUser(userData);
            setIsLoading(false);
            authConfig.log('✅ Mock authentication intercepted successfully');
          } catch (mockError) {
            authConfig.log('Mock user data invalid, clearing...', mockError);
            localStorage.removeItem('mock_token');
            localStorage.removeItem('mock_user');
          }
        }
      } catch (error) {
        authConfig.log('Error in mock auth interceptor:', error);
      }
    };

    initMockAuth();
  }, [setUser, setIsLoading]);

  // Return mock auth status checker
  return {
    isMockAuthActive: () => {
      const mockToken = localStorage.getItem('mock_token');
      const mockUser = localStorage.getItem('mock_user');
      return authConfig.shouldUseMockAuth() && !!mockToken && !!mockUser;
    },
    clearMockAuth: () => {
      localStorage.removeItem('mock_token');
      localStorage.removeItem('mock_user');
    }
  };
};
