import { useMutation } from '@tanstack/react-query';
import axios from 'utils/axios';
import { useCart } from 'contexts/CartContext';
// Using Context API
import { CartProduct } from 'contexts/CartContext';

/**
 * React Query mutation for adding product to cart
 */
export const useAddProductMutation = () => {
  const { addProduct, setError } = useCart();

  return useMutation({
    mutationFn: async ({ product, products }: { product: CartProduct; products: CartProduct[] }) => {
      // noop
      const response = await axios.post('/api/cart/add', { product, products });
      return response.data;
    },
    onSuccess: (data) => {
      addProduct(data.products);
      // noop
    },
    onError: (error) => {
      setError(error instanceof Error ? error.message : 'Failed to add product');
      // noop
    },
  });
};

/**
 * React Query mutation for removing product from cart
 */
export const useRemoveProductMutation = () => {
  const { removeProduct, setError } = useCart();

  return useMutation({
    mutationFn: async ({ id, products }: { id: string | number; products: CartProduct[] }) => {
      // noop
      const response = await axios.post('/api/cart/remove', { id, products });
      return response.data;
    },
    onSuccess: (data) => {
      removeProduct(data.products);
      // noop
    },
    onError: (error) => {
      setError(error instanceof Error ? error.message : 'Failed to remove product');
      // noop
    },
  });
};

/**
 * React Query mutation for updating product quantity in cart
 */
export const useUpdateProductMutation = () => {
  const { updateProduct, setError } = useCart();

  return useMutation({
    mutationFn: async ({ 
      id, 
      quantity, 
      products 
    }: { 
      id: string | number; 
      quantity: number; 
      products: CartProduct[] 
    }) => {
      // noop
      const response = await axios.post('/api/cart/update', { id, quantity, products });
      return response.data;
    },
    onSuccess: (data) => {
      updateProduct(data.products);
      // noop
    },
    onError: (error) => {
      setError(error instanceof Error ? error.message : 'Failed to update product');
      // noop
    },
  });
};

/**
 * Combined cart actions hook
 */
export const useCartActions = () => {
  const addMutation = useAddProductMutation();
  const removeMutation = useRemoveProductMutation();
  const updateMutation = useUpdateProductMutation();
  const cartContext = useCart();

  return {
    // API mutations
    addProduct: (product: CartProduct, products: CartProduct[]) => {
      addMutation.mutate({ product, products });
    },
    removeProduct: (id: string | number, products: CartProduct[]) => {
      removeMutation.mutate({ id, products });
    },
    updateProduct: (id: string | number, quantity: number, products: CartProduct[]) => {
      updateMutation.mutate({ id, quantity, products });
    },

    // Context actions (immediate UI updates)
    setStep: cartContext.setStep,
    setNextStep: cartContext.setNextStep,
    setBackStep: cartContext.setBackStep,
    setBilling: cartContext.setBilling,
    setPayment: cartContext.setPayment,
    setSubtotal: cartContext.setSubtotal,
    setDiscount: cartContext.setDiscount,
    setShipping: cartContext.setShipping,
    setTotal: cartContext.setTotal,
    clearCart: cartContext.clearCart,
    resetCheckout: cartContext.resetCheckout,

    // Loading states
    isLoading: addMutation.isPending || removeMutation.isPending || updateMutation.isPending,
    errors: {
      add: addMutation.error,
      remove: removeMutation.error,
      update: updateMutation.error,
    },
  };
};

// Secondary hook for cart state access
export const useCartState = () => {
  const cartContext = useCart();
  return cartContext;
};

export default useCartActions;
