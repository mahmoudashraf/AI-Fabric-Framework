import { renderHook, waitFor, act } from '@testing-library/react';
import { useAsyncOperation } from '../useAsyncOperation';
import { mockApiResponse } from '@/test-utils/enterprise-testing';

describe('useAsyncOperation (example test)', () => {
  it('handles successful async operation', async () => {
    const resultData = { id: '1', name: 'Test' };
    const asyncFn = jest.fn().mockResolvedValue(mockApiResponse(resultData));

    const { result } = renderHook(() => useAsyncOperation(asyncFn));

    let data;
    await act(async () => {
      data = await result.current.execute();
    });

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(asyncFn).toHaveBeenCalled();
    expect(data).toEqual(resultData);
    expect(result.current.data).toEqual(resultData);
    expect(result.current.error).toBeNull();
  });
});


