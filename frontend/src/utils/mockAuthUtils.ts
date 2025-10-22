/**
 * Development utility to manually set mock authentication token
 * This is for testing purposes only
 */

export const setMockAuthToken = (token: string, user: any) => {
  if (typeof window !== 'undefined') {
    localStorage.setItem('mock_token', token);
    localStorage.setItem('mock_user', JSON.stringify(user));
    
    // Also set axios defaults for immediate use
    const axios = require('axios').default;
    axios.defaults.headers.common.Authorization = `Bearer ${token}`;
    
    console.log('✅ Mock auth token set:', { token, user });
  }
};

export const clearMockAuth = () => {
  if (typeof window !== 'undefined') {
    localStorage.removeItem('mock_token');
    localStorage.removeItem('mock_user');
    
    // Clear axios defaults
    const axios = require('axios').default;
    delete axios.defaults.headers.common.Authorization;
    
    console.log('✅ Mock auth cleared');
  }
};

export const getMockAuthStatus = () => {
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('mock_token');
    const user = localStorage.getItem('mock_user');
    return { token, user: user ? JSON.parse(user) : null };
  }
  return { token: null, user: null };
};
