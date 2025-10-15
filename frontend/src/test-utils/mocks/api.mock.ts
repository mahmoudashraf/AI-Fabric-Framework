/**
 * API Mock utilities
 * Provides mock responses for testing API calls
 */

export interface ApiResponse<T = unknown> {
  data: T;
  message: string;
  success: boolean;
  statusCode: number;
  timestamp: string;
  requestId: string;
}

export const mockApiResponse = <T>(
  data: T,
  success: boolean = true,
  statusCode: number = success ? 200 : 400
): ApiResponse<T> => ({
  data,
  message: success ? 'Operation successful' : 'Operation failed',
  success,
  statusCode,
  timestamp: new Date().toISOString(),
  requestId: `test-${Math.random().toString(36).substring(7)}`
});

export const mockSuccessResponse = <T>(data: T): ApiResponse<T> =>
  mockApiResponse(data, true, 200);

export const mockErrorResponse = (
  message: string = 'Error occurred',
  statusCode: number = 400
): ApiResponse<null> => ({
  data: null,
  message,
  success: false,
  statusCode,
  timestamp: new Date().toISOString(),
  requestId: `test-error-${Math.random().toString(36).substring(7)}`
});

export const mockNotFoundResponse = (): ApiResponse<null> =>
  mockErrorResponse('Resource not found', 404);

export const mockUnauthorizedResponse = (): ApiResponse<null> =>
  mockErrorResponse('Unauthorized', 401);

export const mockForbiddenResponse = (): ApiResponse<null> =>
  mockErrorResponse('Forbidden', 403);

export const mockServerErrorResponse = (): ApiResponse<null> =>
  mockErrorResponse('Internal server error', 500);

/**
 * Mock fetch implementation for testing
 */
export const mockFetch = <T>(
  data: T,
  success: boolean = true,
  delay: number = 100
): Promise<Response> =>
  new Promise((resolve) =>
    setTimeout(
      () =>
        resolve({
          ok: success,
          status: success ? 200 : 400,
          statusText: success ? 'OK' : 'Bad Request',
          json: async () => mockApiResponse(data, success),
          text: async () => JSON.stringify(mockApiResponse(data, success)),
          headers: new Headers({ 'Content-Type': 'application/json' }),
          redirected: false,
          type: 'basic',
          url: 'http://localhost:3000/api/test',
          clone: function() { return this; },
          body: null,
          bodyUsed: false,
          arrayBuffer: async () => new ArrayBuffer(0),
          blob: async () => new Blob(),
          formData: async () => new FormData()
        } as Response),
      delay
    )
  );

/**
 * Mock async operation for testing
 */
export const mockAsyncOperation = <T>(
  data: T,
  delay: number = 100,
  shouldFail: boolean = false
): Promise<T> =>
  new Promise((resolve, reject) =>
    setTimeout(() => {
      if (shouldFail) {
        reject(new Error('Mock operation failed'));
      } else {
        resolve(data);
      }
    }, delay)
  );

/**
 * Mock async operation with retry
 */
export const mockAsyncOperationWithRetry = <T>(
  data: T,
  failCount: number = 2,
  delay: number = 100
): {
  execute: () => Promise<T>;
  callCount: number;
} => {
  let callCount = 0;
  return {
    execute: () =>
      new Promise((resolve, reject) => {
        callCount++;
        setTimeout(() => {
          if (callCount <= failCount) {
            reject(new Error(`Mock operation failed (attempt ${callCount})`));
          } else {
            resolve(data);
          }
        }, delay);
      }),
    get callCount() {
      return callCount;
    }
  };
};

/**
 * Wait for async operations to complete
 */
export const waitForAsync = (ms: number = 0): Promise<void> =>
  new Promise((resolve) => setTimeout(resolve, ms));
