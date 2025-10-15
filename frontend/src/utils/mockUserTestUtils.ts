// Testing utilities for mock users
import { mockAuthService, MockAuthResponse } from '@/services/mockAuthService';
import { MockUser } from '@/data/mockUsers';

export class MockUserTestUtils {
  private static instance: MockUserTestUtils;
  
  static getInstance(): MockUserTestUtils {
    if (!MockUserTestUtils.instance) {
      MockUserTestUtils.instance = new MockUserTestUtils();
    }
    return MockUserTestUtils.instance;
  }

  // Quick setup methods for different test scenarios
  async setupAsAdmin(): Promise<MockAuthResponse> {
    return await mockAuthService.loginAsAdmin();
  }

  async setupAsOwner(): Promise<MockAuthResponse> {
    // Simplified system only supports ADMIN quick login
    return await mockAuthService.loginAsAdmin();
  }

  async setupAsAgencyOwner(): Promise<MockAuthResponse> {
    // Simplified system only supports ADMIN quick login
    return await mockAuthService.loginAsAdmin();
  }

  async setupAsAgencyMember(): Promise<MockAuthResponse> {
    // Simplified system only supports ADMIN quick login
    return await mockAuthService.loginAsAdmin();
  }

  async setupAsTenant(): Promise<MockAuthResponse> {
    // Simplified system only supports ADMIN quick login
    return await mockAuthService.loginAsAdmin();
  }

  // Cleanup after tests
  async cleanup(): Promise<void> {
    await mockAuthService.signOut();
  }

  // Get mock headers for API calls
  getAuthHeaders(): Record<string, string> {
    const token = mockAuthService.getCurrentToken();
    if (!token) {
      throw new Error('No mock user authenticated');
    }
    
    return {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    };
  }

  // Test data generators
  generateMockProperty(ownerId?: string) {
    return {
      title: `Test Property ${Date.now()}`,
      description: 'This is a test property for mock testing',
      price: Math.floor(Math.random() * 1000000) + 100000,
      bedrooms: Math.floor(Math.random() * 5) + 1,
      bathrooms: Math.floor(Math.random() * 3) + 1,
      area: Math.floor(Math.random() * 2000) + 500,
      address: '123 Test Street, Test City, TC 12345',
      propertyType: 'APARTMENT',
      status: 'AVAILABLE',
      ownerId: ownerId || mockAuthService.getCurrentUser()?.id,
    };
  }

  generateMockAgency(ownerId?: string) {
    return {
      name: `Test Agency ${Date.now()}`,
      email: `test-agency-${Date.now()}@example.com`,
      phone: '+1-555-0000',
      address: '123 Test Agency Street, Test City, TC 12345',
      licenseNumber: `TEST-LICENSE-${Date.now()}`,
      ownerId: ownerId || mockAuthService.getCurrentUser()?.id,
    };
  }

  generateMockBooking(propertyId?: string, tenantId?: string) {
    const startDate = new Date();
    startDate.setDate(startDate.getDate() + 30);
    const endDate = new Date(startDate);
    endDate.setDate(endDate.getDate() + 7);

    return {
      propertyId: propertyId || 'mock-property-001',
      tenantId: tenantId || mockAuthService.getCurrentUser()?.id,
      startDate: startDate.toISOString(),
      endDate: endDate.toISOString(),
      totalPrice: Math.floor(Math.random() * 5000) + 500,
      status: 'PENDING',
      notes: 'Test booking for mock testing',
    };
  }

  // Role-based test scenarios
  async testAdminScenario(): Promise<void> {
    await this.setupAsAdmin();
    // Admin can access all endpoints
    console.log('Testing as Admin - Full access');
  }

  async testOwnerScenario(): Promise<void> {
    await this.setupAsOwner();
    // Owner can manage their properties
    console.log('Testing as Owner - Property management');
  }

  async testAgencyScenario(): Promise<void> {
    await this.setupAsAgencyOwner();
    // Agency owner can manage agency and properties
    console.log('Testing as Agency Owner - Agency management');
  }

  async testTenantScenario(): Promise<void> {
    await this.setupAsTenant();
    // Tenant can view and book properties
    console.log('Testing as Tenant - Property viewing and booking');
  }

  // API testing helpers
  async testApiEndpoint(
    endpoint: string, 
    method: 'GET' | 'POST' | 'PUT' | 'DELETE' = 'GET',
    body?: any
  ): Promise<Response> {
    const headers = this.getAuthHeaders();
    
    const options: RequestInit = {
      method,
      headers,
    };

    if (body && method !== 'GET') {
      options.body = JSON.stringify(body);
    }

    return fetch(endpoint, options);
  }

  // Batch testing for all roles
  async testAllRoles(testFunction: (role: MockUser['role']) => Promise<void>): Promise<void> {
    const roles: MockUser['role'][] = ['ADMIN'];
    
    for (const role of roles) {
      console.log(`Testing role: ${role}`);
      
      try {
        switch (role) {
          case 'ADMIN':
            await this.setupAsAdmin();
            break;
          // Only ADMIN role is available
        }
        
        await testFunction(role);
        await this.cleanup();
      } catch (error) {
        console.error(`Error testing role ${role}:`, error);
        await this.cleanup();
      }
    }
  }

  // Mock data validation
  validateMockUser(user: MockUser): boolean {
    return !!(
      user.id &&
      user.email &&
      user.firstName &&
      user.lastName &&
      user.role &&
      user.supabaseId
    );
  }

  validateMockProperty(property: any): boolean {
    return !!(
      property.title &&
      property.description &&
      property.price &&
      property.bedrooms &&
      property.bathrooms &&
      property.address
    );
  }

  validateMockAgency(agency: any): boolean {
    return !!(
      agency.name &&
      agency.email &&
      agency.phone &&
      agency.address &&
      agency.licenseNumber
    );
  }

  // Performance testing helpers
  async measureApiCall(
    endpoint: string,
    method: 'GET' | 'POST' | 'PUT' | 'DELETE' = 'GET',
    body?: any,
    iterations: number = 10
  ): Promise<{ averageTime: number; minTime: number; maxTime: number; errors: number }> {
    const times: number[] = [];
    let errors = 0;

    for (let i = 0; i < iterations; i++) {
      const startTime = performance.now();
      
      try {
        await this.testApiEndpoint(endpoint, method, body);
        const endTime = performance.now();
        times.push(endTime - startTime);
      } catch (error) {
        errors++;
        console.error(`API call failed (iteration ${i + 1}):`, error);
      }
    }

    const averageTime = times.reduce((sum, time) => sum + time, 0) / times.length;
    const minTime = Math.min(...times);
    const maxTime = Math.max(...times);

    return {
      averageTime,
      minTime,
      maxTime,
      errors,
    };
  }
}

// Export singleton instance
export const mockUserTestUtils = MockUserTestUtils.getInstance();

// Convenience functions
export const setupMockUser = (role: MockUser['role']) => {
  switch (role) {
    case 'ADMIN':
      return mockUserTestUtils.setupAsAdmin();
    // Only ADMIN role is available
    default:
      throw new Error(`Unknown role: ${role}`);
  }
};

export const cleanupMockUser = () => mockUserTestUtils.cleanup();
export const getMockAuthHeaders = () => mockUserTestUtils.getAuthHeaders();
export const testApiEndpoint = (endpoint: string, method?: 'GET' | 'POST' | 'PUT' | 'DELETE', body?: any) => 
  mockUserTestUtils.testApiEndpoint(endpoint, method, body);
