'use client';

import { useEffect, useCallback, useRef } from 'react';

// Analytics and monitoring utilities
export class AnalyticsService {
  private static instance: AnalyticsService;
  private events: Map<string, any> = new Map();
  private sessionId: string;

  constructor() {
    this.sessionId = this.generateSessionId();
  }

  static getInstance(): AnalyticsService {
    if (!AnalyticsService.instance) {
      AnalyticsService.instance = new AnalyticsService();
    }
    return AnalyticsService.instance;
  }

  private generateSessionId(): string {
    return `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * Track page views
   */
  trackPageView(page: string, properties?: Record<string, any>): void {
    const event = {
      type: 'page_view',
      page,
      sessionId: this.sessionId,
      timestamp: new Date().toISOString(),
      properties: properties || {},
    };

    this.sendEvent(event);
  }

  /**
   * Track user interactions
   */
  trackEvent(eventName: string, properties?: Record<string, any>): void {
    const event = {
      type: 'event',
      name: eventName,
      sessionId: this.sessionId,
      timestamp: new Date().toISOString(),
      properties: properties || {},
    };

    this.sendEvent(event);
  }

  /**
   * Track performance metrics
   */
  trackPerformance(metricName: string, value: number, properties?: Record<string, any>): void {
    const event = {
      type: 'performance',
      metric: metricName,
      value,
      sessionId: this.sessionId,
      timestamp: new Date().toISOString(),
      properties: properties || {},
    };

    this.sendEvent(event);
  }

  /**
   * Track errors
   */
  trackError(error: Error, context?: Record<string, any>): void {
    const event = {
      type: 'error',
      message: error.message,
      stack: error.stack,
      sessionId: this.sessionId,
      timestamp: new Date().toISOString(),
      context: context || {},
    };

    this.sendEvent(event);
  }

  /**
   * Track user behavior
   */
  trackUserBehavior(action: string, target?: string, properties?: Record<string, any>): void {
    const event = {
      type: 'user_behavior',
      action,
      target,
      sessionId: this.sessionId,
      timestamp: new Date().toISOString(),
      properties: properties || {},
    };

    this.sendEvent(event);
  }

  /**
   * Send event to analytics service
   */
  private sendEvent(event: any): void {
    // In a real application, this would send to your analytics service
    console.log('Analytics Event:', event);

    // Store locally for debugging
    this.events.set(event.timestamp, event);

    // Send to external service (mock)
    this.sendToExternalService(event);
  }

  /**
   * Send to external analytics service
   */
  private async sendToExternalService(event: any): Promise<void> {
    try {
      // Mock API call
      await fetch('/api/analytics', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(event),
      });
    } catch (error) {
      console.error('Failed to send analytics event:', error);
    }
  }

  /**
   * Get session data
   */
  getSessionData(): Record<string, any> {
    return {
      sessionId: this.sessionId,
      startTime: new Date().toISOString(),
      events: Array.from(this.events.values()),
    };
  }
}

// Hook for page view tracking
export function usePageTracking(pageName: string, properties?: Record<string, any>): void {
  const analytics = AnalyticsService.getInstance();

  useEffect(() => {
    analytics.trackPageView(pageName, properties);
  }, [pageName, properties, analytics]);
}

// Hook for event tracking
export function useEventTracking() {
  const analytics = AnalyticsService.getInstance();

  const trackEvent = useCallback(
    (eventName: string, properties?: Record<string, any>) => {
      analytics.trackEvent(eventName, properties);
    },
    [analytics]
  );

  const trackUserBehavior = useCallback(
    (action: string, target?: string, properties?: Record<string, any>) => {
      analytics.trackUserBehavior(action, target, properties);
    },
    [analytics]
  );

  const trackError = useCallback(
    (error: Error, context?: Record<string, any>) => {
      analytics.trackError(error, context);
    },
    [analytics]
  );

  return {
    trackEvent,
    trackUserBehavior,
    trackError,
  };
}

// Hook for performance monitoring
export function usePerformanceMonitoring(): {
  trackPerformance: (metricName: string, value: number, properties?: Record<string, any>) => void;
  measureRenderTime: (componentName: string) => () => void;
  measureAsyncOperation: <T>(operationName: string, operation: () => Promise<T>) => Promise<T>;
} {
  const analytics = AnalyticsService.getInstance();
  const renderTimes = useRef<Map<string, number>>(new Map());

  const trackPerformance = useCallback(
    (metricName: string, value: number, properties?: Record<string, any>) => {
      analytics.trackPerformance(metricName, value, properties);
    },
    [analytics]
  );

  const measureRenderTime = useCallback(
    (componentName: string) => {
      const startTime = performance.now();
      renderTimes.current.set(componentName, startTime);

      return () => {
        const endTime = performance.now();
        const duration = endTime - startTime;
        trackPerformance('component_render_time', duration, { component: componentName });
      };
    },
    [trackPerformance]
  );

  const measureAsyncOperation = useCallback(
    async <T>(operationName: string, operation: () => Promise<T>): Promise<T> => {
      const startTime = performance.now();

      try {
        const result = await operation();
        const endTime = performance.now();
        const duration = endTime - startTime;

        trackPerformance('async_operation_time', duration, { operation: operationName });
        return result;
      } catch (error) {
        const endTime = performance.now();
        const duration = endTime - startTime;

        trackPerformance('async_operation_error', duration, {
          operation: operationName,
          error: error instanceof Error ? error.message : 'Unknown error',
        });
        throw error;
      }
    },
    [trackPerformance]
  );

  return {
    trackPerformance,
    measureRenderTime,
    measureAsyncOperation,
  };
}

// Hook for user session tracking
export function useUserSession(): {
  sessionId: string;
  trackSessionEvent: (event: string, data?: any) => void;
  getSessionData: () => Record<string, any>;
} {
  const analytics = AnalyticsService.getInstance();

  const trackSessionEvent = useCallback(
    (event: string, data?: any) => {
      analytics.trackEvent(`session_${event}`, data);
    },
    [analytics]
  );

  const getSessionData = useCallback(() => {
    return analytics.getSessionData();
  }, [analytics]);

  return {
    sessionId: analytics.getSessionData().sessionId,
    trackSessionEvent,
    getSessionData,
  };
}

// Hook for Web Vitals tracking
export function useWebVitals(): void {
  const analytics = AnalyticsService.getInstance();

  useEffect(() => {
    // Track Core Web Vitals
    const trackWebVital = (metric: any) => {
      analytics.trackPerformance(metric.name, metric.value, {
        id: metric.id,
        delta: metric.delta,
        navigationType: metric.navigationType,
      });
    };

    // Track FCP (First Contentful Paint)
    if ('web-vitals' in window) {
      import('web-vitals').then(({ getCLS, getFID, getFCP, getLCP, getTTFB }) => {
        getCLS(trackWebVital);
        getFID(trackWebVital);
        getFCP(trackWebVital);
        getLCP(trackWebVital);
        getTTFB(trackWebVital);
      });
    }
  }, [analytics]);
}

// Hook for error boundary integration
export function useErrorTracking(): {
  trackError: (error: Error, errorInfo?: any) => void;
} {
  const analytics = AnalyticsService.getInstance();

  const trackError = useCallback(
    (error: Error, errorInfo?: any) => {
      analytics.trackError(error, {
        componentStack: errorInfo?.componentStack,
        errorBoundary: errorInfo?.errorBoundary,
      });
    },
    [analytics]
  );

  return { trackError };
}

// Hook for A/B testing
export function useABTesting(
  testName: string,
  variants: string[]
): {
  variant: string;
  trackConversion: (conversionName: string, value?: number) => void;
} {
  const analytics = AnalyticsService.getInstance();
  const variant = useRef<string>('');

  useEffect(() => {
    // Simple A/B test assignment based on session ID
    const { sessionId } = analytics.getSessionData();
    const hash = sessionId.split('').reduce((a: number, b: string) => {
      a = (a << 5) - a + b.charCodeAt(0);
      return a & a;
    }, 0);

    const index = Math.abs(hash) % variants.length;
    variant.current = variants[index] || variants[0] || '';

    // Track variant assignment
    analytics.trackEvent('ab_test_assignment', {
      testName,
      variant: variant.current,
    });
  }, [testName, variants, analytics]);

  const trackConversion = useCallback(
    (conversionName: string, value?: number) => {
      analytics.trackEvent('ab_test_conversion', {
        testName,
        variant: variant.current,
        conversionName,
        value,
      });
    },
    [testName, analytics]
  );

  return {
    variant: variant.current,
    trackConversion,
  };
}
