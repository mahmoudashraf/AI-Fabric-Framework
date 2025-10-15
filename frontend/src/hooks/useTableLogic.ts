// Enterprise Pattern: Generic reusable hook for table logic
import { useState, useCallback, useEffect } from 'react';
import type { ArrangementOrder, KeyedObject } from 'types';

// Generic table sort utilities
function descendingComparator(a: KeyedObject, b: KeyedObject, orderBy: string) {
  if (b[orderBy] < a[orderBy]) return -1;
  if (b[orderBy] > a[orderBy]) return 1;
  return 0;
}

function getComparator(order: ArrangementOrder, orderBy: string) {
  return order === 'desc'
    ? (a: KeyedObject, b: KeyedObject) => descendingComparator(a, b, orderBy)
    : (a: KeyedObject, b: KeyedObject) => -descendingComparator(a, b, orderBy);
}

function stableSort<T extends KeyedObject>(array: T[], comparator: (a: T, b: T) => number) {
  const stabilizedThis = array.map((el: T, index: number) => [el, index]);
  stabilizedThis.sort((a, b) => {
    const order = comparator(a[0] as T, b[0] as T);
    if (order !== 0) return order;
    return (a[1] as number) - (b[1] as number);
  });
  return stabilizedThis.map((el) => el[0]);
}

interface UseTableLogicOptions<T extends KeyedObject> {
  data: T[];
  searchFields?: string[];
  defaultOrderBy?: string;
  defaultRowsPerPage?: number;
  rowIdentifier?: keyof T; // Field to use for row selection (default: 'name')
}

export function useTableLogic<T extends KeyedObject>({
  data,
  searchFields = [],
  defaultOrderBy = 'name',
  defaultRowsPerPage = 5,
  rowIdentifier = 'name' as keyof T,
}: UseTableLogicOptions<T>) {
  const [order, setOrder] = useState<ArrangementOrder>('asc');
  const [orderBy, setOrderBy] = useState<string>(defaultOrderBy);
  const [selected, setSelected] = useState<string[]>([]);
  const [page, setPage] = useState<number>(0);
  const [rowsPerPage, setRowsPerPage] = useState<number>(defaultRowsPerPage);
  const [search, setSearch] = useState<string>('');
  const [rows, setRows] = useState<T[]>([]);

  // Initialize and filter rows based on data and search
  useEffect(() => {
    if (search && searchFields.length > 0) {
      const filtered = data.filter((row: KeyedObject) => {
        return searchFields.some((property) =>
          row[property]?.toString().toLowerCase().includes(search.toLowerCase())
        );
      });
      setRows(filtered);
    } else {
      setRows(data);
    }
  }, [data, search, searchFields]);

  const handleSearch = useCallback(
    (event: React.ChangeEvent<HTMLTextAreaElement | HTMLInputElement> | undefined) => {
      const newString = event?.target.value || '';
      setSearch(newString);
      setPage(0); // Reset to first page when searching
    },
    []
  );

  const handleRequestSort = useCallback(
    (_event: React.SyntheticEvent<Element, Event>, property: string) => {
      const isAsc = orderBy === property && order === 'asc';
      setOrder(isAsc ? 'desc' : 'asc');
      setOrderBy(property);
    },
    [order, orderBy]
  );

  const handleSelectAllClick = useCallback(
    (_event: React.ChangeEvent<HTMLInputElement>) => {
      if (_event.target.checked) {
        if (selected.length > 0) {
          setSelected([]);
        } else {
          const newSelectedId = rows.map((n) => String(n[rowIdentifier]));
          setSelected(newSelectedId);
        }
        return;
      }
      setSelected([]);
    },
    [rows, selected.length, rowIdentifier]
  );

  const handleClick = useCallback(
    (_event: React.MouseEvent<HTMLTableHeaderCellElement, MouseEvent>, identifier: string) => {
      const selectedIndex = selected.indexOf(identifier);
      let newSelected: string[] = [];

      if (selectedIndex === -1) {
        newSelected = newSelected.concat(selected, identifier);
      } else if (selectedIndex === 0) {
        newSelected = newSelected.concat(selected.slice(1));
      } else if (selectedIndex === selected.length - 1) {
        newSelected = newSelected.concat(selected.slice(0, -1));
      } else if (selectedIndex > 0) {
        newSelected = newSelected.concat(selected.slice(0, selectedIndex), selected.slice(selectedIndex + 1));
      }

      setSelected(newSelected);
    },
    [selected]
  );

  const handleChangePage = useCallback(
    (_event: React.MouseEvent<HTMLButtonElement, MouseEvent> | null, newPage: number) => {
      setPage(newPage);
    },
    []
  );

  const handleChangeRowsPerPage = useCallback(
    (_event: React.ChangeEvent<HTMLTextAreaElement | HTMLInputElement> | undefined) => {
      if (_event?.target.value) setRowsPerPage(parseInt(_event?.target.value, 10));
      setPage(0);
    },
    []
  );

  const isSelected = useCallback((identifier: string) => selected.indexOf(identifier) !== -1, [selected]);

  const sortedAndPaginatedRows = stableSort(rows, getComparator(order, orderBy)).slice(
    page * rowsPerPage,
    page * rowsPerPage + rowsPerPage
  );

  const emptyRows = page > 0 ? Math.max(0, (1 + page) * rowsPerPage - rows.length) : 0;

  return {
    // State
    order,
    orderBy,
    selected,
    page,
    rowsPerPage,
    search,
    rows,
    sortedAndPaginatedRows,
    emptyRows,
    // Handlers
    handleSearch,
    handleRequestSort,
    handleSelectAllClick,
    handleClick,
    handleChangePage,
    handleChangeRowsPerPage,
    isSelected,
  };
}
