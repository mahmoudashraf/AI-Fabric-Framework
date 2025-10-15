import { QueryClient } from '@tanstack/react-query';

// Query client configuration following project guidelines
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Consider cached data fresh for 5 minutes
      staleTime: 5 * 60 * 1000,
      // Cache data for 10 minutes when inactive
      gcTime: 10 * 60 * 1000,
      // Retry failed requests 3 times
      retry: 3,
      // Retry delay with exponential backoff
      retryDelay: attemptIndex => Math.min(1000 * 2 ** attemptIndex, 30000),
      // Refetch on window focus (disable for development)
      refetchOnWindowFocus: process.env.NODE_ENV === 'production',
      // Refetch on reconnect
      refetchOnReconnect: true,
    },
    mutations: {
      // Retry failed mutations once
      retry: 1,
      // Retry delay
      retryDelay: attemptIndex => Math.min(1000 * 2 ** attemptIndex, 30000),
    },
  },
});

// Query keys factory for consistent key management
export const queryKeys = {
  // User-related queries
  user: {
    all: ['user'] as const,
    current: () => [...queryKeys.user.all, 'current'] as const,
    profile: (id: string) => [...queryKeys.user.all, 'profile', id] as const,
    list: () => [...queryKeys.user.all, 'list'] as const,
    listS1: () => [...queryKeys.user.all, 'list-s1'] as const,
    listS2: () => [...queryKeys.user.all, 'list-s2'] as const,
    followers: (userId: string) => [...queryKeys.user.all, 'followers', userId] as const,
    friends: (userId: string) => [...queryKeys.user.all, 'friends', userId] as const,
    gallery: (userId: string) => [...queryKeys.user.all, 'gallery', userId] as const,
    posts: (userId: string) => [...queryKeys.user.all, 'posts', userId] as const,
  },
  
  // Product-related queries
  products: {
    all: ['products'] as const,
    list: (filters?: Record<string, unknown>) => [...queryKeys.products.all, 'list', filters] as const,
    detail: (id: string) => [...queryKeys.products.all, 'detail', id] as const,
    reviews: (id: string) => [...queryKeys.products.detail(id), 'reviews'] as const,
    related: (id: string) => [...queryKeys.products.detail(id), 'related'] as const,
  },
  
  // Cart-related queries
  cart: {
    all: ['cart'] as const,
    current: () => [...queryKeys.cart.all, 'current'] as const,
  },
  
  // Menu-related queries
  menu: {
    all: ['menu'] as const,
    widget: () => [...queryKeys.menu.all, 'widget'] as const,
  },
  
  // Customer-related queries
  customers: {
    all: ['customers'] as const,
    list: (filters?: Record<string, unknown>) => [...queryKeys.customers.all, 'list', filters] as const,
    orders: (customerId: string) => [...queryKeys.customers.all, 'orders', customerId] as const,
  },
  
  // Chat-related queries
  chat: {
    all: ['chat'] as const,
    users: () => [...queryKeys.chat.all, 'users'] as const,
    conversations: (userId: string) => [...queryKeys.chat.all, 'conversations', userId] as const,
    messages: (conversationId: string) => [...queryKeys.chat.all, 'messages', conversationId] as const,
  },
  
  // Calendar-related queries
  calendar: {
    all: ['calendar'] as const,
    events: (filters?: Record<string, unknown>) => [...queryKeys.calendar.all, 'events', filters] as const,
  },
  
  // Kanban-related queries
  kanban: {
    all: ['kanban'] as const,
    board: (boardId: string) => [...queryKeys.kanban.all, 'board', boardId] as const,
    columns: (boardId: string) => [...queryKeys.kanban.board(boardId), 'columns'] as const,
    items: (boardId: string) => [...queryKeys.kanban.board(boardId), 'items'] as const,
  },
} as const;

// Error handling utilities
export const handleQueryError = (error: unknown) => {
  console.error('Query error:', error);
  
  // Return a user-friendly error message
  if (error instanceof Error) {
    return error.message;
  }
  
  return 'An unexpected error occurred';
};

// Cache invalidation utilities
export const queryKeysUtils = {
  // Invalidate all user-related queries
  invalidateUser: () => queryClient.invalidateQueries({ queryKey: queryKeys.user.all }),
  
  // Invalidate all product-related queries
  invalidateProducts: () => queryClient.invalidateQueries({ queryKey: queryKeys.products.all }),
  
  // Invalidate cart queries
  invalidateCart: () => queryClient.invalidateQueries({ queryKey: queryKeys.cart.all }),
  
  // Invalidate menu queries
  invalidateMenu: () => queryClient.invalidateQueries({ queryKey: queryKeys.menu.all }),
};
