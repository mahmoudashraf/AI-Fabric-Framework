/**
 * Test data factory for Product entities
 * Provides consistent test data with easy overrides
 */

export interface TestProduct {
  id: string | number;
  name: string;
  description: string;
  price: number;
  salePrice?: number;
  offerPrice?: number;
  category: string;
  image?: string;
  images?: string[];
  colors?: string[];
  rating?: number;
  isStock: boolean;
  inStock?: boolean;
  quantity?: number;
  sku?: string;
  brand?: string;
  gender?: string;
  created: string;
  createdAt?: string;
  updatedAt?: string;
}

export const createTestProduct = (overrides: Partial<TestProduct> = {}): TestProduct => ({
  id: 1,
  name: 'Test Product',
  description: 'This is a test product description',
  price: 99.99,
  salePrice: 89.99,
  offerPrice: 99.99,
  category: 'electronics',
  image: '/assets/images/e-commerce/prod-1.png',
  images: [
    '/assets/images/e-commerce/prod-1.png',
    '/assets/images/e-commerce/prod-2.png'
  ],
  colors: ['red', 'blue', 'green'],
  rating: 4.5,
  isStock: true,
  inStock: true,
  quantity: 100,
  sku: 'PROD-001',
  brand: 'Test Brand',
  gender: 'unisex',
  created: new Date('2024-01-01').toISOString(),
  createdAt: new Date('2024-01-01').toISOString(),
  updatedAt: new Date('2024-01-01').toISOString(),
  ...overrides
});

export const createOutOfStockProduct = (overrides: Partial<TestProduct> = {}): TestProduct =>
  createTestProduct({
    id: 2,
    name: 'Out of Stock Product',
    isStock: false,
    inStock: false,
    quantity: 0,
    ...overrides
  });

export const createDiscountedProduct = (overrides: Partial<TestProduct> = {}): TestProduct =>
  createTestProduct({
    id: 3,
    name: 'Discounted Product',
    price: 199.99,
    salePrice: 149.99,
    offerPrice: 199.99,
    ...overrides
  });

export const createTestProductList = (count: number = 5): TestProduct[] =>
  Array.from({ length: count }, (_, index) =>
    createTestProduct({
      id: index + 1,
      name: `Product ${index + 1}`,
      description: `Description for product ${index + 1}`,
      price: (index + 1) * 50,
      sku: `PROD-${String(index + 1).padStart(3, '0')}`
    })
  );

export const createProductsByCategory = (category: string, count: number = 3): TestProduct[] =>
  Array.from({ length: count }, (_, index) =>
    createTestProduct({
      id: `${category}-${index + 1}`,
      name: `${category} Product ${index + 1}`,
      category,
      sku: `${category.substring(0, 3).toUpperCase()}-${String(index + 1).padStart(3, '0')}`
    })
  );
