import React, { useState, useEffect, useCallback } from 'react';
import type { ApiResponse } from '@/types/enterprise';

interface DataProviderProps<T> {
  children: (props: {
    data: T | null;
    loading: boolean;
    error: Error | null;
    refetch: () => Promise<void>;
    mutate: (data: T) => void;
  }) => React.ReactNode;
  fetchFn: () => Promise<ApiResponse<T>>;
  initialData?: T | null;
  dependencies?: React.DependencyList;
}

export function DataProvider<T>({
  children,
  fetchFn,
  initialData = null,
  dependencies = []
}: DataProviderProps<T>) {
  const [data, setData] = useState<T | null>(initialData);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<Error | null>(null);

  const fetchData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await fetchFn();
      setData(response.data);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Unknown error'));
    } finally {
      setLoading(false);
    }
  }, dependencies);

  const mutate = useCallback((newData: T) => {
    setData(newData);
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return (
    <>
      {children({
        data,
        loading,
        error,
        refetch: fetchData,
        mutate
      })}
    </>
  );
}


