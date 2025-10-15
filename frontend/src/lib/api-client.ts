import axios, { AxiosInstance } from 'axios';
import { supabase } from './supabase';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

// Create axios instance
const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 30000,
});

// Request interceptor to add JWT token
apiClient.interceptors.request.use(
  async (config: any) => {
    try {
      const { data: { session } } = await supabase.auth.getSession();
      
      if (session?.access_token) {
        config.headers.Authorization = `Bearer ${session.access_token}`;
        console.log('🔑 Using Supabase token for API call:', config.url);
      } else {
        console.log('⚠️ No token found for API call:', config.url);
      }
    } catch (error) {
      console.error('Error getting session:', error);
    }
    
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor for error handling
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      // Try to refresh the session
      const { error: refreshError } = await supabase.auth.refreshSession();
      
      if (refreshError) {
        // Refresh failed, redirect to login
        if (typeof window !== 'undefined') {
          window.location.href = '/login';
        }
      } else {
        // Retry the original request
        return apiClient.request(error.config);
      }
    }
    
    return Promise.reject(error);
  }
);

export default apiClient;
