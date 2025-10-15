// Modern Product Management Hook
// Pure Context API + React Query implementation

import { useProduct } from 'contexts/ProductContext';
import { useProducts, useProductManagement } from './useProductQuery';
import axios from 'utils/axios';

/**
 * Modern Product Management Hook
 * Provides comprehensive product management using Context API + React Query
 */
export const useModernProduct = () => {
  // Context-based product state
  const productContext = useProduct();
  
  // React Query product operations
  const productsQuery = useProducts();
  const productManagement = useProductManagement();

  return {
    // Context state
    products: productContext.state.products || [],
    selectedProduct: productContext.state.product,
    productReviews: productContext.state.reviews || [],
    loading: productsQuery.isLoading,
    error: productContext.state.error || productsQuery.error,

    // Product operations
    getProducts: productsQuery.refetch,
    getProduct: async (id: string) => {
      const res = await axios.get(`/api/products/detail/${id}`);
      return res.data;
    },

    // Review operations  
    getProductReviews: async (productId: string) => {
      const res = await axios.get(`/api/products/reviews/${productId}`);
      return res.data;
    },
    addProductReview: async (productId: string | number, review: any) => {
      return productManagement.addReview(productId, review);
    },

    // Related products
    getRelatedProducts: async (productId: string) => {
      const res = await axios.get(`/api/products/related/${productId}`);
      return res.data;
    },

    // Address operations
    getAddresses: async () => {
      const res = await axios.get('/api/user/addresses');
      return res.data;
    },
    addAddress: productManagement.addAddress,
    updateAddress: productManagement.editAddress,
    deleteAddress: productManagement.deleteAddress,

    // Filtering and search
    getFilteredProducts: async (filters: any) => {
      const res = await axios.post('/api/products/filter', filters);
      return res.data;
    },

    // Selection management
    selectProduct: (_product: any) => {},

    // React Query state
    queryProducts: productsQuery,
    isQueryLoading: productsQuery.isLoading,
    queryError: productsQuery.error,
    refetchProducts: productsQuery.refetch,
  };
};

export default useModernProduct;
