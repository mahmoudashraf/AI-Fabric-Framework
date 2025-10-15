// Enterprise Pattern: Customer table hook - wrapper around generic useTableLogic
// Note: This is now a simple wrapper. You can use useTableLogic<Customer> directly.
// Keeping this for backward compatibility.

import { useTableLogic } from './useTableLogic';
import type { Customer } from 'types/customer';

interface UseCustomerTableOptions {
  customers: Customer[];
  searchFields?: string[];
}

/**
 * Custom hook for customer table logic
 * 
 * @deprecated Consider using useTableLogic<Customer> directly for more flexibility
 * @example
 * const table = useTableLogic<Customer>({
 *   data: customers,
 *   searchFields: ['name', 'email', 'location', 'orders']
 * });
 */
export function useCustomerTable({ 
  customers, 
  searchFields = ['name', 'email', 'location', 'orders'] 
}: UseCustomerTableOptions) {
  return useTableLogic<Customer>({
    data: customers,
    searchFields,
    defaultOrderBy: 'name',
    defaultRowsPerPage: 5,
    rowIdentifier: 'name',
  });
}
