import { useQuery, useMutation, useQueryClient, type UseQueryResult, type UseMutationResult } from '@tanstack/react-query';
import { queryKeys, queryKeysUtils, handleQueryError } from '../lib/react-query';

// Generic hook for API calls with React Query patterns
export function useReactQuery<TData = unknown, TError extends Error = Error>(
  queryKey: readonly unknown[],
  queryFn: () => Promise<TData>,
  options?: {
    enabled?: boolean;
    staleTime?: number;
    gcTime?: number;
    refetchOnWindowFocus?: boolean;
    refetchOnReconnect?: boolean;
  }
): UseQueryResult<TData, TError> {
  const result = useQuery({
    queryKey,
    queryFn: queryFn as () => Promise<TData>,
    ...(options?.staleTime !== undefined && { staleTime: options.staleTime }),
    ...(options?.gcTime !== undefined && { gcTime: options.gcTime }),
    ...(options?.refetchOnWindowFocus !== undefined && { refetchOnWindowFocus: options.refetchOnWindowFocus }),
    ...(options?.refetchOnReconnect !== undefined && { refetchOnReconnect: options.refetchOnReconnect }),
    ...(options?.enabled !== undefined && { enabled: options.enabled }),
  });
  
  return result as unknown as UseQueryResult<TData, TError>;
}

// Generic mutation hook for API operations
export function useReactMutation<TData = unknown, TError extends Error = Error, TVariables = void>(
  mutationFn: (variables: TVariables) => Promise<TData>,
  options?: {
    onSuccess?: (data: TData, variables: TVariables) => void | Promise<void>;
    onError?: (error: TError, variables: TVariables) => void;
    invalidateQueries?: ReadonlyArray<readonly unknown[]>;
  }
): UseMutationResult<TData, TError, TVariables, unknown> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn,
    onSuccess: async (data, variables) => {
      // Invalidate related queries if specified
      if (options?.invalidateQueries) {
        options.invalidateQueries.forEach((queryKey) => {
          queryClient.invalidateQueries({ queryKey });
        });
      }
      
      // Call custom success handler
      if (options?.onSuccess) {
        await options.onSuccess(data, variables);
      }
    },
    onError: (error, variables) => {
      // Handle error logging
      handleQueryError(error);
      
      // Call custom error handler
      if (options?.onError) {
        options.onError(error as TError, variables);
      }
    },
  });
}

// Specific hooks for common patterns used in the application

// User-related hooks
export function useCurrentUser() {
  return useReactQuery(queryKeys.user.current(), async () => {
    // Placeholder - replace with actual API call
    const response = await fetch('/api/user/current');
    if (!response.ok) throw new Error('Failed to fetch current user');
    return response.json();
  });
}

// Product-related hooks
export function useProducts(filters?: Record<string, unknown>) {
  return useReactQuery(queryKeys.products.list(filters), async () => {
    // Placeholder - replace with actual API call
    const searchParams = new URLSearchParams(filters as Record<string, string>);
    const response = await fetch(`/api/products?${searchParams}`);
    if (!response.ok) throw new Error('Failed to fetch products');
    return response.json();
  });
}

export function useProduct(id: string) {
  return useReactQuery(queryKeys.products.detail(id), async () => {
    const response = await fetch(`/api/products/${id}`);
    if (!response.ok) throw new Error('Failed to fetch product');
    return response.json();
  });
}

// Cart-related hooks
export function useCart() {
  return useReactQuery(queryKeys.cart.current(), async () => {
    const response = await fetch('/api/cart');
    if (!response.ok) throw new Error('Failed to fetch cart');
    return response.json();
  });
}

// Common utility hooks
export function useInvalidateQueries() {
  return {
    invalidateUser: queryKeysUtils.invalidateUser,
    invalidateProducts: queryKeysUtils.invalidateProducts,
    invalidateCart: queryKeysUtils.invalidateCart,
    invalidateMenu: queryKeysUtils.invalidateMenu,
  };
}

// Hook for optimistic updates pattern
export function useOptimisticMutation<TData = unknown, TError extends Error = Error, TVariables = void>(
  mutationFn: (variables: TVariables) => Promise<TData>,
  options: {
    queryKey: readonly unknown[];
    onMutate?: (variables: TVariables) => Promise<TData | undefined>;
    onError?: (error: TError, variables: TVariables, context: unknown) => void;
    onSettled?: () => void;
  }
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn,
    onMutate: async (variables) => {
      // Cancel outgoing refetches
      await queryClient.cancelQueries({ queryKey: options.queryKey });

      // Snapshot previous value
      const previousData = queryClient.getQueryData(options.queryKey);

      // Optimistically update to the new value
      if (options.onMutate) {
        const optimisticData = await options.onMutate(variables);
        if (optimisticData) {
          queryClient.setQueryData(options.queryKey, optimisticData);
        }
      }

      return { previousData };
    },
    onError: (error, variables, context) => {
      // Rollback to previous value on error
      if (context?.previousData) {
        queryClient.setQueryData(options.queryKey, context.previousData);
      }
      
      if (options.onError) {
        options.onError(error as TError, variables, context);
      }
    },
    onSettled: () => {
      // Refetch to ensure server state
      queryClient.invalidateQueries({ queryKey: options.queryKey });
      
      if (options.onSettled) {
        options.onSettled();
      }
    },
  });
}
