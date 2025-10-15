'use client';

import React, {
  memo,
  Suspense,
  lazy,
  ComponentType,
  useMemo,
  useRef,
  useCallback,
  useState,
  useReducer,
  useEffect,
} from 'react';

import ErrorBoundary from './ErrorBoundary';
import LoadingState from './LoadingState';

interface ILazyComponentProps {
  fallback?: React.ComponentType;
  errorFallback?: React.ComponentType<{ error: Error; resetError: () => void }>;
}

/**
 * Higher-order component for lazy loading with error boundaries and loading states
 */
export function withLazyLoading<P extends React.ComponentProps<any> = {}>(
  importFunc: () => Promise<{ default: ComponentType<P> }>,
  options: ILazyComponentProps = {}
) {
  const LazyComponent = lazy(importFunc);

  const WrappedComponent = memo((props: P) => {
    const FallbackComponent = options.fallback || (() => <LoadingState />);
    const ErrorComponent = options.errorFallback;

    return (
      <ErrorBoundary {...(ErrorComponent && { fallback: ErrorComponent })}>
        <Suspense fallback={<FallbackComponent />}>
          <LazyComponent {...(props as any)} />
        </Suspense>
      </ErrorBoundary>
    );
  });

  WrappedComponent.displayName = 'withLazyLoading(Component)';

  return WrappedComponent;
}

/**
 * Hook for creating lazy-loaded components
 */
export function useLazyComponent<P extends React.ComponentProps<any> = {}>(
  importFunc: () => Promise<{ default: ComponentType<P> }>,
  options: ILazyComponentProps = {}
) {
  return useMemo(() => withLazyLoading(importFunc, options), [importFunc, options]);
}

/**
 * Component for conditional rendering with performance optimization
 */
interface IConditionalRenderProps {
  condition: boolean;
  children: React.ReactNode;
  fallback?: React.ReactNode;
  lazy?: boolean;
}

export const ConditionalRender: React.FC<IConditionalRenderProps> = memo(
  ({ condition, children, fallback = null, lazy = false }) => {
    if (!condition) {
      return <>{fallback}</>;
    }

    if (lazy) {
      return <Suspense fallback={<LoadingState />}>{children}</Suspense>;
    }

    return <>{children}</>;
  }
);

ConditionalRender.displayName = 'ConditionalRender';

/**
 * Hook for memoizing expensive calculations
 */
export function useMemoizedValue<T>(factory: () => T, deps: React.DependencyList): T {
  return useMemo(factory, deps);
}

/**
 * Hook for throttling function calls
 */
function useThrottle<T extends (...args: any[]) => any>(callback: T, delay: number): T {
  const lastRun = useRef(Date.now());

  return useCallback(
    ((...args: Parameters<T>) => {
      if (Date.now() - lastRun.current >= delay) {
        callback(...args);
        lastRun.current = Date.now();
      }
    }) as T,
    [callback, delay]
  );
}

/**
 * Hook for batching state updates
 */
export function useBatchedUpdates() {
  const [, forceUpdate] = useReducer(x => x + 1, 0);

  const batchedUpdate = useCallback((updates: (() => void)[]) => {
    updates.forEach(update => update());
  }, []);

  return { batchedUpdate, forceUpdate };
}

/**
 * Component for virtual scrolling
 */
interface IVirtualListProps<T> {
  items: T[];
  itemHeight: number;
  containerHeight: number;
  renderItem: (item: T, index: number) => React.ReactNode;
  overscan?: number;
}

export function VirtualList<T>({
  items,
  itemHeight,
  containerHeight,
  renderItem,
  overscan = 5,
}: IVirtualListProps<T>) {
  const [scrollTop, setScrollTop] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);

  const visibleCount = Math.ceil(containerHeight / itemHeight);
  const startIndex = Math.max(0, Math.floor(scrollTop / itemHeight) - overscan);
  const endIndex = Math.min(items.length - 1, startIndex + visibleCount + overscan * 2);

  const visibleItems = items.slice(startIndex, endIndex + 1);
  const totalHeight = items.length * itemHeight;
  const offsetY = startIndex * itemHeight;

  const handleScroll = useThrottle((e: React.UIEvent<HTMLDivElement>) => {
    setScrollTop(e.currentTarget.scrollTop);
  }, 16); // ~60fps

  return (
    <div
      ref={containerRef}
      style={{
        height: containerHeight,
        overflow: 'auto',
      }}
      onScroll={handleScroll}
    >
      <div style={{ height: totalHeight, position: 'relative' }}>
        <div
          style={{
            transform: `translateY(${offsetY}px)`,
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
          }}
        >
          {visibleItems.map((item, index) => renderItem(item, startIndex + index))}
        </div>
      </div>
    </div>
  );
}

/**
 * Hook for intersection observer with performance optimization
 */
export function useOptimizedIntersectionObserver(
  callback: (entries: IntersectionObserverEntry[]) => void,
  options?: IntersectionObserverInit
) {
  const observerRef = useRef<IntersectionObserver | null>(null);
  const elementsRef = useRef<Set<Element>>(new Set());

  useEffect(() => {
    observerRef.current = new IntersectionObserver(useThrottle(callback, 100), {
      threshold: 0.1,
      rootMargin: '50px',
      ...options,
    });

    return () => {
      if (observerRef.current) {
        observerRef.current.disconnect();
      }
    };
  }, [callback, options]);

  const observe = useCallback((element: Element | null) => {
    if (observerRef.current && element && !elementsRef.current.has(element)) {
      observerRef.current.observe(element);
      elementsRef.current.add(element);
    }
  }, []);

  const unobserve = useCallback((element: Element | null) => {
    if (observerRef.current && element && elementsRef.current.has(element)) {
      observerRef.current.unobserve(element);
      elementsRef.current.delete(element);
    }
  }, []);

  return { observe, unobserve };
}
