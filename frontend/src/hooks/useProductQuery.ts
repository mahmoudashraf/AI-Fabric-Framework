import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { queryKeys } from 'lib/react-query';
import axios from 'utils/axios';
// Using Context API

// Types
export interface Product {
  id: string | number;
  name: string;
  image: string;
  offerPrice: number;
  salePrice?: number;
  color?: string;
  size?: string;
  [key: string]: unknown;
}

export interface ProductsFilter {
  id?: number;
  search?: string;
  sort?: string;
  sort2?: number;
  sort3?: number;
  sort4?: number;
  sort5?: number;
  filters?: Record<string, unknown>;
  [key: string]: unknown;
}

export interface ProductReview {
  id: number;
  name: string;
  rating: number;
  review: string;
  good: string[];
  bad: string[];
  image: string;
  date: string;
}

export interface Address {
  id?: string | number;
  name: string;
  destination: string;
  building: string;
  street: string;
  city: string;
  state: string;
  country: string;
  post: string;
  phone: string;
  isDefault: boolean;
}

/**
 * React Query hooks for product data management
 */
export const useProducts = (filters?: ProductsFilter) => {
  return useQuery({
    queryKey: queryKeys.products.list(filters),
    queryFn: async () => {
      logMigrationActivity('Products list fetch', 'MARK_PRODUCT');
      const response = await axios.get('/api/products/list', { params: filters });
      return response.data;
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

export const useProduct = (id: string | number) => {
  return useQuery({
    queryKey: queryKeys.products.detail(id.toString()),
    queryFn: async () => {
      logMigrationActivity('Product detail fetch', 'MARK_PRODUCT', { productId: id });
      const response = await axios.get(`/api/products/detail/${id}`);
      return response.data;
    },
    enabled: !!id,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

export const useProductReviews = (productId: string | number) => {
  return useQuery({
    queryKey: queryKeys.products.reviews(productId.toString()),
    queryFn: async () => {
      logMigrationActivity('Product reviews fetch', 'MARK_PRODUCT', { productId });
      const response = await axios.get(`/api/products/reviews/${productId}`);
      return response.data;
    },
    enabled: !!productId,
    staleTime: 10 * 60 * 1000, // 10 minutes - reviews change less frequently
  });
};

export const useRelatedProducts = (productId: string | number) => {
  return useQuery({
    queryKey: queryKeys.products.related(productId.toString()),
    queryFn: async () => {
      logMigrationActivity('Related products fetch', 'MARK_PRODUCT', { productId });
      const response = await axios.get(`/api/products/related/${productId}`);
      return response.data;
    },
    enabled: !!productId,
    staleTime: 15 * 60 * 1000, // 15 minutes - related products change rarely
  });
};

export const useAddresses = () => {
  return useQuery({
    queryKey: ['addresses'],
    queryFn: async () => {
      logMigrationActivity('Addresses list fetch', 'MARK_PRODUCT');
      const response = await axios.get('/api/user/addresses');
      return response.data;
    },
    staleTime: 10 * 60 * 1000, // 10 minutes
  });
};

export const useFilteredProducts = (filters: ProductsFilter) => {
  return useQuery({
    queryKey: queryKeys.products.list({ ...filters, filtered: true }),
    queryFn: async () => {
      logMigrationActivity('Filtered products fetch', 'MARK_PRODUCT', { filters });
      const response = await axios.post('/api/products/filter', filters);
      return response.data;
    },
    enabled: !!filters && (Object.keys(filters).length > 0),
    staleTime: 3 * 60 * 1000, // 3 minutes - filtered results change more frequently
  });
};

/**
 * Mutations for product operations
 */
export const useAddReviewMutation = () => {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: async ({ productId, review }: { productId: string | number; review: Partial<ProductReview> }) => {
      logMigrationActivity('Product review add', 'MARK_PRODUCT', { productId });
      const response = await axios.post(`/api/products/reviews/${productId}`, review);
      return response.data;
    },
    onSuccess: (_data, variables) => {
      // Invalidate products and reviews queries
      queryClient.invalidateQueries({ queryKey: queryKeys.products.reviews(variables.productId.toString()) });
      queryClient.invalidateQueries({ queryKey: queryKeys.products.detail(variables.productId.toString()) });
      logMigrationActivity('Review added successfully', 'MARK_PRODUCT');
    },
    onError: (error) => {
      logMigrationActivity('Review add failed', 'MARK_PRODUCT', { error });
    },
  });
};

export const useAddressMutation = () => {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: async ({ address, action }: { address: Address; action: 'add' | 'edit' | 'delete' }) => {
      logMigrationActivity('Address operation', 'MARK_PRODUCT', { action });
      
      let response;
      switch (action) {
        case 'add':
          response = await axios.post('/api/user/addresses', address);
          break;
        case 'edit':
          response = await axios.put(`/api/user/addresses/${address.id}`, address);
          break;
        case 'delete':
          response = await axios.delete(`/api/user/addresses/${address.id}`);
          break;
        default:
          throw new Error(`Invalid address action: ${action}`);
      }
      
      return response.data;
    },
    onSuccess: () => {
      // Invalidate addresses query
      queryClient.invalidateQueries({ queryKey: ['addresses'] });
      logMigrationActivity('Address operation successful', 'MARK_PRODUCT');
    },
    onError: (error) => {
      logMigrationActivity('Address operation failed', 'MARK_PRODUCT', { error });
    },
  });
};

/**
 * Combined product management hook
 */
export const useProductManagement = () => {
  const addReviewMutation = useAddReviewMutation();
  const addressMutation = useAddressMutation();

  return {
    // Review operations
    addReview: (productId: string | number, review: Partial<ProductReview>) => {
      addReviewMutation.mutate({ productId, review });
    },
    isAddingReview: addReviewMutation.isPending,
    addReviewError: addReviewMutation.error,

    // Address operations
    addAddress: (address: Address) => {
      addressMutation.mutate({ address, action: 'add' });
    },
    editAddress: (address: Address) => {
      addressMutation.mutate({ address, action: 'edit' });
    },
    deleteAddress: (address: Address) => {
      addressMutation.mutate({ address, action: 'delete' });
    },
    isAddressOperationPending: addressMutation.isPending,
    addressOperationError: addressMutation.error,
  };
};

export default useProductManagement;
