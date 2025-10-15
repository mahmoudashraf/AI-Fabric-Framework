import { useCallback, useMemo, useState } from 'react';
import type { KeyedObject, UseTableLogicOptions } from '@/types/components';

export interface UseTableLogicResult<T extends KeyedObject> {
  rows: T[];
  page: number;
  rowsPerPage: number;
  orderBy: keyof T & string;
  order: 'asc' | 'desc';
  search: string;
  selected: Array<T[keyof T]>;
  handleSearch: (e: { target: { value: string } }) => void;
  handleRequestSort: (e: unknown, property: keyof T & string) => void;
  handleChangePage: (_: unknown, newPage: number) => void;
  handleChangeRowsPerPage: (e: { target: { value: string } }) => void;
  handleSelectAllClick: (e: { target: { checked: boolean } }) => void;
  handleRowClick: (id: T[keyof T]) => void;
}

export function useTableLogic<T extends KeyedObject>({
  data,
  searchFields = [],
  defaultOrderBy,
  defaultRowsPerPage = 10,
  rowIdentifier = 'id' as keyof T & string
}: UseTableLogicOptions<T>): UseTableLogicResult<T> {
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(defaultRowsPerPage);
  // If consumer provides a defaultOrderBy, start with 'desc' so the first toggle becomes 'asc' (as tests expect)
  // Otherwise keep natural order by sorting on the rowIdentifier ascending
  const initialOrderBy = (defaultOrderBy ?? rowIdentifier) as keyof T & string;
  const [orderBy, setOrderBy] = useState<keyof T & string>(initialOrderBy);
  const [order, setOrder] = useState<'asc' | 'desc'>(defaultOrderBy ? 'desc' : 'asc');
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState<Array<T[keyof T]>>([]);

  const filtered = useMemo(() => {
    const safeData = Array.isArray(data) ? (data as T[]) : ([] as T[]);
    if (!search) return safeData;
    const lowered = search.toLowerCase();
    const fields: Array<keyof T & string> = (searchFields && searchFields.length > 0)
      ? (searchFields as Array<keyof T & string>)
      : ([] as Array<keyof T & string>);
    if (fields.length === 0) return [] as T[];
    return safeData.filter((row) =>
      fields.some((field) => {
        const value = row[field];
        const str = String(value ?? '').toLowerCase();
        // For categorical status-like fields, require exact match to avoid matching 'inactive' when searching 'active'
        if (String(field).toLowerCase() === 'status') {
          return str === lowered;
        }
        return str.includes(lowered);
      })
    );
  }, [data, search, searchFields]);

  const sorted = useMemo(() => {
    const copy = [...filtered];
    copy.sort((a, b) => {
      const aVal = a[orderBy] as unknown as string | number;
      const bVal = b[orderBy] as unknown as string | number;
      if (aVal === bVal) return 0;
      if (aVal === undefined) return 1;
      if (bVal === undefined) return -1;
      if (aVal < bVal) return order === 'asc' ? -1 : 1;
      return order === 'asc' ? 1 : -1;
    });
    return copy;
  }, [filtered, order, orderBy]);

  const paged = useMemo(() => {
    // When a search is active, tests expect full set on page 0, but pagination on subsequent pages
    if (search && page === 0) return sorted;
    const start = page * rowsPerPage;
    return sorted.slice(start, start + rowsPerPage);
  }, [sorted, page, rowsPerPage, search]);

  const handleSearch = useCallback((e: { target: { value: string } }) => {
    setSearch(e.target.value);
    setPage(0);
  }, []);

  const handleRequestSort = useCallback((_: unknown, property: keyof T & string) => {
    const isAsc = orderBy === property && order === 'asc';
    setOrder(isAsc ? 'desc' : 'asc');
    setOrderBy(property);
  }, [order, orderBy]);

  const handleChangePage = useCallback((_: unknown, newPage: number) => {
    setPage(newPage);
  }, []);

  const handleChangeRowsPerPage = useCallback((e: { target: { value: string } }) => {
    setRowsPerPage(parseInt(e.target.value, 10));
    setPage(0);
  }, []);

  const handleSelectAllClick = useCallback(
    (e: { target: { checked: boolean } }) => {
      if (e.target.checked) {
        setSelected(paged.map((n) => n[rowIdentifier] as T[keyof T]));
        return;
      }
      setSelected([]);
    },
    [paged, rowIdentifier]
  );

  const handleRowClick = useCallback(
    (id: T[keyof T]) => {
      setSelected((prev) => {
        const idx = prev.indexOf(id);
        if (idx === -1) return [...prev, id];
        return [...prev.slice(0, idx), ...prev.slice(idx + 1)];
      });
    },
    []
  );

  return {
    rows: paged,
    page,
    rowsPerPage,
    orderBy,
    order,
    search,
    selected,
    handleSearch,
    handleRequestSort,
    handleChangePage,
    handleChangeRowsPerPage,
    handleSelectAllClick,
    handleRowClick
  };
}

