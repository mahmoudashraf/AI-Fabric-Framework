import { renderHook, act } from '@testing-library/react';
import { useMemoizedCallback, useMemoizedSelector, useStableReference } from '../useMemoization';

describe('useMemoizedCallback', () => {
  it('should memoize callback function', () => {
    const callback = jest.fn((x: number) => x * 2);
    const { result, rerender } = renderHook(
      ({ cb, deps }) => useMemoizedCallback(cb, deps),
      { initialProps: { cb: callback, deps: [1] } }
    );

    const memoizedCallback1 = result.current;
    
    // Rerender with same deps
    rerender({ cb: callback, deps: [1] });
    const memoizedCallback2 = result.current;

    // Should return same reference
    expect(memoizedCallback1).toBe(memoizedCallback2);
  });

  it('should update callback when dependencies change', () => {
    const callback1 = jest.fn((x: number) => x * 2);
    const callback2 = jest.fn((x: number) => x * 3);
    const { result, rerender } = renderHook(
      ({ cb, deps }) => useMemoizedCallback(cb, deps),
      { initialProps: { cb: callback1, deps: [2] } }
    );

    const memoizedCallback1 = result.current;
    
    // Change callback and dependency
    rerender({ cb: callback2, deps: [3] });
    const memoizedCallback2 = result.current;

    // Should return different reference when deps change
    expect(memoizedCallback1).not.toBe(memoizedCallback2);
  });

  it('should call callback with correct arguments', () => {
    const callback = jest.fn((x: number) => x * 2);
    const { result } = renderHook(() => useMemoizedCallback(callback, []));

    act(() => {
      result.current(5);
    });

    expect(callback).toHaveBeenCalledWith(5);
  });

  it('should return correct value from callback', () => {
    const callback = (x: number) => x * 2;
    const { result } = renderHook(() => useMemoizedCallback(callback, []));

    let returnValue: number;
    act(() => {
      returnValue = result.current(5);
    });

    expect(returnValue!).toBe(10);
  });

  it('should handle callbacks with multiple arguments', () => {
    const callback = jest.fn((a: number, b: number, c: number) => a + b + c);
    const { result } = renderHook(() => useMemoizedCallback(callback, []));

    act(() => {
      result.current(1, 2, 3);
    });

    expect(callback).toHaveBeenCalledWith(1, 2, 3);
  });

  it('should detect dependency changes correctly', () => {
    const callback1 = jest.fn();
    const callback2 = jest.fn();
    const { result, rerender } = renderHook(
      ({ cb, deps }) => useMemoizedCallback(cb, deps),
      { initialProps: { cb: callback1, deps: [1, 2] } }
    );

    const memoizedCallback1 = result.current;
    
    // Change callback and dependency
    rerender({ cb: callback2, deps: [1, 3] });
    const memoizedCallback2 = result.current;

    expect(memoizedCallback1).not.toBe(memoizedCallback2);
  });

  it('should handle empty dependencies', () => {
    const callback = jest.fn();
    const { result, rerender } = renderHook(() => useMemoizedCallback(callback, []));

    const memoizedCallback1 = result.current;
    rerender();
    const memoizedCallback2 = result.current;

    // Should always return same reference with empty deps
    expect(memoizedCallback1).toBe(memoizedCallback2);
  });

  it('should handle object dependencies', () => {
    const obj1 = { id: 1 };
    const obj2 = { id: 1 };
    const callback1 = jest.fn();
    const callback2 = jest.fn();
    
    const { result, rerender } = renderHook(
      ({ cb, deps }) => useMemoizedCallback(cb, deps),
      { initialProps: { cb: callback1, deps: [obj1] } }
    );

    const memoizedCallback1 = result.current;
    
    // Different object reference and callback
    rerender({ cb: callback2, deps: [obj2] });
    const memoizedCallback2 = result.current;

    // Should return different reference (shallow comparison)
    expect(memoizedCallback1).not.toBe(memoizedCallback2);
  });
});

describe('useMemoizedSelector', () => {
  it('should memoize selector result', () => {
    const data = { items: [1, 2, 3] };
    const selector = jest.fn((d: typeof data) => d.items.length);
    
    const { result, rerender } = renderHook(() => useMemoizedSelector(data, selector));

    const result1 = result.current;
    rerender();
    const result2 = result.current;

    expect(result1).toBe(result2);
    expect(result1).toBe(3);
    // Selector should only be called once
    expect(selector).toHaveBeenCalledTimes(1);
  });

  it('should recalculate when data changes', () => {
    const selector = (d: { items: number[] }) => d.items.length;
    
    const { result, rerender } = renderHook(
      ({ data }) => useMemoizedSelector(data, selector),
      { initialProps: { data: { items: [1, 2, 3] } } }
    );

    expect(result.current).toBe(3);

    // Update data
    rerender({ data: { items: [1, 2, 3, 4] } });

    expect(result.current).toBe(4);
  });

  it('should recalculate when dependencies change', () => {
    const data = { items: [1, 2, 3] };
    let multiplier = 2;
    const selector = (d: typeof data) => d.items.length * multiplier;
    
    const { result, rerender } = renderHook(
      ({ deps }) => useMemoizedSelector(data, selector, deps),
      { initialProps: { deps: [multiplier] } }
    );

    expect(result.current).toBe(6); // 3 * 2

    // Change dependency
    multiplier = 3;
    rerender({ deps: [multiplier] });

    expect(result.current).toBe(9); // 3 * 3
  });

  it('should handle complex data transformations', () => {
    const data = {
      users: [
        { id: 1, name: 'John', active: true },
        { id: 2, name: 'Jane', active: false },
        { id: 3, name: 'Bob', active: true }
      ]
    };

    const selector = (d: typeof data) => d.users.filter(u => u.active).map(u => u.name);
    
    const { result } = renderHook(() => useMemoizedSelector(data, selector));

    expect(result.current).toEqual(['John', 'Bob']);
  });

  it('should not recalculate when data reference is same', () => {
    const data = { items: [1, 2, 3] };
    const selector = jest.fn((d: typeof data) => d.items.length);
    
    const { result, rerender } = renderHook(() => useMemoizedSelector(data, selector));

    expect(selector).toHaveBeenCalledTimes(1);
    
    rerender();
    rerender();
    rerender();

    // Selector should still only be called once
    expect(selector).toHaveBeenCalledTimes(1);
    expect(result.current).toBe(3);
  });

  it('should handle empty dependencies array', () => {
    const data = { items: [1, 2, 3] };
    const selector = jest.fn((d: typeof data) => d.items.length);
    
    const { rerender } = renderHook(() => useMemoizedSelector(data, selector, []));

    expect(selector).toHaveBeenCalledTimes(1);
    
    rerender();

    expect(selector).toHaveBeenCalledTimes(1);
  });

  it('should handle nested data selection', () => {
    const data = {
      level1: {
        level2: {
          level3: {
            value: 'deep value'
          }
        }
      }
    };

    const selector = (d: typeof data) => d.level1.level2.level3.value;
    
    const { result } = renderHook(() => useMemoizedSelector(data, selector));

    expect(result.current).toBe('deep value');
  });

  it('should handle array mapping operations', () => {
    const data = { numbers: [1, 2, 3, 4, 5] };
    const selector = (d: typeof data) => d.numbers.map(n => n * 2);
    
    const { result } = renderHook(() => useMemoizedSelector(data, selector));

    expect(result.current).toEqual([2, 4, 6, 8, 10]);
  });
});

describe('useStableReference', () => {
  it('should return initial value', () => {
    const value = { id: 1, name: 'Test' };
    const { result } = renderHook(() => useStableReference(value));

    expect(result.current).toEqual(value);
  });

  it('should update reference when value changes', () => {
    const { result, rerender } = renderHook(
      ({ value }) => useStableReference(value),
      { initialProps: { value: { id: 1 } } }
    );

    expect(result.current).toEqual({ id: 1 });

    // Update value
    rerender({ value: { id: 2 } });

    expect(result.current).toEqual({ id: 2 });
  });

  it('should maintain stable reference between renders with same value', () => {
    const value = { id: 1 };
    const { result, rerender } = renderHook(() => useStableReference(value));

    const ref1 = result.current;
    rerender();
    const ref2 = result.current;

    // Should maintain same reference
    expect(ref1).toBe(ref2);
  });

  it('should handle primitive values', () => {
    const { result, rerender } = renderHook(
      ({ value }) => useStableReference(value),
      { initialProps: { value: 42 } }
    );

    expect(result.current).toBe(42);

    rerender({ value: 100 });

    expect(result.current).toBe(100);
  });

  it('should handle string values', () => {
    const { result, rerender } = renderHook(
      ({ value }) => useStableReference(value),
      { initialProps: { value: 'initial' } }
    );

    expect(result.current).toBe('initial');

    rerender({ value: 'updated' });

    expect(result.current).toBe('updated');
  });

  it('should handle array values', () => {
    const { result, rerender } = renderHook(
      ({ value }) => useStableReference(value),
      { initialProps: { value: [1, 2, 3] } }
    );

    expect(result.current).toEqual([1, 2, 3]);

    rerender({ value: [4, 5, 6] });

    expect(result.current).toEqual([4, 5, 6]);
  });

  it('should handle function values', () => {
    const fn1 = () => 'first';
    const fn2 = () => 'second';
    
    const { result, rerender } = renderHook(
      ({ value }) => useStableReference(value),
      { initialProps: { value: fn1 } }
    );

    expect(result.current()).toBe('first');

    rerender({ value: fn2 });

    expect(result.current()).toBe('second');
  });

  it('should handle undefined and null', () => {
    const { result, rerender } = renderHook(
      ({ value }) => useStableReference(value),
      { initialProps: { value: undefined as any } }
    );

    expect(result.current).toBeUndefined();

    rerender({ value: null });

    expect(result.current).toBeNull();

    rerender({ value: 'value' });

    expect(result.current).toBe('value');
  });

  it('should work with complex nested objects', () => {
    const complexObject = {
      user: {
        id: 1,
        profile: {
          name: 'John',
          settings: {
            theme: 'dark'
          }
        }
      }
    };

    const { result, rerender } = renderHook(
      ({ value }) => useStableReference(value),
      { initialProps: { value: complexObject } }
    );

    expect(result.current).toEqual(complexObject);

    const updatedObject = {
      user: {
        id: 2,
        profile: {
          name: 'Jane',
          settings: {
            theme: 'light'
          }
        }
      }
    };

    rerender({ value: updatedObject });

    expect(result.current).toEqual(updatedObject);
  });

  it('should prevent unnecessary re-renders with stable reference', () => {
    const callback = jest.fn();
    const value = { id: 1 };

    const { rerender } = renderHook(() => {
      const stable = useStableReference(value);
      callback(stable);
      return stable;
    });

    expect(callback).toHaveBeenCalledTimes(1);

    // Rerender multiple times
    rerender();
    rerender();
    rerender();

    // Callback should be called on each render, but with same reference
    expect(callback).toHaveBeenCalledTimes(4);
    const calls = callback.mock.calls;
    expect(calls[0][0]).toBe(calls[1][0]);
    expect(calls[1][0]).toBe(calls[2][0]);
    expect(calls[2][0]).toBe(calls[3][0]);
  });
});

describe('useMemoization - Integration Tests', () => {
  it('should work together for performance optimization', () => {
    const data = { items: [1, 2, 3, 4, 5] };
    const selectorFn = jest.fn((d: typeof data) => d.items.filter(i => i > 2));
    const callbackFn = jest.fn((item: number) => item * 2);

    const { result, rerender } = renderHook(() => {
      const filtered = useMemoizedSelector(data, selectorFn);
      const stableFiltered = useStableReference(filtered);
      const processItem = useMemoizedCallback(callbackFn, []);

      return { filtered: stableFiltered, processItem };
    });

    expect(result.current.filtered).toEqual([3, 4, 5]);
    expect(selectorFn).toHaveBeenCalledTimes(1);

    // Process items
    act(() => {
      result.current.filtered.forEach(item => result.current.processItem(item));
    });

    expect(callbackFn).toHaveBeenCalledTimes(3);

    // Rerender - selector should not be called again
    rerender();

    expect(selectorFn).toHaveBeenCalledTimes(1);
  });

  it('should optimize complex data transformations', () => {
    const users = [
      { id: 1, name: 'John', age: 25, active: true },
      { id: 2, name: 'Jane', age: 30, active: false },
      { id: 3, name: 'Bob', age: 35, active: true }
    ];

    const transformFn = jest.fn((users: typeof users) => 
      users.filter(u => u.active).map(u => ({ ...u, ageNextYear: u.age + 1 }))
    );

    const data = { users };
    const { result, rerender } = renderHook(
      ({ data }) => useMemoizedSelector(data, (d) => transformFn(d.users)),
      { initialProps: { data } }
    );

    expect(transformFn).toHaveBeenCalledTimes(1);
    expect(result.current).toHaveLength(2);
    expect(result.current[0].ageNextYear).toBe(26);

    // Multiple rerenders shouldn't trigger transformation
    rerender({ data });
    rerender({ data });

    expect(transformFn).toHaveBeenCalledTimes(1);
  });
});
