'use client';

import React, { createContext, useContext, useReducer, ReactNode } from 'react';
import axios from 'utils/axios';

// ==============| Types |=============//

interface CustomerState {
  error: null | string;
  customers: any[];
  orders: any[];
  products: any[];
  productreviews: any[];
}

interface CustomerAction {
  type: string;
  payload?: any;
}

interface CustomerContextType {
  state: CustomerState;
  dispatch: React.Dispatch<CustomerAction>;
  getCustomers: () => Promise<void>;
  getOrders: () => Promise<void>;
  getProducts: () => Promise<void>;
  getProductReviews: () => Promise<void>;
}

// ==============| Initial State |=============//

const INITIAL_CUSTOMER_STATE: CustomerState = {
  error: null,
  customers: [],
  orders: [],
  products: [],
  productreviews: [],
};

// ==============|| Reducer ||=============//

const customerReducer = (state: CustomerState, action: CustomerAction): CustomerState => {
  switch (action.type) {
    case 'HAS_ERROR':
      return { ...state, error: action.payload };
    case 'GET_CUSTOMERS_SUCCESS':
      return { ...state, customers: action.payload };
    case 'GET_ORDERS_SUCCESS':
      return { ...state, orders: action.payload };
    case 'GET_PRODUCTS_SUCCESS':
      return { ...state, products: action.payload };
    case 'GET_PRODUCT_REVIEWS_SUCCESS':
      return { ...state, productreviews: action.payload };
    default:
      return state;
  }
};

// ==============|| Context ||=============//

const CustomerContext = createContext<CustomerContextType | null>(null);

type CustomerProviderProps = {
  children: ReactNode;
};

export const CustomerProvider = ({ children }: CustomerProviderProps) => {
  const [state, dispatch] = useReducer(customerReducer, INITIAL_CUSTOMER_STATE);

  const getCustomers = async () => {
    try {
      const response = await axios.get('/api/customer/list');
      dispatch({ type: 'GET_CUSTOMERS_SUCCESS', payload: response.data.customers });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const getOrders = async () => {
    try {
      const response = await axios.get('/api/customer/order/list');
      dispatch({ type: 'GET_ORDERS_SUCCESS', payload: response.data.orders });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const getProducts = async () => {
    try {
      const response = await axios.get('/api/customer/product/list');
      dispatch({ type: 'GET_PRODUCTS_SUCCESS', payload: response.data.products });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const getProductReviews = async () => {
    try {
      const response = await axios.get('/api/customer/product/reviews');
      dispatch({ type: 'GET_PRODUCT_REVIEWS_SUCCESS', payload: response.data.productreviews });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const contextValue: CustomerContextType = {
    state,
    dispatch,
    getCustomers,
    getOrders,
    getProducts,
    getProductReviews,
  };

  return <CustomerContext.Provider value={contextValue}>{children}</CustomerContext.Provider>;
};

export const useCustomer = () => {
  const context = useContext(CustomerContext);
  if (!context) {
    throw new Error('useCustomer must be used within a CustomerProvider');
  }
  return context;
};

export default CustomerProvider;


