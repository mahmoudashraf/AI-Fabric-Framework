import { renderHook, act, waitFor } from '@testing-library/react';
import { useAsyncOperation } from '../useAsyncOperation';

describe('useAsyncOperation', () => {
  describe('Basic functionality', () => {
    it('initializes with correct default values', () => {
      const mockFn = jest.fn().mockResolvedValue('success');
      const { result } = renderHook(() => useAsyncOperation(mockFn));

      expect(result.current.data).toBeNull();
      expect(result.current.loading).toBe(false);
      expect(result.current.error).toBeNull();
      expect(typeof result.current.execute).toBe('function');
      expect(typeof result.current.retry).toBe('function');
    });

    it('executes async operation successfully', async () => {
      const mockFn = jest.fn().mockResolvedValue('success data');
      const { result } = renderHook(() => useAsyncOperation(mockFn));

      let returnValue;
      await act(async () => {
        returnValue = await result.current.execute();
      });

      expect(mockFn).toHaveBeenCalledTimes(1);
      expect(returnValue).toBe('success data');
      expect(result.current.data).toBe('success data');
      expect(result.current.error).toBeNull();
      expect(result.current.loading).toBe(false);
    });

    it('handles async operation failure', async () => {
      const mockError = new Error('Test error');
      const mockFn = jest.fn().mockRejectedValue(mockError);
      const { result } = renderHook(() => useAsyncOperation(mockFn));

      try {
        await act(async () => {
          await result.current.execute();
        });
      } catch (error) {
        // Expected to throw
      }

      await waitFor(() => {
        expect(result.current.error).toBe(mockError);
        expect(result.current.data).toBeNull();
        expect(result.current.loading).toBe(false);
      });
    });

    it('sets loading state during execution', async () => {
      let resolvePromise: (value: string) => void;
      const promise = new Promise<string>((resolve) => {
        resolvePromise = resolve;
      });
      const mockFn = jest.fn().mockReturnValue(promise);
      
      const { result } = renderHook(() => useAsyncOperation(mockFn));

      act(() => {
        result.current.execute();
      });

      // Should be loading
      expect(result.current.loading).toBe(true);

      await act(async () => {
        resolvePromise!('done');
        await promise;
      });

      // Should not be loading anymore
      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });
    });
  });

  describe('Retry logic', () => {
    it('retries specified number of times on failure', async () => {
      const mockFn = jest.fn().mockRejectedValue(new Error('Test error'));
      const { result } = renderHook(() =>
        useAsyncOperation(mockFn, {
          retryCount: 3,
          retryDelay: 10
        })
      );

      try {
        await act(async () => {
          await result.current.execute();
        });
      } catch (error) {
        // Expected to throw after all retries
      }

      await waitFor(() => {
        // Initial call + 3 retries = 4 total calls
        expect(mockFn).toHaveBeenCalledTimes(4);
      });
    });

    it('succeeds on retry', async () => {
      const mockFn = jest
        .fn()
        .mockRejectedValueOnce(new Error('First fail'))
        .mockRejectedValueOnce(new Error('Second fail'))
        .mockResolvedValueOnce('success');

      const { result } = renderHook(() =>
        useAsyncOperation(mockFn, {
          retryCount: 3,
          retryDelay: 10
        })
      );

      let returnValue;
      await act(async () => {
        returnValue = await result.current.execute();
      });

      expect(mockFn).toHaveBeenCalledTimes(3);
      expect(returnValue).toBe('success');
      expect(result.current.data).toBe('success');
      expect(result.current.error).toBeNull();
    });

    it('respects retry delay', async () => {
      const mockFn = jest.fn().mockRejectedValue(new Error('Test error'));
      const retryDelay = 100;
      const startTime = Date.now();

      const { result } = renderHook(() =>
        useAsyncOperation(mockFn, {
          retryCount: 2,
          retryDelay
        })
      );

      try {
        await act(async () => {
          await result.current.execute();
        });
      } catch (error) {
        // Expected to throw
      }

      await waitFor(() => {
        const elapsed = Date.now() - startTime;
        // Should take at least 2 * retryDelay (2 retries)
        expect(elapsed).toBeGreaterThanOrEqual(2 * retryDelay);
      });
    });

    it('manual retry function works', async () => {
      const mockFn = jest.fn().mockRejectedValue(new Error('Test error'));
      const { result } = renderHook(() => useAsyncOperation(mockFn));

      // First execution
      try {
        await act(async () => {
          await result.current.execute();
        });
      } catch (error) {
        // Expected to throw
      }

      expect(mockFn).toHaveBeenCalledTimes(1);

      // Manual retry
      mockFn.mockResolvedValueOnce('success');
      await act(async () => {
        await result.current.retry();
      });

      expect(mockFn).toHaveBeenCalledTimes(2);
      expect(result.current.data).toBe('success');
    });
  });

  describe('Callbacks', () => {
    it('calls onSuccess callback on successful execution', async () => {
      const mockFn = jest.fn().mockResolvedValue('success');
      const onSuccess = jest.fn();

      const { result } = renderHook(() =>
        useAsyncOperation(mockFn, {
          onSuccess
        })
      );

      await act(async () => {
        await result.current.execute();
      });

      expect(onSuccess).toHaveBeenCalledTimes(1);
      expect(onSuccess).toHaveBeenCalledWith('success');
    });

    it('calls onError callback on failure', async () => {
      const mockError = new Error('Test error');
      const mockFn = jest.fn().mockRejectedValue(mockError);
      const onError = jest.fn();

      const { result } = renderHook(() =>
        useAsyncOperation(mockFn, {
          onError
        })
      );

      try {
        await act(async () => {
          await result.current.execute();
        });
      } catch (error) {
        // Expected to throw
      }

      await waitFor(() => {
        expect(onError).toHaveBeenCalledTimes(1);
        expect(onError).toHaveBeenCalledWith(mockError);
      });
    });

    it('calls onSuccess after successful retry', async () => {
      const mockFn = jest
        .fn()
        .mockRejectedValueOnce(new Error('Fail'))
        .mockResolvedValueOnce('success');
      const onSuccess = jest.fn();

      const { result } = renderHook(() =>
        useAsyncOperation(mockFn, {
          retryCount: 2,
          retryDelay: 10,
          onSuccess
        })
      );

      await act(async () => {
        await result.current.execute();
      });

      expect(onSuccess).toHaveBeenCalledTimes(1);
      expect(onSuccess).toHaveBeenCalledWith('success');
    });

    it('calls onError only after all retries exhausted', async () => {
      const mockFn = jest.fn().mockRejectedValue(new Error('Test error'));
      const onError = jest.fn();

      const { result } = renderHook(() =>
        useAsyncOperation(mockFn, {
          retryCount: 2,
          retryDelay: 10,
          onError
        })
      );

      try {
        await act(async () => {
          await result.current.execute();
        });
      } catch (error) {
        // Expected to throw
      }

      await waitFor(() => {
        // Should only call onError once after all retries fail
        expect(onError).toHaveBeenCalledTimes(1);
      });
    });
  });

  describe('Multiple executions', () => {
    it('handles multiple sequential executions', async () => {
      const mockFn = jest
        .fn()
        .mockResolvedValueOnce('first')
        .mockResolvedValueOnce('second')
        .mockResolvedValueOnce('third');

      const { result } = renderHook(() => useAsyncOperation(mockFn));

      await act(async () => {
        await result.current.execute();
      });
      expect(result.current.data).toBe('first');

      await act(async () => {
        await result.current.execute();
      });
      expect(result.current.data).toBe('second');

      await act(async () => {
        await result.current.execute();
      });
      expect(result.current.data).toBe('third');

      expect(mockFn).toHaveBeenCalledTimes(3);
    });

    it('clears error on successful execution after failure', async () => {
      const mockFn = jest
        .fn()
        .mockRejectedValueOnce(new Error('Test error'))
        .mockResolvedValueOnce('success');

      const { result } = renderHook(() => useAsyncOperation(mockFn));

      // First execution fails
      try {
        await act(async () => {
          await result.current.execute();
        });
      } catch (error) {
        // Expected to throw
      }

      await waitFor(() => {
        expect(result.current.error).toBeTruthy();
      });

      // Second execution succeeds
      await act(async () => {
        await result.current.execute();
      });

      expect(result.current.data).toBe('success');
      expect(result.current.error).toBeNull();
    });
  });

  describe('With parameters', () => {
    it('passes parameters to async function', async () => {
      const mockFn = jest.fn().mockResolvedValue('success');
      const { result } = renderHook(() => useAsyncOperation(mockFn));

      await act(async () => {
        await result.current.execute('param1', 'param2', 123);
      });

      expect(mockFn).toHaveBeenCalledWith('param1', 'param2', 123);
    });

    it('handles different parameter types', async () => {
      const mockFn = jest.fn().mockResolvedValue('success');
      const { result } = renderHook(() => useAsyncOperation(mockFn));

      const obj = { key: 'value' };
      const arr = [1, 2, 3];
      const nullParam = null;
      const undefinedParam = undefined;

      await act(async () => {
        await result.current.execute(obj, arr, nullParam, undefinedParam);
      });

      expect(mockFn).toHaveBeenCalledWith(obj, arr, nullParam, undefinedParam);
    });
  });

  describe('Edge cases', () => {
    it('handles function that returns undefined', async () => {
      const mockFn = jest.fn().mockResolvedValue(undefined);
      const { result } = renderHook(() => useAsyncOperation(mockFn));

      await act(async () => {
        await result.current.execute();
      });

      expect(result.current.data).toBeNull();
      expect(result.current.error).toBeNull();
    });

    it('handles function that returns null', async () => {
      const mockFn = jest.fn().mockResolvedValue(null);
      const { result } = renderHook(() => useAsyncOperation(mockFn));

      await act(async () => {
        await result.current.execute();
      });

      expect(result.current.data).toBeNull();
    });

    it('handles zero retries', async () => {
      const mockFn = jest.fn().mockRejectedValue(new Error('Test error'));
      const { result } = renderHook(() =>
        useAsyncOperation(mockFn, {
          retryCount: 0
        })
      );

      try {
        await act(async () => {
          await result.current.execute();
        });
      } catch (error) {
        // Expected to throw
      }

      // Should only call once (no retries)
      await waitFor(() => {
        expect(mockFn).toHaveBeenCalledTimes(1);
      });
    });

    it('handles zero retry delay', async () => {
      const mockFn = jest.fn().mockRejectedValue(new Error('Test error'));
      const { result } = renderHook(() =>
        useAsyncOperation(mockFn, {
          retryCount: 2,
          retryDelay: 0
        })
      );

      try {
        await act(async () => {
          await result.current.execute();
        });
      } catch (error) {
        // Expected to throw
      }

      await waitFor(() => {
        expect(mockFn).toHaveBeenCalledTimes(3);
      });
    });
  });
});
