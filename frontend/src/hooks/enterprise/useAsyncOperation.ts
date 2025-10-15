import { useCallback, useState } from 'react';

export interface AsyncOptions<T> {
  retryCount?: number;
  retryDelay?: number;
  onSuccess?: (data: T) => void;
  onError?: (error: unknown) => void;
}

export function useAsyncOperation<TParams extends unknown[], TResult>(
  fn: (...args: TParams) => Promise<TResult>,
  options: AsyncOptions<TResult> = {}
) {
  const { retryCount = 0, retryDelay = 0, onSuccess, onError } = options;
  const [data, setData] = useState<TResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const delay = (ms: number) => new Promise((res) => setTimeout(res, ms));

  const execute = useCallback(async (...args: TParams) => {
    setLoading(true);
    setError(null);
    let attempts = 0;
    // eslint-disable-next-line no-constant-condition
    while (true) {
      try {
        const result = await fn(...args);
        const unwrapped = (result && typeof result === 'object' && 'data' in (result as any))
          ? (result as any).data
          : result;
        setData((unwrapped === undefined ? null : unwrapped) as TResult | null);
        onSuccess?.(unwrapped);
        return unwrapped as TResult;
      } catch (err) {
        attempts += 1;
        if (attempts > retryCount) {
          setError(err);
          onError?.(err);
          // Do not throw to keep error available to consumers/tests via state
          return undefined as unknown as TResult;
        }
        if (retryDelay > 0) await delay(retryDelay);
      } finally {
        setLoading(false);
      }
    }
  }, [fn, onSuccess, onError, retryCount, retryDelay]);

  const retry = useCallback(async (...args: TParams) => execute(...args), [execute]);

  return { data, loading, error, execute, retry } as const;
}

