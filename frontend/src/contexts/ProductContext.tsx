'use client';

import React, { createContext, useContext, useReducer, ReactNode, useRef, useCallback, useEffect } from 'react';

// types
import { ProductsFilter, Address } from 'types/e-commerce';

// project imports
import axios from 'utils/axios';

// ==============| Types |=============//

interface ProductState {
  error: null | string;
  products: any[];
  product: any | null;
  relatedProducts: any[];
  reviews: any[];
  addresses: Address[];
  loading: {
    products: boolean;
    product: boolean;
    relatedProducts: boolean;
    reviews: boolean;
    addresses: boolean;
  };
}

interface ProductAction {
  type: string;
  payload?: any;
}

interface ProductContextType {
  state: ProductState;
  dispatch: React.Dispatch<ProductAction>;
  // Actions with enhanced error handling
  getProducts: () => Promise<void>;
  filterProducts: (filter: ProductsFilter) => Promise<void>;
  getProduct: (id: string | undefined) => Promise<void>;
  getRelatedProducts: (id: string | undefined) => Promise<void>;
  getProductReviews: () => Promise<void>;
  getAddresses: () => Promise<void>;
  addAddress: (address: Address) => Promise<void>;
  editAddress: (address: Address) => Promise<void>;
}

// ==============| PRESERVED DUMMY DATA |=============//

// All hard-coded data preserved exactly as in Redux slice
const INITIAL_PRODUCT_STATE: ProductState = {
  error: null,
  products: [], // Preserved: Product catalog
  product: null, // Preserved: Selected product
  relatedProducts: [], // Preserved: Related products
  reviews: [], // Preserved: Product reviews
  addresses: [], // Preserved: User addresses
  loading: {
    products: false,
    product: false,
    relatedProducts: false,
    reviews: false,
    addresses: false,
  },
};

// ==============|| PRODUCT REDUCER ||=============//

const productReducer = (state: ProductState, action: ProductAction): ProductState => {
  switch (action.type) {
    case 'HAS_ERROR':
      return {
        ...state,
        error: action.payload,
      };
    case 'SET_LOADING':
      return {
        ...state,
        loading: { ...state.loading, [action.payload.key]: action.payload.value },
      };
    case 'GET_PRODUCTS_SUCCESS':
      return {
        ...state,
        products: action.payload,
        loading: { ...state.loading, products: false },
      };
    case 'FILTER_PRODUCTS_SUCCESS':
      return {
        ...state,
        products: action.payload,
        loading: { ...state.loading, products: false },
      };
    case 'GET_PRODUCT_SUCCESS':
      return {
        ...state,
        product: action.payload,
        loading: { ...state.loading, product: false },
      };
    case 'GET_RELATED_PRODUCTS_SUCCESS':
      return {
        ...state,
        relatedProducts: action.payload,
        loading: { ...state.loading, relatedProducts: false },
      };
    case 'GET_PRODUCT_REVIEWS_SUCCESS':
      return {
        ...state,
        reviews: action.payload,
        loading: { ...state.loading, reviews: false },
      };
    case 'GET_ADDRESSES_SUCCESS':
      return {
        ...state,
        addresses: action.payload,
        loading: { ...state.loading, addresses: false },
      };
    case 'ADD_ADDRESS_SUCCESS':
      return {
        ...state,
        addresses: action.payload,
        loading: { ...state.loading, addresses: false },
      };
    case 'EDIT_ADDRESS_SUCCESS':
      return {
        ...state,
        addresses: action.payload,
        loading: { ...state.loading, addresses: false },
      };
    default:
      return state;
  }
};

// ==============|| CONTEXT DEFINITION ||=============//

const ProductContext = createContext<ProductContextType | null>(null);

type ProductProviderProps = {
  children: ReactNode;
};

export const ProductProvider = ({ children }: ProductProviderProps) => {
  const [state, dispatch] = useReducer(productReducer, INITIAL_PRODUCT_STATE);
  
  // Request deduplication and throttling
  const requestCache = useRef<Map<string, Promise<any>>>(new Map());
  const lastRequestTime = useRef<Map<string, number>>(new Map());
  const REQUEST_THROTTLE_MS = 1000; // 1 second throttle
  const MAX_RETRIES = 3;
  const RETRY_DELAY_MS = 1000; // Base delay for exponential backoff

  // Helper function for retry with exponential backoff
  const retryWithBackoff = useCallback(async function<T>(
    requestFn: () => Promise<T>,
    retries = MAX_RETRIES
  ): Promise<T> {
    try {
      return await requestFn();
    } catch (error) {
      if (retries > 0) {
        const delay = RETRY_DELAY_MS * Math.pow(2, MAX_RETRIES - retries);
        await new Promise(resolve => setTimeout(resolve, delay));
        return retryWithBackoff(requestFn, retries - 1);
      }
      throw error;
    }
  }, []);

  // Helper function for throttled requests
  const throttledRequest = useCallback(async function<T>(
    key: string,
    requestFn: () => Promise<T>,
    forceRefresh = false
  ): Promise<T> {
    const now = Date.now();
    const lastTime = lastRequestTime.current.get(key) || 0;
    
    // Check if request is throttled
    if (!forceRefresh && now - lastTime < REQUEST_THROTTLE_MS) {
      // Return cached promise if available
      const cachedPromise = requestCache.current.get(key);
      if (cachedPromise) {
        return cachedPromise;
      }
    }
    
    // Check if request is already in progress
    const existingPromise = requestCache.current.get(key);
    if (existingPromise && !forceRefresh) {
      return existingPromise;
    }
    
    // Create new request
    const promise = requestFn().finally(() => {
      // Clean up cache after request completes
      requestCache.current.delete(key);
      lastRequestTime.current.set(key, now);
    });
    
    // Cache the promise
    requestCache.current.set(key, promise);
    
    return promise;
  }, []);

  // ==============|| ENHANCED API FUNCTIONS WITH THROTTLING |=============//

  // Enterprise Pattern: Enhanced error handling with retry logic and throttling
  const getProducts = useCallback(async (forceRefresh = false) => {
    const cacheKey = 'getProducts';
    
    return throttledRequest(cacheKey, async () => {
      dispatch({ type: 'SET_LOADING', payload: { key: 'products', value: true } });
      try {
        const response = await retryWithBackoff(() => 
          axios.get('/api/products/list')
        );
        dispatch({ type: 'GET_PRODUCTS_SUCCESS', payload: response.data.products });
        return response.data.products;
      } catch (error) {
        dispatch({ type: 'HAS_ERROR', payload: error });
        dispatch({ type: 'SET_LOADING', payload: { key: 'products', value: false } });
        throw error;
      }
    }, forceRefresh);
  }, [throttledRequest]);

  const filterProducts = useCallback(async (filter: ProductsFilter, forceRefresh = false) => {
    const cacheKey = `filterProducts-${JSON.stringify(filter)}`;
    
    return throttledRequest(cacheKey, async () => {
      dispatch({ type: 'SET_LOADING', payload: { key: 'products', value: true } });
      try {
        const response = await retryWithBackoff(() => 
          axios.post('/api/products/filter', { filter })
        );
        dispatch({ type: 'FILTER_PRODUCTS_SUCCESS', payload: response.data });
        return response.data;
      } catch (error) {
        dispatch({ type: 'HAS_ERROR', payload: error });
        dispatch({ type: 'SET_LOADING', payload: { key: 'products', value: false } });
        throw error;
      }
    }, forceRefresh);
  }, [throttledRequest]);

  const getProduct = useCallback(async (id: string | undefined, forceRefresh = false) => {
    if (!id) return;
    
    const cacheKey = `getProduct-${id}`;
    
    return throttledRequest(cacheKey, async () => {
      dispatch({ type: 'SET_LOADING', payload: { key: 'product', value: true } });
      try {
        const response = await retryWithBackoff(() => 
          axios.post('/api/product/details', { id })
        );
        dispatch({ type: 'GET_PRODUCT_SUCCESS', payload: response.data });
        return response.data;
      } catch (error) {
        dispatch({ type: 'HAS_ERROR', payload: error });
        dispatch({ type: 'SET_LOADING', payload: { key: 'product', value: false } });
        throw error;
      }
    }, forceRefresh);
  }, [throttledRequest]);

  const getRelatedProducts = useCallback(async (id: string | undefined, forceRefresh = false) => {
    if (!id) return;
    
    const cacheKey = `getRelatedProducts-${id}`;
    
    return throttledRequest(cacheKey, async () => {
      dispatch({ type: 'SET_LOADING', payload: { key: 'relatedProducts', value: true } });
      try {
        const response = await retryWithBackoff(() => 
          axios.post('/api/product/related', { id })
        );
        dispatch({ type: 'GET_RELATED_PRODUCTS_SUCCESS', payload: response.data });
        return response.data;
      } catch (error) {
        dispatch({ type: 'HAS_ERROR', payload: error });
        dispatch({ type: 'SET_LOADING', payload: { key: 'relatedProducts', value: false } });
        throw error;
      }
    }, forceRefresh);
  }, [throttledRequest]);

  const getProductReviews = useCallback(async (forceRefresh = false) => {
    const cacheKey = 'getProductReviews';
    
    return throttledRequest(cacheKey, async () => {
      dispatch({ type: 'SET_LOADING', payload: { key: 'reviews', value: true } });
      try {
        const response = await retryWithBackoff(() => 
          axios.get('/api/review/list')
        );
        dispatch({ type: 'GET_PRODUCT_REVIEWS_SUCCESS', payload: response.data.productReviews });
        return response.data.productReviews;
      } catch (error) {
        dispatch({ type: 'HAS_ERROR', payload: error });
        dispatch({ type: 'SET_LOADING', payload: { key: 'reviews', value: false } });
        throw error;
      }
    }, forceRefresh);
  }, [throttledRequest]);

  const getAddresses = useCallback(async (forceRefresh = false) => {
    const cacheKey = 'getAddresses';
    
    return throttledRequest(cacheKey, async () => {
      dispatch({ type: 'SET_LOADING', payload: { key: 'addresses', value: true } });
      try {
        const response = await retryWithBackoff(() => 
          axios.get('/api/address/list')
        );
        dispatch({ type: 'GET_ADDRESSES_SUCCESS', payload: response.data.address });
        return response.data.address;
      } catch (error) {
        dispatch({ type: 'HAS_ERROR', payload: error });
        dispatch({ type: 'SET_LOADING', payload: { key: 'addresses', value: false } });
        throw error;
      }
    }, forceRefresh);
  }, [throttledRequest]);

  const addAddress = useCallback(async (address: Address) => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'addresses', value: true } });
    try {
      const response = await retryWithBackoff(() => 
        axios.post('/api/address/new', address)
      );
      dispatch({ type: 'ADD_ADDRESS_SUCCESS', payload: response.data.address });
      return response.data.address;
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'addresses', value: false } });
      throw error;
    }
  }, []);

  const editAddress = useCallback(async (address: Address) => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'addresses', value: true } });
    try {
      const response = await retryWithBackoff(() => 
        axios.post('/api/address/edit', address)
      );
      dispatch({ type: 'EDIT_ADDRESS_SUCCESS', payload: response.data.address });
      return response.data.address;
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'addresses', value: false } });
      throw error;
    }
  }, []);

  // Cleanup effect to clear request cache on unmount
  useEffect(() => {
    return () => {
      requestCache.current.clear();
      lastRequestTime.current.clear();
    };
  }, []);

  const contextValue: ProductContextType = {
    state,
    dispatch,
    getProducts,
    filterProducts,
    getProduct,
    getRelatedProducts,
    getProductReviews,
    getAddresses,
    addAddress,
    editAddress,
  };

  return <ProductContext.Provider value={contextValue}>{children}</ProductContext.Provider>;
};

// ==============|| CUSTOM HOOK ||=============//

export const useProduct = () => {
  const context = useContext(ProductContext);
  if (!context) {
    throw new Error('useProduct must be used within a ProductProvider');
  }
  return context;
};
