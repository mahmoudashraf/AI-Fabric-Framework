// types
import { KeyedObject } from 'types';
import { Products, Reviews, Address } from 'types/e-commerce';

// ==============================|| TYPES - PRODUCT ||============================== //

export interface ProductCardProps extends KeyedObject {
  id?: string | number;
  color?: string;
  name: string;
  image: string;
  description?: string;
  offerPrice?: number;
  salePrice?: number;
  rating?: number;
}

export interface ProductStateProps {
  products: Products[];
  product: Products | null;
  relatedProducts: Products[];
  reviews: Reviews[];
  addresses: Address[];
  error: object | string | null;
}
