// Modern Cart Management Hook
// Pure Context API implementation

import { useCart, type CartProduct, type Address } from 'contexts/CartContext';

/**
 * Modern Cart Management Hook
 * Provides comprehensive cart management using Context API
 */
export const useModernCart = () => {
  const cartContext = useCart();

  return {
    // Cart state (from context.state)
    checkout: cartContext.state.checkout,
    subtotal: cartContext.state.checkout.subtotal,
    shipping: cartContext.state.checkout.shipping,
    discount: cartContext.state.checkout.discount,
    total: cartContext.state.checkout.total,
    
    // Product operations
    addProduct: (products: CartProduct[]) => {
      return cartContext.addProduct(products);
    },
    removeProduct: (products: CartProduct[]) => {
      return cartContext.removeProduct(products);
    },
    updateProduct: (products: CartProduct[]) => {
      return cartContext.updateProduct(products);
    },
    
    // Cart management
    clearCart: () => {
      return cartContext.clearCart();
    },
    setNextStep: () => {
      return cartContext.setNextStep();
    },
    setBackStep: () => {
      return cartContext.setBackStep();
    },
    
    // Address management
    setBillingAddress: (address: Address | null) => {
      return cartContext.setBilling(address);
    },
    // Shipping address is not maintained separately; only shipping cost
    setShippingCharge: (charge: number) => {
      return cartContext.setShipping(charge);
    },
    
    // Cart data
    products: cartContext.state.checkout.products,
    billingAddress: cartContext.state.checkout.billing,
    shippingAddress: null as Address | null,
    shippingCharge: cartContext.state.checkout.shipping, 
  };
};

export default useModernCart;
