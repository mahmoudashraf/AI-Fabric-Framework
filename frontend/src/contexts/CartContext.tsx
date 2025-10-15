'use client';

import React, { createContext, useContext, useReducer, useEffect, ReactNode } from 'react';

// Types for cart functionality
export interface CartProduct {
  id: string | number;
  name: string;
  image: string;
  salePrice?: number;
  offerPrice: number;
  color?: string;
  size?: string;
  quantity: number;
  [key: string]: unknown;
}

export interface Address {
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

export interface PaymentInfo {
  type: 'free' | 'credit_card' | 'paypal';
  method: 'cod' | 'credit' | 'paypal' | 'bank';
  card?: string;
  address?: Address;
}

export interface CartState {
  checkout: {
    step: number;
    products: CartProduct[];
    subtotal: number;
    total: number;
    discount: number;
    shipping: number;
    billing: Address | null;
    payment: PaymentInfo;
  };
  error: string | null;
}

export type CartAction =
  | { type: 'SET_ERROR'; payload: string | null }
  | { type: 'SET_CART_DATA'; payload: CartState }
  | { type: 'ADD_PRODUCT'; payload: CartProduct[] }
  | { type: 'REMOVE_PRODUCT'; payload: CartProduct[] }
  | { type: 'UPDATE_PRODUCT'; payload: CartProduct[] }
  | { type: 'SET_STEP'; payload: number }
  | { type: 'SET_BILLING'; payload: Address | null }
  | { type: 'SET_PAYMENT'; payload: PaymentInfo }
  | { type: 'SET_SUBTOTAL'; payload: number }
  | { type: 'SET_DISCOUNT'; payload: number }
  | { type: 'SET_SHIPPING'; payload: number }
  | { type: 'SET_TOTAL'; payload: number }
  | { type: 'CLEAR_CART' }
  | { type: 'RESET_CHECKOUT' };

// Context type
interface CartContextType {
  state: CartState;
  // Compatibility field for legacy consumers
  checkout?: CartState['checkout'];
  setError: (error: string | null) => void;
  setCartData: (data: CartState) => void;
  addProduct: (product: CartProduct[]) => void;
  removeProduct: (products: CartProduct[]) => void;
  updateProduct: (products: CartProduct[]) => void;
  setStep: (step: number) => void;
  setNextStep: () => void;
  setBackStep: () => void;
  setBilling: (billing: Address | null) => void;
  // Compatibility aliases used by some views
  setBillingAddress?: (billing: Address | null) => void;
  setPayment: (payment: PaymentInfo) => void;
  setSubtotal: (subtotal: number) => void;
  setDiscount: (discount: number) => void;
  setShipping: (shipping: number) => void;
  // Compatibility alias
  setShippingCharge?: (shipping: number) => void;
  setTotal: (total: number) => void;
  clearCart: () => void;
  resetCheckout: () => void;
  // Compatibility alias
  resetCart?: () => void;
}

// Context
export const CartContext = createContext<CartContextType | null>(null);

// Initial state
const initialState: CartState = {
  checkout: {
    step: 0,
    products: [],
    subtotal: 0,
    total: 0,
    discount: 0,
    shipping: 0,
    billing: null,
    payment: {
      type: 'free',
      method: 'cod',
      card: '',
    },
  },
  error: null,
};

// Helper functions
const calculateSubtotal = (products: CartProduct[]): number => {
  return products.reduce((sum, product) => sum + (product.offerPrice * product.quantity), 0);
};

const calculateTotal = (subtotal: number, shipping: number, discount: number): number => {
  return subtotal + shipping - discount;
};

// Reducer
function cartReducer(state: CartState, action: CartAction): CartState {
  switch (action.type) {
    case 'SET_ERROR':
      return { ...state, error: action.payload };

    case 'SET_CART_DATA':
      return action.payload;

    case 'ADD_PRODUCT':
      const addSubtotal = calculateSubtotal(action.payload);
      return {
        ...state,
        checkout: {
          ...state.checkout,
          products: action.payload,
          subtotal: addSubtotal,
          total: calculateTotal(addSubtotal, state.checkout.shipping, state.checkout.discount),
        },
      };

    case 'REMOVE_PRODUCT':
      const removeSubtotal = calculateSubtotal(action.payload);
      return {
        ...state,
        checkout: {
          ...state.checkout,
          products: action.payload,
          subtotal: removeSubtotal,
          total: calculateTotal(removeSubtotal, state.checkout.shipping, state.checkout.discount),
        },
      };

    case 'UPDATE_PRODUCT':
      const updateSubtotal = calculateSubtotal(action.payload);
      return {
        ...state,
        checkout: {
          ...state.checkout,
          products: action.payload,
          subtotal: updateSubtotal,
          total: calculateTotal(updateSubtotal, state.checkout.shipping, state.checkout.discount),
        },
      };

    case 'SET_STEP':
      return {
        ...state,
        checkout: { ...state.checkout, step: action.payload },
      };

    case 'SET_BILLING':
      return {
        ...state,
        checkout: { ...state.checkout, billing: action.payload },
      };

    case 'SET_PAYMENT':
      return {
        ...state,
        checkout: { ...state.checkout, payment: action.payload },
      };

    case 'SET_SUBTOTAL':
      return {
        ...state,
        checkout: {
          ...state.checkout,
          subtotal: action.payload,
          total: calculateTotal(action.payload, state.checkout.shipping, state.checkout.discount),
        },
      };

    case 'SET_DISCOUNT':
      return {
        ...state,
        checkout: {
          ...state.checkout,
          discount: action.payload,
          total: calculateTotal(state.checkout.subtotal, state.checkout.shipping, action.payload),
        },
      };

    case 'SET_SHIPPING':
      return {
        ...state,
        checkout: {
          ...state.checkout,
          shipping: action.payload,
          total: calculateTotal(state.checkout.subtotal, action.payload, state.checkout.discount),
        },
      };

    case 'SET_TOTAL':
      return {
        ...state,
        checkout: { ...state.checkout, total: action.payload },
      };

    case 'CLEAR_CART':
      return {
        ...state,
        checkout: {
          ...state.checkout,
          products: [],
          subtotal: 0,
          total: 0,
          discount: 0,
        },
      };

    case 'RESET_CHECKOUT':
      return {
        ...state,
        checkout: { ...initialState.checkout },
      };

    default:
      return state;
  }
}

// Provider component
export const CartProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [state, dispatch] = useReducer(cartReducer, initialState);

  // Persist cart state to localStorage
  useEffect(() => {
    try {
      const savedCart = localStorage.getItem('kiwi-cart-checkout');
      if (savedCart) {
        const parsed = JSON.parse(savedCart);
        if (parsed.checkout) {
          dispatch({ type: 'SET_CART_DATA', payload: parsed });
        }
      }
    } catch (error) {
      console.warn('Failed to load cart from localStorage:', error);
    }
  }, []);

  // Save to localStorage whenever cart changes
  useEffect(() => {
    try {
      localStorage.setItem('kiwi-cart-checkout', JSON.stringify(state));
    } catch (error) {
      console.warn('Failed to save cart to localStorage:', error);
    }
  }, [state]);

  const value: CartContextType = {
    state,
    checkout: state.checkout,
    setError: (error: string | null) => {
      dispatch({ type: 'SET_ERROR', payload: error });
    },
    setCartData: (data: CartState) => {
      dispatch({ type: 'SET_CART_DATA', payload: data });
    },
    addProduct: (products: CartProduct[]) => {
      dispatch({ type: 'ADD_PRODUCT', payload: products });
    },
    removeProduct: (products: CartProduct[]) => {
      dispatch({ type: 'REMOVE_PRODUCT', payload: products });
    },
    updateProduct: (products: CartProduct[]) => {
      dispatch({ type: 'UPDATE_PRODUCT', payload: products });
    },
    setStep: (step: number) => {
      dispatch({ type: 'SET_STEP', payload: step });
    },
    setNextStep: () => {
      dispatch({ type: 'SET_STEP', payload: Math.min(state.checkout.step + 1, 2) });
    },
    setBackStep: () => {
      dispatch({ type: 'SET_STEP', payload: Math.max(state.checkout.step - 1, 0) });
    },
    setBilling: (billing: Address | null) => {
      dispatch({ type: 'SET_BILLING', payload: billing });
    },
    setBillingAddress: (billing: Address | null) => {
      dispatch({ type: 'SET_BILLING', payload: billing });
    },
    setPayment: (payment: PaymentInfo) => {
      dispatch({ type: 'SET_PAYMENT', payload: payment });
    },
    setSubtotal: (subtotal: number) => {
      dispatch({ type: 'SET_SUBTOTAL', payload: subtotal });
    },
    setDiscount: (discount: number) => {
      dispatch({ type: 'SET_DISCOUNT', payload: discount });
    },
    setShipping: (shipping: number) => {
      dispatch({ type: 'SET_SHIPPING', payload: shipping });
    },
    setShippingCharge: (shipping: number) => {
      dispatch({ type: 'SET_SHIPPING', payload: shipping });
    },
    setTotal: (total: number) => {
      dispatch({ type: 'SET_TOTAL', payload: total });
    },
    clearCart: () => {
      dispatch({ type: 'CLEAR_CART' });
    },
    resetCheckout: () => {
      dispatch({ type: 'RESET_CHECKOUT' });
    },
    resetCart: () => {
      dispatch({ type: 'CLEAR_CART' });
    },
  };

  return (
    <CartContext.Provider value={value}>
      {children}
    </CartContext.Provider>
  );
};

// Hook for using cart context
export const useCart = (): CartContextType => {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart must be used within a CartProvider');
  }
  return context;
};

// Convenience hooks
export const useCartState = () => {
  const { state } = useCart();
  return state;
};

export const useCartActions = () => {
  const { 
    setError,
    setCartData,
    addProduct,
    removeProduct,
    updateProduct,
    setStep,
    setNextStep,
    setBackStep,
    setBilling,
    setPayment,
    setSubtotal,
    setDiscount,
    setShipping,
    setTotal,
    clearCart,
    resetCheckout 
  } = useCart();
  
  return {
    setError,
    setCartData,
    addProduct,
    removeProduct,
    updateProduct,
    setStep,
    setNextStep,
    setBackStep,
    setBilling,
    setPayment,
    setSubtotal,
    setDiscount,
    setShipping,
    setTotal,
    clearCart,
    resetCheckout,
  };
};

export default CartProvider;
