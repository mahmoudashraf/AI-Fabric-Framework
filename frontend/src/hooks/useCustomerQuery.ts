import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { queryKeys } from 'lib/react-query';
import axios from 'utils/axios';
// Using Context API

// Types
export interface Customer {
  id: number;
  name: string;
  email: string;
  location: string;
  orderStatus: string;
  avatar: string;
  [key: string]: unknown;
}

export interface Order {
  id: number;
  orderId: string;
  name: string;
  product: string;
  date: string;
  deliveryDate: string;
  status: string;
  trackingNo: string;
  total: number;
  [key: string]: unknown;
}

export interface CustomersFilter {
  search?: string;
  status?: string;
  sort?: string;
  [key: string]: unknown;
}

/**
 * React Query hooks for customer data management
 */
export const useCustomers = (filters?: CustomersFilter) => {
  return useQuery({
    queryKey: queryKeys.customers.list(filters),
    queryFn: async () => {
      logMigrationActivity('Customers list fetch', 'MARK_CUSTOMER');
      const response = await axios.get('/api/customers/list', { params: filters });
      return response.data;
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

export const useCustomerOrders = (customerId: string | number) => {
  return useQuery({
    queryKey: queryKeys.customers.orders(customerId.toString()),
    queryFn: async () => {
      logMigrationActivity('Customer orders fetch', 'MARK_CUSTOMER', { customerId });
      const response = await axios.get(`/api/customers/orders/${customerId}`);
      return response.data;
    },
    enabled: !!customerId,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

export const useCustomerProducts = () => {
  return useQuery({
    queryKey: ['customer-products'],
    queryFn: async () => {
      logMigrationActivity('Customer products fetch', 'MARK_CUSTOMER');
      const response = await axios.get('/api/customers/products');
      return response.data;
    },
    staleTime: 10 * 60 * 1000, // 10 minutes
  });
};

export const useCustomerProductReviews = () => {
  return useQuery({
    queryKey: ['customer-product-reviews'],
    queryFn: async () => {
      logMigrationActivity('Customer product reviews fetch', 'MARK_CUSTOMER');
      const response = await axios.get('/api/customers/product-reviews');
      return response.data;
    },
    staleTime: 10 * 60 * 1000, // 10 minutes
  });
};

/**
 * Mutations for customer operations
 */
export const useCreateCustomerMutation = () => {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: async (customerData: Partial<Customer>) => {
      logMigrationActivity('Customer creation', 'MARK_CUSTOMER');
      const response = await axios.post('/api/customers', customerData);
      return response.data;
    },
    onSuccess: () => {
      // Invalidate customers list
      queryClient.invalidateQueries({ queryKey: queryKeys.customers.all });
      logMigrationActivity('Customer created successfully', 'MARK_CUSTOMER');
    },
    onError: (error) => {
      logMigrationActivity('Customer creation failed', 'MARK_CUSTOMER', { error })
    },
  });
};

export const useUpdateCustomerMutation = () => {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: async ({ id, customerData }: { id: number; customerData: Partial<Customer> }) => {
      logMigrationActivity('Customer update', 'MARK_CUSTOMER', { customerId: id });
      const response = await axios.put(`/api/customers/${id}`, customerData);
      return response.data;
    },
    onSuccess: (_data, variables) => {
      // Invalidate customers list and specific customer orders
      queryClient.invalidateQueries({ queryKey: queryKeys.customers.all });
      queryClient.invalidateQueries({ queryKey: queryKeys.customers.orders(variables.id.toString()) });
      logMigrationActivity('Customer updated successfully', 'MARK_CUSTOMER');
    },
    onError: (error) => {
      logMigrationActivity('Customer update failed', 'MARK_CUSTOMER', { error });
    },
  });
};

export const useCreateOrderMutation = () => {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: async ({ customerId, orderData }: { customerId: number; orderData: Partial<Order> }) => {
      logMigrationActivity('Order creation', 'MARK_CUSTOMER', { customerId });
      const response = await axios.post(`/api/customers/${customerId}/orders`, orderData);
      return response.data;
    },
    onSuccess: (_data, variables) => {
      // Invalidate customer orders and customers list
      queryClient.invalidateQueries({ queryKey: queryKeys.customers.orders(variables.customerId.toString()) });
      queryClient.invalidateQueries({ queryKey: queryKeys.customers.all });
      logMigrationActivity('Order created successfully', 'MARK_CUSTOMER');
    },
    onError: (error) => {
      logMigrationActivity('Order creation failed', 'MARK_CUSTOMER', { error });
    },
  });
};

/**
 * Combined customer management hook
 */
export const useCustomerManagement = () => {
  const createCustomerMutation = useCreateCustomerMutation();
  const updateCustomerMutation = useUpdateCustomerMutation();
  const createOrderMutation = useCreateOrderMutation();

  return {
    // Customer operations
    createCustomer: (customerData: Partial<Customer>) => {
      createCustomerMutation.mutate(customerData);
    },
    updateCustomer: (id: number, customerData: Partial<Customer>) => {
      updateCustomerMutation.mutate({ id, customerData });
    },
    
    // Order operations
    createOrder: (customerId: number, orderData: Partial<Order>) => {
      createOrderMutation.mutate({ customerId, orderData });
    },
    
    // Loading states
    isCreatingCustomer: createCustomerMutation.isPending,
    isUpdatingCustomer: updateCustomerMutation.isPending,
    isCreatingOrder: createOrderMutation.isPending,
    
    // Error states
    createCustomerError: createCustomerMutation.error,
    updateCustomerError: updateCustomerMutation.error,
    createOrderError: createOrderMutation.error,
  };
};

export default useCustomerManagement;
