/**
 * Mock handlers for common API endpoints
 * Used with MSW (Mock Service Worker) or direct mocking
 */

import { createTestUser, createTestUserList } from '../factories/user.factory';
import { createTestProduct, createTestProductList } from '../factories/product.factory';
import { createTestCustomer, createTestCustomerList, createTestOrder, createTestOrderList } from '../factories/customer.factory';
import { mockSuccessResponse, mockErrorResponse } from './api.mock';

// Jest type declaration for mock functions
declare const jest: typeof import('@jest/globals')['jest'];

/**
 * Mock handlers for user endpoints
 */
export const userHandlers = {
  getUsers: jest.fn().mockResolvedValue(mockSuccessResponse(createTestUserList(10))),
  getUser: jest.fn((id: string) => Promise.resolve(mockSuccessResponse(createTestUser({ id })))),
  createUser: jest.fn((data: any) =>
    Promise.resolve(mockSuccessResponse(createTestUser(data)))
  ),
  updateUser: jest.fn((id: string, data: any) =>
    Promise.resolve(mockSuccessResponse(createTestUser({ id, ...data })))
  ),
  deleteUser: jest.fn((id: string) =>
    Promise.resolve(mockSuccessResponse({ id, deleted: true }))
  ),
  getUsersFailed: jest.fn().mockRejectedValue(mockErrorResponse('Failed to fetch users'))
};

/**
 * Mock handlers for product endpoints
 */
export const productHandlers = {
  getProducts: jest.fn().mockResolvedValue(mockSuccessResponse(createTestProductList(10))),
  getProduct: jest.fn((id: string | number) =>
    Promise.resolve(mockSuccessResponse(createTestProduct({ id })))
  ),
  createProduct: jest.fn((data: any) =>
    Promise.resolve(mockSuccessResponse(createTestProduct(data)))
  ),
  updateProduct: jest.fn((id: string | number, data: any) =>
    Promise.resolve(mockSuccessResponse(createTestProduct({ id, ...data })))
  ),
  deleteProduct: jest.fn((id: string | number) =>
    Promise.resolve(mockSuccessResponse({ id, deleted: true }))
  ),
  searchProducts: jest.fn((query: string) =>
    Promise.resolve(
      mockSuccessResponse(
        createTestProductList(5).filter((p) =>
          p.name.toLowerCase().includes(query.toLowerCase())
        )
      )
    )
  ),
  filterProducts: jest.fn((filter: any) =>
    Promise.resolve(mockSuccessResponse(createTestProductList(5)))
  )
};

/**
 * Mock handlers for customer endpoints
 */
export const customerHandlers = {
  getCustomers: jest.fn().mockResolvedValue(mockSuccessResponse(createTestCustomerList(10))),
  getCustomer: jest.fn((id: string | number) =>
    Promise.resolve(mockSuccessResponse(createTestCustomer({ id })))
  ),
  createCustomer: jest.fn((data: any) =>
    Promise.resolve(mockSuccessResponse(createTestCustomer(data)))
  ),
  updateCustomer: jest.fn((id: string | number, data: any) =>
    Promise.resolve(mockSuccessResponse(createTestCustomer({ id, ...data })))
  ),
  deleteCustomer: jest.fn((id: string | number) =>
    Promise.resolve(mockSuccessResponse({ id, deleted: true }))
  )
};

/**
 * Mock handlers for order endpoints
 */
export const orderHandlers = {
  getOrders: jest.fn().mockResolvedValue(mockSuccessResponse(createTestOrderList(10))),
  getOrder: jest.fn((id: string | number) =>
    Promise.resolve(mockSuccessResponse(createTestOrder({ id })))
  ),
  createOrder: jest.fn((data: any) =>
    Promise.resolve(mockSuccessResponse(createTestOrder(data)))
  ),
  updateOrder: jest.fn((id: string | number, data: any) =>
    Promise.resolve(mockSuccessResponse(createTestOrder({ id, ...data })))
  ),
  deleteOrder: jest.fn((id: string | number) =>
    Promise.resolve(mockSuccessResponse({ id, deleted: true }))
  )
};

/**
 * Reset all mock handlers
 */
export const resetAllHandlers = () => {
  Object.values(userHandlers).forEach((handler) => {
    if (jest.isMockFunction(handler)) {
      handler.mockClear();
    }
  });
  Object.values(productHandlers).forEach((handler) => {
    if (jest.isMockFunction(handler)) {
      handler.mockClear();
    }
  });
  Object.values(customerHandlers).forEach((handler) => {
    if (jest.isMockFunction(handler)) {
      handler.mockClear();
    }
  });
  Object.values(orderHandlers).forEach((handler) => {
    if (jest.isMockFunction(handler)) {
      handler.mockClear();
    }
  });
};

/**
 * Create a mock context for testing
 */
export const createMockContext = <T>(initialState: T, actions: Record<string, jest.Mock> = {}) => ({
  state: initialState,
  ...actions
});
