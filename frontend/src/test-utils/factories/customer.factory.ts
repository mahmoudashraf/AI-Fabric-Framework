/**
 * Test data factory for Customer entities
 * Provides consistent test data with easy overrides
 */

export interface TestCustomer {
  id: string | number;
  name: string;
  email: string;
  phone?: string;
  address?: string;
  city?: string;
  state?: string;
  country?: string;
  location?: string;
  orders?: number;
  date?: string;
  status?: number;
  avatar?: string;
  company?: string;
  createdAt?: string;
  updatedAt?: string;
}

export const createTestCustomer = (overrides: Partial<TestCustomer> = {}): TestCustomer => ({
  id: 1,
  name: 'John Doe',
  email: 'john.doe@example.com',
  phone: '+1-555-0123',
  address: '123 Main St',
  city: 'New York',
  state: 'NY',
  country: 'USA',
  location: 'New York, USA',
  orders: 10,
  date: new Date('2024-01-15').toISOString(),
  status: 1, // 1: Complete, 2: Processing, 3: Confirm
  avatar: '/assets/images/users/avatar-1.png',
  company: 'Test Company Inc.',
  createdAt: new Date('2024-01-01').toISOString(),
  updatedAt: new Date('2024-01-15').toISOString(),
  ...overrides
});

export const createVIPCustomer = (overrides: Partial<TestCustomer> = {}): TestCustomer =>
  createTestCustomer({
    id: 2,
    name: 'Jane Smith',
    email: 'jane.smith@example.com',
    orders: 50,
    status: 1,
    company: 'VIP Corporation',
    ...overrides
  });

export const createNewCustomer = (overrides: Partial<TestCustomer> = {}): TestCustomer =>
  createTestCustomer({
    id: 3,
    name: 'Bob Johnson',
    email: 'bob.johnson@example.com',
    orders: 1,
    status: 3, // Confirm status
    date: new Date().toISOString(),
    ...overrides
  });

export const createTestCustomerList = (count: number = 5): TestCustomer[] =>
  Array.from({ length: count }, (_, index) =>
    createTestCustomer({
      id: index + 1,
      name: `Customer ${index + 1}`,
      email: `customer${index + 1}@example.com`,
      orders: Math.floor(Math.random() * 20) + 1,
      status: (index % 3) + 1, // Rotate through statuses
      location: ['New York, USA', 'Los Angeles, USA', 'Chicago, USA', 'Houston, USA', 'Phoenix, USA'][
        index % 5
      ]
    })
  );

export interface TestOrder {
  id: string | number;
  customerId: string | number;
  name: string;
  company: string;
  type: string;
  qty: number;
  date: string;
  status: number; // 1: Complete, 2: Pending, 3: Processing
}

export const createTestOrder = (overrides: Partial<TestOrder> = {}): TestOrder => ({
  id: 1,
  customerId: 1,
  name: 'John Doe',
  company: 'Test Company',
  type: 'Credit Card',
  qty: 5,
  date: new Date('2024-01-15').toISOString(),
  status: 1,
  ...overrides
});

export const createTestOrderList = (count: number = 5): TestOrder[] =>
  Array.from({ length: count }, (_, index) =>
    createTestOrder({
      id: index + 1,
      customerId: Math.floor(Math.random() * 10) + 1,
      name: `Customer ${index + 1}`,
      company: `Company ${index + 1}`,
      type: ['Credit Card', 'PayPal', 'Bank Transfer'][index % 3],
      qty: Math.floor(Math.random() * 10) + 1,
      status: (index % 3) + 1
    })
  );
