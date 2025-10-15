'use client';

import { MOCK_USERS, MOCK_CREDENTIALS, MockUser } from '@/data/mockUsers';

export interface MockAuthResponse {
  user: MockUser;
  token: string;
  session: {
    access_token: string;
    refresh_token: string;
    expires_at: number;
  };
}

export class MockAuthService {
  private static instance: MockAuthService;
  private currentUser: MockUser | null = null;
  private currentToken: string | null = null;

  static getInstance(): MockAuthService {
    if (!MockAuthService.instance) {
      MockAuthService.instance = new MockAuthService();
    }
    return MockAuthService.instance;
  }

  // Mock sign in
  async signIn(email: string, password: string): Promise<MockAuthResponse> {
    try {
      // Call the backend mock login endpoint
      const response = await fetch('http://localhost:8080/api/mock/auth/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email, password }),
      });

      if (!response.ok) {
        throw new Error('Invalid email or password');
      }

      const data = await response.json();
      
      // Set current user and token
      this.currentUser = data.user;
      this.currentToken = data.token;

      // Store in localStorage for persistence
      localStorage.setItem('mock_user', JSON.stringify(data.user));
      localStorage.setItem('mock_token', data.token);

      return {
        user: data.user,
        token: data.token,
        session: {
          access_token: data.token,
          refresh_token: `refresh_${data.token}`,
          expires_at: Date.now() + (24 * 60 * 60 * 1000), // 24 hours
        },
      };
    } catch (error) {
      throw new Error('Invalid email or password');
    }
  }

  // Mock sign up
  async signUp(email: string, _password: string, firstName?: string, lastName?: string): Promise<MockAuthResponse> {
    // Check if user already exists
    const existingUser = MOCK_USERS.find(u => u.email === email);
    if (existingUser) {
      throw new Error('User already exists');
    }

    // Create new mock user
    const newUser: MockUser = {
      id: `mock-user-${Date.now()}`,
      email,
      firstName: firstName || 'New',
      lastName: lastName || 'User',
      role: 'ADMIN', // Default role
      supabaseId: `mock-supabase-${Date.now()}`,
      avatar: `https://i.pravatar.cc/150?img=${Math.floor(Math.random() * 70) + 1}`,
      phone: '+1-555-0000',
      isActive: true,
    };

    // Generate mock token
    const token = this.generateMockToken(newUser);
    
    // Set current user and token
    this.currentUser = newUser;
    this.currentToken = token;

    // Store in localStorage
    localStorage.setItem('mock_user', JSON.stringify(newUser));
    localStorage.setItem('mock_token', token);

    return {
      user: newUser,
      token,
      session: {
        access_token: token,
        refresh_token: `refresh_${token}`,
        expires_at: Date.now() + (24 * 60 * 60 * 1000),
      },
    };
  }

  // Mock sign out
  async signOut(): Promise<void> {
    this.currentUser = null;
    this.currentToken = null;
    localStorage.removeItem('mock_user');
    localStorage.removeItem('mock_token');
  }

  // Get current user
  getCurrentUser(): MockUser | null {
    if (this.currentUser) {
      return this.currentUser;
    }

    // Try to restore from localStorage
    const storedUser = localStorage.getItem('mock_user');
    if (storedUser) {
      this.currentUser = JSON.parse(storedUser);
      this.currentToken = localStorage.getItem('mock_token');
      return this.currentUser;
    }

    return null;
  }

  // Get current token
  getCurrentToken(): string | null {
    if (this.currentToken) {
      return this.currentToken;
    }

    // Try to restore from localStorage
    const storedToken = localStorage.getItem('mock_token');
    if (storedToken) {
      this.currentToken = storedToken;
      return this.currentToken;
    }

    return null;
  }

  // Check if user is authenticated
  isAuthenticated(): boolean {
    return this.getCurrentUser() !== null && this.getCurrentToken() !== null;
  }

  // Check if user has specific role
  hasRole(role: MockUser['role']): boolean {
    const user = this.getCurrentUser();
    return user?.role === role;
  }

  // Check if user has any of the specified roles
  hasAnyRole(roles: MockUser['role'][]): boolean {
    const user = this.getCurrentUser();
    return user ? roles.includes(user.role) : false;
  }

  // Quick login methods for testing
  async loginAsAdmin(): Promise<MockAuthResponse> {
    return this.signIn(MOCK_CREDENTIALS.admin.email, MOCK_CREDENTIALS.admin.password);
  }

  // Only admin login is available in the simplified version

  // Generate mock JWT token
  private generateMockToken(user: MockUser): string {
    // Generate token in the format expected by backend MockAuthenticationFilter
    // Format: mock-token-{userId}-{timestamp}
    const userId = user.id.replace(/-/g, '');
    const timestamp = Date.now();
    return `mock-token-${userId}-${timestamp}`;
  }

  // Verify mock token
  verifyToken(token: string): MockUser | null {
    try {
      // Check if token is in the expected format: mock-token-{userId}-{timestamp}
      if (!token.startsWith('mock-token-')) {
        return null;
      }

      // Extract userId from token
      const parts = token.split('-');
      if (parts.length < 3) {
        return null;
      }

      // Reconstruct userId (remove dashes from the middle part)
      const userIdPart = parts.slice(2, -1).join(''); // Remove 'mock', 'token', and timestamp
      
      // Find user by ID (need to reconstruct UUID format)
      const user = MOCK_USERS.find(u => u.id.replace(/-/g, '') === userIdPart);
      
      if (!user) {
        return null;
      }

      return user;
    } catch (error) {
      return null;
    }
  }
}

// Export singleton instance
export const mockAuthService = MockAuthService.getInstance();
