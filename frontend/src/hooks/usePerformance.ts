'use client';

import { useEffect, useRef, useCallback, useState } from 'react';

// Performance monitoring utilities
export class PerformanceMonitor {
  private static instance: PerformanceMonitor;
  private metrics: Map<string, number> = new Map();

  static getInstance(): PerformanceMonitor {
    if (!PerformanceMonitor.instance) {
      PerformanceMonitor.instance = new PerformanceMonitor();
    }
    return PerformanceMonitor.instance;
  }

  startTiming(label: string): void {
    this.metrics.set(label, performance.now());
  }

  endTiming(label: string): number {
    const startTime = this.metrics.get(label);
    if (!startTime) {
      console.warn(`No start time found for label: ${label}`);
      return 0;
    }

    const duration = performance.now() - startTime;
    this.metrics.delete(label);

    // Log slow operations
    if (duration > 100) {
      console.warn(`Slow operation detected: ${label} took ${duration.toFixed(2)}ms`);
    }

    return duration;
  }

  measureAsync<T>(label: string, fn: () => Promise<T>): Promise<T> {
    this.startTiming(label);
    return fn().finally(() => {
      this.endTiming(label);
    });
  }

  measureSync<T>(label: string, fn: () => T): T {
    this.startTiming(label);
    const result = fn();
    this.endTiming(label);
    return result;
  }
}

// Hook for measuring component render performance
export function useRenderPerformance(componentName: string): void {
  const renderCount = useRef(0);
  const startTime = useRef<number>(0);

  useEffect(() => {
    renderCount.current += 1;
    startTime.current = performance.now();
  });

  useEffect(() => {
    const renderTime = performance.now() - startTime.current;

    if (renderTime > 16) {
      // More than one frame (60fps)
      console.warn(
        `${componentName} render ${renderCount.current} took ${renderTime.toFixed(2)}ms`
      );
    }
  });
}

// Hook for measuring API call performance
export function useApiPerformance(): {
  measureApiCall: <T>(label: string, apiCall: () => Promise<T>) => Promise<T>;
} {
  const monitor = PerformanceMonitor.getInstance();

  const measureApiCall = useCallback(
    async <T>(label: string, apiCall: () => Promise<T>): Promise<T> => {
      return monitor.measureAsync(label, apiCall);
    },
    [monitor]
  );

  return { measureApiCall };
}

// Hook for measuring expensive operations
export function useExpensiveOperation(): {
  measureOperation: <T>(label: string, operation: () => T) => T;
} {
  const monitor = PerformanceMonitor.getInstance();

  const measureOperation = useCallback(
    <T>(label: string, operation: () => T): T => {
      return monitor.measureSync(label, operation);
    },
    [monitor]
  );

  return { measureOperation };
}

// Hook for intersection observer (lazy loading)
export function useIntersectionObserver(
  callback: (entries: IntersectionObserverEntry[]) => void,
  options?: IntersectionObserverInit
): (element: HTMLElement | null) => void {
  const observerRef = useRef<IntersectionObserver | null>(null);

  useEffect(() => {
    observerRef.current = new IntersectionObserver(callback, {
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

  const observe = useCallback((element: HTMLElement | null) => {
    if (observerRef.current && element) {
      observerRef.current.observe(element);
    }
  }, []);

  return observe;
}

// Hook for virtual scrolling optimization
export function useVirtualScrolling<T>(
  items: T[],
  itemHeight: number,
  containerHeight: number,
  overscan: number = 5
): {
  visibleItems: T[];
  startIndex: number;
  endIndex: number;
  totalHeight: number;
  offsetY: number;
} {
  const [scrollTop] = useState(0);

  const visibleCount = Math.ceil(containerHeight / itemHeight);
  const startIndex = Math.max(0, Math.floor(scrollTop / itemHeight) - overscan);
  const endIndex = Math.min(items.length - 1, startIndex + visibleCount + overscan * 2);

  const visibleItems = items.slice(startIndex, endIndex + 1);
  const totalHeight = items.length * itemHeight;
  const offsetY = startIndex * itemHeight;

  return {
    visibleItems,
    startIndex,
    endIndex,
    totalHeight,
    offsetY,
  };
}

// Hook for debounced search
export function useDebouncedSearch<T>(
  searchFn: (query: string) => Promise<T[]>,
  delay: number = 300
): {
  results: T[];
  loading: boolean;
  error: string | null;
  search: (query: string) => void;
  clear: () => void;
} {
  const [results, setResults] = useState<T[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const timeoutRef = useRef<NodeJS.Timeout | null>(null);

  const search = useCallback(
    (query: string) => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }

      if (!query.trim()) {
        setResults([]);
        setLoading(false);
        setError(null);
        return;
      }

      setLoading(true);
      setError(null);

      timeoutRef.current = setTimeout(async () => {
        try {
          const searchResults = await searchFn(query);
          setResults(searchResults);
        } catch (err) {
          setError(err instanceof Error ? err.message : 'Search failed');
          setResults([]);
        } finally {
          setLoading(false);
        }
      }, delay);
    },
    [searchFn, delay]
  );

  const clear = useCallback(() => {
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
    }
    setResults([]);
    setLoading(false);
    setError(null);
  }, []);

  useEffect(() => {
    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    };
  }, []);

  return { results, loading, error, search, clear };
}

// Hook for image lazy loading
export function useLazyImage(
  src: string,
  placeholder?: string
): {
  imageSrc: string;
  loading: boolean;
  error: boolean;
  ref: (element: HTMLImageElement | null) => void;
} {
  const [imageSrc, setImageSrc] = useState(placeholder || '');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const imgRef = useRef<HTMLImageElement | null>(null);

  const observe = useIntersectionObserver(
    useCallback(
      entries => {
        entries.forEach(entry => {
          if (entry.isIntersecting && loading) {
            const img = new Image();
            img.onload = () => {
              setImageSrc(src);
              setLoading(false);
              setError(false);
            };
            img.onerror = () => {
              setError(true);
              setLoading(false);
            };
            img.src = src;
          }
        });
      },
      [src, loading]
    ),
    { threshold: 0.1 }
  );

  const ref = useCallback(
    (element: HTMLImageElement | null) => {
      imgRef.current = element;
      observe(element);
    },
    [observe]
  );

  return { imageSrc, loading, error, ref };
}

// Performance budget monitoring
export function usePerformanceBudget(): {
  checkBudget: () => boolean;
  getMetrics: () => {
    fcp: number;
    lcp: number;
    fid: number;
    cls: number;
  };
} {
  const checkBudget = useCallback((): boolean => {
    if (typeof window === 'undefined' || !('performance' in window)) {
      return true;
    }

    const navigation = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming;

    // Performance budgets
    const budgets = {
      fcp: 1800, // First Contentful Paint
      lcp: 2500, // Largest Contentful Paint
      fid: 100, // First Input Delay
      cls: 0.1, // Cumulative Layout Shift
    };

    // Check if we're within budget
    const fcp = navigation.loadEventEnd - navigation.fetchStart;
    return fcp < budgets.fcp;
  }, []);

  const getMetrics = useCallback(() => {
    if (typeof window === 'undefined') {
      return { fcp: 0, lcp: 0, fid: 0, cls: 0 };
    }

    const navigation = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming;

    return {
      fcp: navigation.loadEventEnd - navigation.fetchStart,
      lcp: 0, // Would need to be measured with LCP API
      fid: 0, // Would need to be measured with FID API
      cls: 0, // Would need to be measured with CLS API
    };
  }, []);

  return { checkBudget, getMetrics };
}
