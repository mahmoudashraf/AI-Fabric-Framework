'use client';

import React, { createContext, useContext, useEffect, useState } from 'react';
import { Session } from '@supabase/supabase-js';
import axios from 'axios';
import { supabase } from '@/lib/supabase';
import { AuthContextType, User, UserRole } from '@/types/auth';

const SupabaseAuthContext = createContext<AuthContextType | undefined>(undefined);

export { SupabaseAuthContext };

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

interface SupabaseAuthProviderProps {
  children: React.ReactNode;
}

export const SupabaseAuthProvider: React.FC<SupabaseAuthProviderProps> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [session, setSession] = useState<Session | null>(null);

  // Fetch user profile from backend
  const fetchUserProfile = async (accessToken: string): Promise<User | null> => {
    try {
      const response = await axios.get(`${API_BASE_URL}/users/me`, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      console.log('✅ User profile fetched successfully:', response.data);
      return response.data;
    } catch (error) {
      console.error('❌ Error fetching user profile:', error);
      return null;
    }
  };

  // Initialize auth state
  useEffect(() => {
    const initAuth = async () => {
      try {
        const { data: { session } } = await supabase.auth.getSession();
        setSession(session);
        
        if (session?.access_token) {
          const userProfile = await fetchUserProfile(session.access_token);
          setUser(userProfile);
        }
      } catch (error) {
        console.error('Error initializing auth:', error);
      } finally {
        setIsLoading(false);
      }
    };

    initAuth();

    // Listen for auth changes
    const { data: { subscription } } = supabase.auth.onAuthStateChange(async (_event: string, session: Session | null) => {
      setSession(session);
      
      if (session?.access_token) {
        const userProfile = await fetchUserProfile(session.access_token);
        setUser(userProfile);
      } else {
        setUser(null);
      }
      
      setIsLoading(false);
    });

    return () => {
      subscription.unsubscribe();
    };
  }, []);
   
  const signIn = async (email: string, password: string): Promise<void> => {
    setIsLoading(true); 
    
    try {
      const { data, error } = await supabase.auth.signInWithPassword({
        email,
        password,
      });

      if (error) throw error;

      if (data.session?.access_token) {
        const userProfile = await fetchUserProfile(data.session.access_token);
        setUser(userProfile);
      }
    } catch (error: any) {
      console.error('Sign in error:', error);
      throw new Error(error.message || 'Failed to sign in');
    } finally {
      setIsLoading(false);
    }
  };

  const signUp = async (email: string, password: string): Promise<void> => {
    setIsLoading(true);
    try {
      const { data, error } = await supabase.auth.signUp({
        email,
        password,
      });

      if (error) {
        console.error('Supabase sign up error:', error);
        throw new Error(error.message || 'Failed to sign up');
      }

      if (data.session?.access_token) {
        const userProfile = await fetchUserProfile(data.session.access_token);
        setUser(userProfile);
      } else {
        // User might need to confirm email
        console.log('User created, email confirmation may be required');
      }
    } catch (error: any) {
      console.error('Sign up error:', error);
      if (error.message === 'Supabase not configured') {
        throw new Error('Supabase authentication is not configured. Please check your environment variables.');
      }
      throw new Error(error.message || 'Failed to sign up');
    } finally {
      setIsLoading(false);
    }
  };

  const signInWithOAuth = async (provider: 'google' | 'apple' | 'facebook' | 'linkedin'): Promise<void> => {
    try {
      const { error } = await supabase.auth.signInWithOAuth({
        provider,
        options: {
          redirectTo: `${window.location.origin}/auth/callback`,
          queryParams: {
            access_type: 'offline',
            prompt: 'consent',
          },
        },
      });

      if (error) throw error;
    } catch (error: any) {
      console.error('OAuth sign in error:', error);
      throw new Error(error.message || 'Failed to sign in with OAuth');
    }
  };

  const signOut = async (): Promise<void> => {
    setIsLoading(true);
    try {
      const { error } = await supabase.auth.signOut();
      if (error) throw error;
      setUser(null);
      setSession(null);
    } catch (error: any) {
      console.error('Sign out error:', error);
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
    if (session?.access_token) {
      const userProfile = await fetchUserProfile(session.access_token);
      setUser(userProfile);
    } else {
      setUser(null);
    }
  };

  const value: AuthContextType = {
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
    <SupabaseAuthContext.Provider value={value}>
      {children}
    </SupabaseAuthContext.Provider>
  );
};

export const useSupabaseAuth = (): AuthContextType => {
  const context = useContext(SupabaseAuthContext);
  if (!context) {
    throw new Error('useSupabaseAuth must be used within a SupabaseAuthProvider');
  }
  return context;
};
