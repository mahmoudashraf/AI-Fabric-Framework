/**
 * Test data factory for User entities
 * Provides consistent test data with easy overrides
 */

export interface TestUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: 'admin' | 'user' | 'guest';
  avatar?: string;
  phone?: string;
  createdAt: string;
  updatedAt: string;
  isActive: boolean;
}

export const createTestUser = (overrides: Partial<TestUser> = {}): TestUser => ({
  id: '1',
  email: 'test@example.com',
  firstName: 'Test',
  lastName: 'User',
  role: 'user',
  phone: '+1-555-0123',
  avatar: '/assets/images/users/avatar-1.png',
  createdAt: new Date('2024-01-01').toISOString(),
  updatedAt: new Date('2024-01-01').toISOString(),
  isActive: true,
  ...overrides
});

export const createTestAdmin = (overrides: Partial<TestUser> = {}): TestUser =>
  createTestUser({
    id: 'admin-1',
    email: 'admin@example.com',
    firstName: 'Admin',
    lastName: 'User',
    role: 'admin',
    ...overrides
  });

export const createTestUserList = (count: number = 5): TestUser[] =>
  Array.from({ length: count }, (_, index) =>
    createTestUser({
      id: `user-${index + 1}`,
      email: `user${index + 1}@example.com`,
      firstName: `User${index + 1}`,
      lastName: `Test${index + 1}`
    })
  );

export const createInactiveUser = (overrides: Partial<TestUser> = {}): TestUser =>
  createTestUser({
    id: 'inactive-1',
    email: 'inactive@example.com',
    firstName: 'Inactive',
    lastName: 'User',
    isActive: false,
    ...overrides
  });
