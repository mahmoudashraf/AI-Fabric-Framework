'use client';

import React, { createContext, useContext, useReducer, ReactNode } from 'react';
import { AIProfile, AIProfileData, AIProfileContextType, PhotoType } from 'types/ai-profile';
import axios from 'utils/axios';

// AI Profile State
interface AIProfileState {
  aiProfile: AIProfile | null;
  aiProfileData: AIProfileData | null;
  isLoading: boolean;
  error: string | null;
}

// AI Profile Actions
type AIProfileAction =
  | { type: 'SET_LOADING'; payload: boolean }
  | { type: 'SET_ERROR'; payload: string | null }
  | { type: 'SET_AI_PROFILE'; payload: AIProfile | null }
  | { type: 'SET_AI_PROFILE_DATA'; payload: AIProfileData | null }
  | { type: 'CLEAR_ERROR' };

// Initial State
const initialState: AIProfileState = {
  aiProfile: null,
  aiProfileData: null,
  isLoading: false,
  error: null,
};

// Reducer
const aiProfileReducer = (state: AIProfileState, action: AIProfileAction): AIProfileState => {
  switch (action.type) {
    case 'SET_LOADING':
      return { ...state, isLoading: action.payload };
    case 'SET_ERROR':
      return { ...state, error: action.payload, isLoading: false };
    case 'SET_AI_PROFILE':
      return { ...state, aiProfile: action.payload, isLoading: false };
    case 'SET_AI_PROFILE_DATA':
      return { ...state, aiProfileData: action.payload, isLoading: false };
    case 'CLEAR_ERROR':
      return { ...state, error: null };
    default:
      return state;
  }
};

// Context
const AIProfileContext = createContext<AIProfileContextType | null>(null);

// Provider
export const AIProfileProvider = ({ children }: { children: ReactNode }) => {
  const [state, dispatch] = useReducer(aiProfileReducer, initialState);

  // Generate AI profile from CV content
  const generateProfile = async (cvContent: string) => {
    try {
      dispatch({ type: 'SET_LOADING', payload: true });
      dispatch({ type: 'CLEAR_ERROR' });

      const response = await axios.post('/api/ai-profile/generate', {
        cvContent,
      });

      const aiProfile = response.data;
      dispatch({ type: 'SET_AI_PROFILE', payload: aiProfile });

      // Parse AI attributes
      if (aiProfile.aiAttributes) {
        const aiProfileData = JSON.parse(aiProfile.aiAttributes);
        dispatch({ type: 'SET_AI_PROFILE_DATA', payload: aiProfileData });
      }
    } catch (error: any) {
      dispatch({ type: 'SET_ERROR', payload: error.message || 'Failed to generate AI profile' });
    }
  };

  // Upload CV file
  const uploadCV = async (file: File) => {
    try {
      dispatch({ type: 'SET_LOADING', payload: true });
      dispatch({ type: 'CLEAR_ERROR' });

      const formData = new FormData();
      formData.append('file', file);
      formData.append('userId', 'mock-user-id'); // Replace with actual user ID

      const response = await axios.post('/api/ai-profile/upload-cv', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      const aiProfile = response.data;
      dispatch({ type: 'SET_AI_PROFILE', payload: aiProfile });

      // Parse AI attributes
      if (aiProfile.aiAttributes) {
        const aiProfileData = JSON.parse(aiProfile.aiAttributes);
        dispatch({ type: 'SET_AI_PROFILE_DATA', payload: aiProfileData });
      }
    } catch (error: any) {
      dispatch({ type: 'SET_ERROR', payload: error.message || 'Failed to upload CV' });
    }
  };

  // Upload photo
  const uploadPhoto = async (file: File, photoType: PhotoType) => {
    try {
      dispatch({ type: 'SET_LOADING', payload: true });
      dispatch({ type: 'CLEAR_ERROR' });

      const formData = new FormData();
      formData.append('file', file);
      formData.append('userId', 'mock-user-id'); // Replace with actual user ID
      formData.append('photoType', photoType);

      const response = await axios.post('/api/ai-profile/upload-photo', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      const aiProfile = response.data;
      dispatch({ type: 'SET_AI_PROFILE', payload: aiProfile });

      // Parse AI attributes
      if (aiProfile.aiAttributes) {
        const aiProfileData = JSON.parse(aiProfile.aiAttributes);
        dispatch({ type: 'SET_AI_PROFILE_DATA', payload: aiProfileData });
      }
    } catch (error: any) {
      dispatch({ type: 'SET_ERROR', payload: error.message || 'Failed to upload photo' });
    }
  };

  // Update profile data
  const updateProfile = async (profileData: Partial<AIProfileData>) => {
    try {
      dispatch({ type: 'SET_LOADING', payload: true });
      dispatch({ type: 'CLEAR_ERROR' });

      if (!state.aiProfile) {
        throw new Error('No AI profile found');
      }

      const updatedData = { ...state.aiProfileData, ...profileData } as AIProfileData;
      const aiAttributes = JSON.stringify(updatedData);

      const response = await axios.put(`/api/ai-profile/${state.aiProfile.id}`, {
        aiAttributes,
      });

      const aiProfile = response.data;
      dispatch({ type: 'SET_AI_PROFILE', payload: aiProfile });
      dispatch({ type: 'SET_AI_PROFILE_DATA', payload: updatedData });
    } catch (error: any) {
      dispatch({ type: 'SET_ERROR', payload: error.message || 'Failed to update profile' });
    }
  };

  // Enrich with photos
  const enrichWithPhotos = async (photoUrls: Record<string, string>) => {
    try {
      dispatch({ type: 'SET_LOADING', payload: true });
      dispatch({ type: 'CLEAR_ERROR' });

      if (!state.aiProfile) {
        throw new Error('No AI profile found');
      }

      const response = await axios.post(`/api/ai-profile/${state.aiProfile.id}/enrich-photos`, photoUrls);

      const aiProfile = response.data;
      dispatch({ type: 'SET_AI_PROFILE', payload: aiProfile });

      // Parse AI attributes
      if (aiProfile.aiAttributes) {
        const aiProfileData = JSON.parse(aiProfile.aiAttributes);
        dispatch({ type: 'SET_AI_PROFILE_DATA', payload: aiProfileData });
      }
    } catch (error: any) {
      dispatch({ type: 'SET_ERROR', payload: error.message || 'Failed to enrich with photos' });
    }
  };

  // Clear error
  const clearError = () => {
    dispatch({ type: 'CLEAR_ERROR' });
  };

  const contextValue: AIProfileContextType = {
    aiProfile: state.aiProfile,
    aiProfileData: state.aiProfileData,
    isLoading: state.isLoading,
    error: state.error,
    generateProfile,
    uploadCV,
    uploadPhoto,
    updateProfile,
    enrichWithPhotos,
    clearError,
  };

  return <AIProfileContext.Provider value={contextValue}>{children}</AIProfileContext.Provider>;
};

// Hook
export const useAIProfile = () => {
  const context = useContext(AIProfileContext);
  if (!context) {
    throw new Error('useAIProfile must be used within an AIProfileProvider');
  }
  return context;
};
