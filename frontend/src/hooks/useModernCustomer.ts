// Modern Customer Management Hook
// Pure Context API + React Query implementation

import { useCustomer } from 'contexts/CustomerContext';
import { useCustomers, useCustomerManagement } from './useCustomerQuery';
import axios from 'utils/axios';

/**
 * Modern Customer Management Hook
 * Provides comprehensive customer management using Context API + React Query
 */
export const useModernCustomer = () => {
  // Context-based customer state
  const customerContext = useCustomer();
  
  // React Query customer operations
  const customersQuery = useCustomers();
  const customerManagement = useCustomerManagement();

  return {
    // Context state
    customers: customerContext.state.customers || [],
    selectedCustomer: null,
    loading: customersQuery.isLoading,
    error: customerContext.state.error || customersQuery.error,

    // Customer operations
    getCustomers: customersQuery.refetch,
    getCustomer: async (customerId: string) => {
      const res = await axios.get(`/api/customers/${customerId}`);
      return res.data;
    },
    addCustomer: async (customerData: any) => customerManagement.createCustomer(customerData),
    updateCustomer: async (id: number, customerData: any) => customerManagement.updateCustomer(id, customerData),
    deleteCustomer: async (customerId: string) => {
      await axios.delete(`/api/customers/${customerId}`);
    },

    // Orders operations
    getCustomerOrders: async (customerId: string) => {
      const res = await axios.get(`/api/customers/orders/${customerId}`);
      return res.data;
    },

    // Products operations
    getCustomerPurchaseHistory: async (customerId: string) => {
      const res = await axios.get(`/api/customers/${customerId}/purchases`);
      return res.data;
    },

    // Customer actions (selection not managed in context)
    selectCustomer: (_customer: any) => {},

    // Bulk operations
    bulkUpdateCustomers: async (_updates: any[]) => {},

    // React Query state
    queryCustomers: customersQuery,
    isQueryLoading: customersQuery.isLoading,
    queryError: customersQuery.error,
    refetchCustomers: customersQuery.refetch,
  };
};

export default useModernCustomer;
