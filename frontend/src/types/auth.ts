export enum UserRole {
  ADMIN = 'ADMIN',
}

export interface User {
  id: string;
  email: string;
  firstName?: string;
  lastName?: string;
  role: UserRole;
  supabaseId: string;
  createdAt: string;
  updatedAt: string;
}

export interface AuthContextType {
  user: User | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  isLoggedIn: boolean; // Add compatibility with existing code
  signIn: (email: string, password: string) => Promise<void>;
  signUp: (email: string, password: string) => Promise<void>;
  signInWithOAuth: (provider: 'google' | 'apple' | 'facebook') => Promise<void>;
  signOut: () => Promise<void>;
  logout: () => Promise<void>; // Add compatibility alias
  hasRole: (role: UserRole | UserRole[]) => boolean;
  refreshUser: () => Promise<void>;
}
