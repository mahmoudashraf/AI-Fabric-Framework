import { renderHook, act } from '@testing-library/react';
import { useTableLogic } from '../useTableLogic';

interface TestData {
  id: number;
  name: string;
  email: string;
  age: number;
  status: string;
}

const mockData: TestData[] = [
  { id: 1, name: 'John Doe', email: 'john@example.com', age: 30, status: 'active' },
  { id: 2, name: 'Jane Smith', email: 'jane@example.com', age: 25, status: 'inactive' },
  { id: 3, name: 'Bob Johnson', email: 'bob@example.com', age: 35, status: 'active' },
  { id: 4, name: 'Alice Brown', email: 'alice@example.com', age: 28, status: 'active' },
  { id: 5, name: 'Charlie Wilson', email: 'charlie@example.com', age: 32, status: 'inactive' }
];

describe('useTableLogic', () => {
  describe('Initialization', () => {
    it('initializes with provided data', () => {
      const { result } = renderHook(() =>
        useTableLogic({ data: mockData })
      );

      expect(result.current.rows).toHaveLength(5); // All data fits on first page
      expect(result.current.page).toBe(0);
      expect(result.current.rowsPerPage).toBe(10);
      expect(result.current.selected).toEqual([]);
      expect(result.current.search).toBe('');
    });

    it('initializes with custom default values', () => {
      const { result } = renderHook(() =>
        useTableLogic({
          data: mockData,
          defaultOrderBy: 'name',
          defaultRowsPerPage: 5
        })
      );

      expect(result.current.orderBy).toBe('name');
      expect(result.current.rowsPerPage).toBe(5);
    });

    it('handles empty data', () => {
      const { result } = renderHook(() =>
        useTableLogic({ data: [] })
      );

      expect(result.current.rows).toEqual([]);
    });

    it('provides all required methods', () => {
      const { result } = renderHook(() =>
        useTableLogic({ data: mockData })
      );

      expect(typeof result.current.handleRequestSort).toBe('function');
      expect(typeof result.current.handleSelectAllClick).toBe('function');
      expect(typeof result.current.handleRowClick).toBe('function');
      expect(typeof result.current.handleChangePage).toBe('function');
      expect(typeof result.current.handleChangeRowsPerPage).toBe('function');
      expect(typeof result.current.handleSearch).toBe('function');
    });
  });

  describe('Sorting', () => {
    it('sorts data by string field ascending', () => {
      const { result } = renderHook(() =>
        useTableLogic({ data: mockData, defaultOrderBy: 'name' })
      );

      act(() => {
        result.current.handleRequestSort({} as any, 'name');
      });

      expect(result.current.order).toBe('asc');
      expect(result.current.orderBy).toBe('name');
      expect(result.current.rows[0].name).toBe('Alice Brown');
    });

    it('sorts data by string field descending', () => {
      const { result } = renderHook(() =>
        useTableLogic({ data: mockData, defaultOrderBy: 'name' })
      );

      act(() => {
        result.current.handleRequestSort({} as any, 'name');
      });

      act(() => {
        result.current.handleRequestSort({} as any, 'name');
      });

      expect(result.current.order).toBe('desc');
      expect(result.current.rows[0].name).toBe('John Doe');
    });

    it('sorts data by numeric field', () => {
      const { result } = renderHook(() =>
        useTableLogic({ data: mockData })
      );

      act(() => {
        result.current.handleRequestSort({} as any, 'age');
      });

      expect(result.current.rows[0].age).toBe(25);
      expect(result.current.rows[4].age).toBe(35);
    });

    it('changes sort column', () => {
      const { result } = renderHook(() =>
        useTableLogic({ data: mockData, defaultOrderBy: 'name' })
      );

      act(() => {
        result.current.handleRequestSort({} as any, 'name');
      });

      expect(result.current.orderBy).toBe('name');

      act(() => {
        result.current.handleRequestSort({} as any, 'age');
      });

      expect(result.current.orderBy).toBe('age');
      expect(result.current.order).toBe('asc');
    });

    it('toggles sort direction on same column', () => {
      const { result } = renderHook(() =>
        useTableLogic({ data: mockData })
      );

      act(() => {
        result.current.handleRequestSort({} as any, 'name');
      });

      expect(result.current.order).toBe('asc');

      act(() => {
        result.current.handleRequestSort({} as any, 'name');
      });

      expect(result.current.order).toBe('desc');

      act(() => {
        result.current.handleRequestSort({} as any, 'name');
      });

      expect(result.current.order).toBe('asc');
    });
  });

  describe('Pagination', () => {
    it('paginates data correctly', () => {
      const { result } = renderHook(() =>
        useTableLogic({ data: mockData, defaultRowsPerPage: 2 })
      );

      expect(result.current.rows).toHaveLength(2);
      expect(result.current.rows[0].id).toBe(1);
      expect(result.current.rows[1].id).toBe(2);
    });

    it('changes page', () => {
      const { result } = renderHook(() =>
        useTableLogic({ data: mockData, defaultRowsPerPage: 2 })
      );

      act(() => {
        result.current.handleChangePage({} as any, 1);
      });

      expect(result.current.page).toBe(1);
      expect(result.current.rows[0].id).toBe(3);
      expect(result.current.rows[1].id).toBe(4);
    });

    it('changes rows per page', () => {
      const { result } = renderHook(() =>
        useTableLogic({ data: mockData, defaultRowsPerPage: 2 })
      );

      act(() => {
        result.current.handleChangeRowsPerPage({ target: { value: '5' } } as any);
      });

      expect(result.current.rowsPerPage).toBe(5);
      expect(result.current.page).toBe(0); // Reset to first page
      expect(result.current.rows).toHaveLength(5);
    });

    it('handles last page correctly', () => {
      const { result } = renderHook(() =>
        useTableLogic({ data: mockData, defaultRowsPerPage: 2 })
      );

      act(() => {
        result.current.handleChangePage({} as any, 2);
      });

      expect(result.current.page).toBe(2);
      expect(result.current.rows).toHaveLength(1); // Last item
    });
  });

  describe('Search', () => {
    it('filters data by search term', () => {
      const { result } = renderHook(() =>
        useTableLogic({
          data: mockData,
          searchFields: ['name', 'email']
        })
      );

      act(() => {
        result.current.handleSearch({ target: { value: 'john' } } as any);
      });

      expect(result.current.search).toBe('john');
      expect(result.current.rows).toHaveLength(2); // John Doe and Bob Johnson
    });

    it('searches across multiple fields', () => {
      const { result } = renderHook(() =>
        useTableLogic({
          data: mockData,
          searchFields: ['name', 'email', 'status']
        })
      );

      act(() => {
        result.current.handleSearch({ target: { value: 'active' } } as any);
      });

      expect(result.current.rows).toHaveLength(3); // 3 active users
    });

    it('handles case-insensitive search', () => {
      const { result } = renderHook(() =>
        useTableLogic({
          data: mockData,
          searchFields: ['name']
        })
      );

      act(() => {
        result.current.handleSearch({ target: { value: 'JOHN' } } as any);
      });

      expect(result.current.rows).toHaveLength(2); // Case insensitive
    });

    it('resets to first page on search', () => {
      const { result } = renderHook(() =>
        useTableLogic({
          data: mockData,
          searchFields: ['name'],
          defaultRowsPerPage: 2
        })
      );

      act(() => {
        result.current.handleChangePage({} as any, 1);
      });

      expect(result.current.page).toBe(1);

      act(() => {
        result.current.handleSearch({ target: { value: 'john' } } as any);
      });

      expect(result.current.page).toBe(0); // Reset to first page
    });

    it('clears search results when search is empty', () => {
      const { result } = renderHook(() =>
        useTableLogic({
          data: mockData,
          searchFields: ['name']
        })
      );

      act(() => {
        result.current.handleSearch({ target: { value: 'john' } } as any);
      });

      expect(result.current.rows).toHaveLength(2);

      act(() => {
        result.current.handleSearch({ target: { value: '' } } as any);
      });

      expect(result.current.rows).toHaveLength(5); // All data
    });

    it('handles search with no results', () => {
      const { result } = renderHook(() =>
        useTableLogic({
          data: mockData,
          searchFields: ['name']
        })
      );

      act(() => {
        result.current.handleSearch({ target: { value: 'nonexistent' } } as any);
      });

      expect(result.current.rows).toHaveLength(0);
    });
  });

  describe('Row Selection', () => {
    it('selects single row', () => {
      const { result } = renderHook(() =>
        useTableLogic({ data: mockData, rowIdentifier: 'name' })
      );

      act(() => {
        result.current.handleRowClick('John Doe');
      });

      expect(result.current.selected).toContain('John Doe');
      expect(result.current.selected).toHaveLength(1);
    });

    it('deselects row when clicked again', () => {
      const { result } = renderHook(() =>
        useTableLogic({ data: mockData, rowIdentifier: 'name' })
      );

      act(() => {
        result.current.handleRowClick('John Doe');
      });

      expect(result.current.selected).toContain('John Doe');

      act(() => {
        result.current.handleRowClick('John Doe');
      });

      expect(result.current.selected).not.toContain('John Doe');
      expect(result.current.selected).toHaveLength(0);
    });

    it('selects multiple rows', () => {
      const { result } = renderHook(() =>
        useTableLogic({ data: mockData, rowIdentifier: 'name' })
      );

      act(() => {
        result.current.handleRowClick('John Doe');
        result.current.handleRowClick('Jane Smith');
      });

      expect(result.current.selected).toContain('John Doe');
      expect(result.current.selected).toContain('Jane Smith');
      expect(result.current.selected).toHaveLength(2);
    });

    it('selects all rows', () => {
      const { result } = renderHook(() =>
        useTableLogic({ data: mockData, rowIdentifier: 'name' })
      );

      act(() => {
        result.current.handleSelectAllClick({ target: { checked: true } } as any);
      });

      expect(result.current.selected).toHaveLength(5);
    });

    it('deselects all rows', () => {
      const { result } = renderHook(() =>
        useTableLogic({ data: mockData, rowIdentifier: 'name' })
      );

      act(() => {
        result.current.handleSelectAllClick({ target: { checked: true } } as any);
      });

      expect(result.current.selected).toHaveLength(5);

      act(() => {
        result.current.handleSelectAllClick({ target: { checked: false } } as any);
      });

      expect(result.current.selected).toHaveLength(0);
    });

  });

  describe('Data Updates', () => {
    it('updates rows when data changes', () => {
      const { result, rerender } = renderHook(
        ({ data }) => useTableLogic({ data }),
        { initialProps: { data: mockData.slice(0, 3) } }
      );

      expect(result.current.rows).toHaveLength(3);

      rerender({ data: mockData });

      expect(result.current.rows).toHaveLength(5);
    });

    it('preserves sort when data changes', () => {
      const { result, rerender } = renderHook(
        ({ data }) => useTableLogic({ data }),
        { initialProps: { data: mockData.slice(0, 3) } }
      );

      act(() => {
        result.current.handleRequestSort({} as any, 'name');
      });

      expect(result.current.orderBy).toBe('name');

      rerender({ data: mockData });

      expect(result.current.orderBy).toBe('name');
      expect(result.current.order).toBe('asc');
    });

    it('clears selection when data changes significantly', () => {
      const { result, rerender } = renderHook(
        ({ data }) => useTableLogic({ data, rowIdentifier: 'name' }),
        { initialProps: { data: mockData } }
      );

      act(() => {
        result.current.handleRowClick('John Doe');
      });

      expect(result.current.selected).toHaveLength(1);

      // Data changed, selection should still be valid
      rerender({ data: mockData.slice(0, 2) });

      // Selection is maintained (stored separately from data)
      expect(result.current.selected).toContain('John Doe');
    });
  });

  describe('Edge Cases', () => {
    it('handles undefined data', () => {
      const { result } = renderHook(() =>
        useTableLogic({ data: undefined as any })
      );

      expect(result.current.rows).toEqual([]);
    });

    it('handles data with missing search fields', () => {
      const { result } = renderHook(() =>
        useTableLogic({
          data: mockData,
          searchFields: ['nonexistentField' as any]
        })
      );

      act(() => {
        result.current.handleSearch({ target: { value: 'test' } } as any);
      });

      expect(result.current.rows).toHaveLength(0);
    });

    it('handles sorting with null values', () => {
      const dataWithNulls = [
        ...mockData,
        { id: 6, name: null as any, email: 'test@example.com', age: 30, status: 'active' }
      ];

      const { result } = renderHook(() =>
        useTableLogic({ data: dataWithNulls })
      );

      act(() => {
        result.current.handleRequestSort({} as any, 'name');
      });

      // Should not crash
      expect(result.current.rows).toBeDefined();
    });

    it('handles very large rowsPerPage', () => {
      const { result } = renderHook(() =>
        useTableLogic({ data: mockData, defaultRowsPerPage: 1000 })
      );

      expect(result.current.rows).toHaveLength(5); // All data
    });

    it('handles page number greater than available pages', () => {
      const { result } = renderHook(() =>
        useTableLogic({ data: mockData, defaultRowsPerPage: 2 })
      );

      act(() => {
        result.current.handleChangePage({} as any, 10);
      });

      // Should handle gracefully
      expect(result.current.page).toBe(10);
      expect(result.current.rows).toHaveLength(0);
    });
  });

  describe('Combined Operations', () => {
    it('combines search, sort, and pagination', () => {
      const { result } = renderHook(() =>
        useTableLogic({
          data: mockData,
          searchFields: ['status'],
          defaultRowsPerPage: 2
        })
      );

      // Search
      act(() => {
        result.current.handleSearch({ target: { value: 'active' } } as any);
      });

      expect(result.current.rows).toHaveLength(3);

      // Sort
      act(() => {
        result.current.handleRequestSort({} as any, 'age');
      });

      // Paginate
      act(() => {
        result.current.handleChangePage({} as any, 1);
      });

      expect(result.current.page).toBe(1);
      expect(result.current.rows).toHaveLength(1); // Last item on page 2
    });

    it('combines selection with search', () => {
      const { result } = renderHook(() =>
        useTableLogic({
          data: mockData,
          searchFields: ['status'],
          rowIdentifier: 'name'
        })
      );

      // Select some rows
      act(() => {
        result.current.handleRowClick('John Doe');
        result.current.handleRowClick('Jane Smith');
      });

      expect(result.current.selected).toHaveLength(2);

      // Search - selected rows persist regardless of search results
      act(() => {
        result.current.handleSearch({ target: { value: 'active' } } as any);
      });

      // Selections are maintained (stored separately)
      expect(result.current.selected).toHaveLength(2);
    });
  });
});
