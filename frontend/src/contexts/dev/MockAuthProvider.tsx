'use client';

import React, { useEffect, useState } from 'react';
import { authConfig } from '@/config/authConfig';
import { AuthContextType, User, UserRole } from '@/types/auth';
import axios from 'axios';
import { SupabaseAuthContext } from '@/contexts/SupabaseAuthContext';

/**
 * Dev-only Mock Authentication Provider
 * This component intercepts the SupabaseAuthContext and provides mock authentication
 * ONLY used in development mode when NEXT_PUBLIC_MOCK_AUTH_ENABLED=true
 */

interface MockAuthProviderProps {
  children: React.ReactNode;
}

export const MockAuthProvider: React.FC<MockAuthProviderProps> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  // Initialize mock auth state
  useEffect(() => {
    const initMockAuth = async () => {
      try {
        const mockToken = localStorage.getItem('mock_token');
        const mockUser = localStorage.getItem('mock_user');

        if (mockToken && mockUser) {
          try {
            const userData = JSON.parse(mockUser);
            authConfig.log('🎭 [Dev] Initializing with mock user:', userData);
            setUser(userData);
          } catch (mockError) {
            authConfig.log('[Dev] Mock user data invalid, clearing...', mockError);
            localStorage.removeItem('mock_token');
            localStorage.removeItem('mock_user');
          }
        }
      } catch (error) {
        authConfig.log('Error initializing mock auth:', error);
      } finally {
        setIsLoading(false);
      }
    };

    initMockAuth();
  }, []);

  const signIn = async (email: string, password: string): Promise<void> => {
    setIsLoading(true);
    
    try {
      authConfig.log('🎭 [Dev] Mock sign in:', email);
      const response = await axios.post(`${authConfig.getMockAuthUrl()}/login`, {
        email,
        password,
      });

      if (response.data.token) {
        const mockUser = response.data.user;
        setUser(mockUser);
        localStorage.setItem('mock_token', response.data.token);
        localStorage.setItem('mock_user', JSON.stringify(mockUser));
        authConfig.log('✅ Mock login successful', mockUser.email);
      }
    } catch (error: any) {
      authConfig.log('❌ Mock auth failed', error);
      throw new Error(error.message || 'Failed to sign in');
    } finally {
      setIsLoading(false);
    }
  };

  const signUp = async (): Promise<void> => {
    throw new Error('Mock sign up not implemented in dev mode');
  };

  const signInWithOAuth = async (): Promise<void> => {
    throw new Error('Mock OAuth not implemented in dev mode');
  };

  const signOut = async (): Promise<void> => {
    setIsLoading(true);
    try {
      authConfig.log('🎭 [Dev] Mock sign out');
      
      try {
        await axios.post(`${authConfig.getMockAuthUrl()}/logout`);
      } catch (error) {
        authConfig.log('Mock logout API error (non-critical):', error);
      }
      
      localStorage.removeItem('mock_token');
      localStorage.removeItem('mock_user');
      setUser(null);
    } catch (error: any) {
      authConfig.log('Mock sign out error:', error);
      throw new Error(error.message || 'Failed to sign out');
    } finally {
      setIsLoading(false);
    }
  };

  const hasRole = (role: UserRole | UserRole[]): boolean => {
    if (!user) return false;
    
    if (Array.isArray(role)) {
      return role.includes(user.role);
    }
    
    return user.role === role;
  };

  const refreshUser = async (): Promise<void> => {
    const mockToken = localStorage.getItem('mock_token');
    const mockUser = localStorage.getItem('mock_user');
    
    if (mockToken && mockUser) {
      try {
        const userData = JSON.parse(mockUser);
        authConfig.log('🎭 [Dev] Mock user refreshed:', userData);
        setUser(userData);
      } catch (mockError) {
        authConfig.log('[Dev] Mock user data invalid, clearing...', mockError);
        localStorage.removeItem('mock_token');
        localStorage.removeItem('mock_user');
        setUser(null);
      }
    } else {
      setUser(null);
    }
  };

  const mockAuthValue: AuthContextType = {
    user,
    isLoading,
    isAuthenticated: !!user,
    isLoggedIn: !!user,
    signIn,
    signUp,
    signInWithOAuth,
    signOut,
    logout: signOut,
    hasRole,
    refreshUser,
  };

  return (
    <SupabaseAuthContext.Provider value={mockAuthValue}>
      {children}
    </SupabaseAuthContext.Provider>
  );
};
