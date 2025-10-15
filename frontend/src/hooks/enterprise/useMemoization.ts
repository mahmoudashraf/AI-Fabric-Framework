import { useCallback, useMemo, useRef } from 'react';

export function useMemoizedCallback<T extends (...args: any[]) => any>(callback: T, deps: React.DependencyList): T {
  return useCallback(callback, deps) as T;
}

export function useMemoizedSelector<T, R>(data: T, selector: (data: T) => R, deps: React.DependencyList = []): R {
  return useMemo(() => selector(data), [data, ...deps]);
}

export function useStableReference<T>(value: T): T {
  const ref = useRef<T>(value);
  const prevValue = useRef<T>(value);
  
  if (prevValue.current !== value) {
    ref.current = value;
    prevValue.current = value;
  }
  
  return ref.current;
}

