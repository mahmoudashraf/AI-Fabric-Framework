// Mock user data for testing
export interface MockUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: 'ADMIN';
  supabaseId: string;
  avatar?: string;
  phone?: string;
  isActive: boolean;
}

export const MOCK_USERS: MockUser[] = [
  // Admin User
  {
    id: '7128999e-576f-42a2-9495-01ecffe9f8a6',
    email: 'admin@easyluxury.com',
    firstName: 'Admin',
    lastName: 'User',
    role: 'ADMIN',
    supabaseId: 'mock-supabase-mock-admin-001',
    avatar: 'https://i.pravatar.cc/150?img=1',
    phone: '+1-555-0101',
    isActive: true,
  },
];

// Helper functions
export const getMockUserByRole = (role: MockUser['role']): MockUser | undefined => {
  return MOCK_USERS.find(user => user.role === role);
};

export const getMockUserByEmail = (email: string): MockUser | undefined => {
  return MOCK_USERS.find(user => user.email === email);
};

// Mock authentication tokens (for testing purposes)
export const MOCK_TOKENS = {
  admin: 'mock-token-admin-001',
};

// Mock login credentials
export const MOCK_CREDENTIALS = {
  admin: { email: 'admin@easyluxury.com', password: 'admin123' },
};
