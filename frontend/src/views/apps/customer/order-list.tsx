'use client';

import DeleteIcon from '@mui/icons-material/Delete';
import EditTwoToneIcon from '@mui/icons-material/EditTwoTone';
import FileCopyIcon from '@mui/icons-material/FileCopyTwoTone';
import FilterListIcon from '@mui/icons-material/FilterListTwoTone';
import PrintIcon from '@mui/icons-material/PrintTwoTone';
import SearchIcon from '@mui/icons-material/Search';
import VisibilityTwoToneIcon from '@mui/icons-material/VisibilityTwoTone';
import { Box,
  CardContent,
  Checkbox,
  Grid,
  IconButton,
  InputAdornment,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TableSortLabel,
  TextField,
  Toolbar,
  Tooltip,
  Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { visuallyHidden } from '@mui/utils';
import React from 'react';
import { useAsyncOperation } from '@/hooks/enterprise';

// material-ui

// project imports
import { useCustomer } from 'contexts/CustomerContext';
import { useNotifications } from 'contexts/NotificationContext';
import MainCard from 'ui-component/cards/MainCard';
import Chip from 'ui-component/extended/Chip';
import { withErrorBoundary } from '@/components/enterprise';
import { useTableLogic } from '@/hooks/useTableLogic';

// types
import type { Order } from 'types/customer';
import type { EnhancedTableHeadProps, HeadCell, EnhancedTableToolbarProps } from 'types';

// table header options

const headCells: HeadCell[] = [
  {
    id: 'id',
    numeric: true,
    label: 'ID',
    align: 'center',
  },
  {
    id: 'name',
    numeric: false,
    label: 'Customer Name',
    align: 'left',
  },
  {
    id: 'company',
    numeric: true,
    label: 'Branch',
    align: 'left',
  },
  {
    id: 'type',
    numeric: true,
    label: 'Payment Type',
    align: 'left',
  },
  {
    id: 'qty',
    numeric: true,
    label: 'Quantity',
    align: 'right',
  },
  {
    id: 'date',
    numeric: true,
    label: 'Registered',
    align: 'center',
  },
  {
    id: 'status',
    numeric: false,
    label: 'Status',
    align: 'center',
  },
];

// ==============================|| TABLE HEADER TOOLBAR ||============================== //

const EnhancedTableToolbar = ({ numSelected }: EnhancedTableToolbarProps) => (
  <Toolbar
    sx={{
      p: 0,
      pl: 1,
      pr: 1,
      ...(numSelected > 0 && {
        color: theme => theme.palette.secondary.main,
      }),
    }}
  >
    {numSelected > 0 ? (
      <Typography color="inherit" variant="h4">
        {numSelected} Selected
      </Typography>
    ) : (
      <Typography variant="h6" id="tableTitle">
        Nutrition
      </Typography>
    )}
    <Box sx={{ flexGrow: 1 }} />
    {numSelected > 0 && (
      <Tooltip title="Delete">
        <IconButton size="large">
          <DeleteIcon fontSize="small" />
        </IconButton>
      </Tooltip>
    )}
  </Toolbar>
);

// ==============================|| TABLE HEADER ||============================== //

interface OrderListEnhancedTableHeadProps extends EnhancedTableHeadProps {
  selected: string[];
}

function EnhancedTableHead({
  onSelectAllClick,
  order,
  orderBy,
  numSelected,
  rowCount,
  onRequestSort,
  selected,
}: OrderListEnhancedTableHeadProps) {
  const theme = useTheme();
  const createSortHandler = (property: string) => (_event: React.SyntheticEvent<Element, Event>) => {
    onRequestSort(_event, property);
  };

  return (
    <TableHead>
      <TableRow>
        <TableCell padding="checkbox" sx={{ pl: 3 }}>
          <Checkbox
            color="primary"
            indeterminate={numSelected > 0 && numSelected < rowCount}
            checked={rowCount > 0 && numSelected === rowCount}
            onChange={onSelectAllClick}
            inputProps={{
              'aria-label': 'select all desserts',
            }}
          />
        </TableCell>
        {numSelected > 0 && (
          <TableCell padding="none" colSpan={8}>
            <EnhancedTableToolbar numSelected={selected.length} />
          </TableCell>
        )}
        {numSelected <= 0 &&
          headCells.map(headCell => (
            <TableCell
              key={headCell.id}
              align={(headCell.align ?? 'left') as 'left' | 'right' | 'center' | 'inherit' | 'justify'}
              padding={headCell.disablePadding ? 'none' : 'normal'}
              sortDirection={orderBy === headCell.id ? (order ?? 'asc') : false}
            >
              <TableSortLabel
                component="span"
                active={orderBy === headCell.id}
                direction={orderBy === headCell.id ? (order ?? 'asc') : 'asc'}
                onClick={createSortHandler(headCell.id)}
              >
                {headCell.label}
                {orderBy === headCell.id ? (
                  <Box component="span" sx={visuallyHidden as any}>
                    {order === 'desc' ? 'sorted descending' : 'sorted ascending'}
                  </Box>
                ) : null}
              </TableSortLabel>
            </TableCell>
          ))}
        {numSelected <= 0 && (
          <TableCell sortDirection={false} align="center" sx={{ pr: 3 }}>
            <Typography
              variant="subtitle1"
              sx={{ color: theme.palette.mode === 'dark' ? 'grey.600' : 'grey.900' }}
            >
              Action
            </Typography>
          </TableCell>
        )}
      </TableRow>
    </TableHead>
  );
}

// ==============================|| ORDER LIST ||============================== //

const OrderList = () => {
  const theme = useTheme();
  
  // Enterprise Pattern: Context hooks
  const customerContext = useCustomer();
  const notificationContext = useNotifications();

  // Enterprise Pattern: Generic table hook
  const table = useTableLogic<Order>({
    data: customerContext.state.orders,
    searchFields: ['name', 'company', 'type', 'qty', 'id'],
    defaultOrderBy: 'id',
    rowIdentifier: 'name',
  });

  // Enterprise Pattern: Async operation with retry and notifications
  const { execute: loadOrders } = useAsyncOperation(async () => {
    await customerContext.getOrders();
    return true as const;
  }, {
    retryCount: 2,
    retryDelay: 500,
    onError: () => {
      notificationContext.showNotification({
        message: 'Failed to load orders',
        variant: 'error',
        alert: { color: 'error', variant: 'filled' },
        close: true,
      });
    }
  });

  React.useEffect(() => {
    loadOrders();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <MainCard title="Order List" content={false}>
      <CardContent>
        <Grid container justifyContent="space-between" alignItems="center" spacing={2}>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon fontSize="small" />
                  </InputAdornment>
                ),
              }}
              onChange={table.handleSearch}
              placeholder="Search Order"
              value={table.search}
              size="small"
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }} sx={{ textAlign: 'right' }}>
            <Tooltip title="Copy">
              <IconButton size="large">
                <FileCopyIcon />
              </IconButton>
            </Tooltip>
            <Tooltip title="Print">
              <IconButton size="large">
                <PrintIcon />
              </IconButton>
            </Tooltip>
            <Tooltip title="Filter">
              <IconButton size="large">
                <FilterListIcon />
              </IconButton>
            </Tooltip>
          </Grid>
        </Grid>
      </CardContent>

      {/* table */}
      <TableContainer>
        <Table sx={{ minWidth: 750 }} aria-labelledby="tableTitle">
          <EnhancedTableHead
            numSelected={table.selected.length}
            order={table.order}
            orderBy={table.orderBy}
            onSelectAllClick={table.handleSelectAllClick}
            onRequestSort={table.handleRequestSort}
            rowCount={table.rows.length}
            selected={table.selected}
          />
          <TableBody>
            {table.sortedAndPaginatedRows
              .filter((row): row is Order => row !== undefined)
              .map((row, index) => {
                const isItemSelected = table.isSelected(row.name);
                const labelId = `enhanced-table-checkbox-${index}`;

                return (
                  <TableRow
                    hover
                    role="checkbox"
                    aria-checked={isItemSelected}
                    tabIndex={-1}
                    key={index}
                    selected={isItemSelected}
                  >
                    <TableCell
                      padding="checkbox"
                      sx={{ pl: 3 }}
                      onClick={_event => table.handleClick(_event, row.name)}
                    >
                      <Checkbox
                        color="primary"
                        checked={isItemSelected}
                        inputProps={{
                          'aria-labelledby': labelId,
                        }}
                      />
                    </TableCell>
                    <TableCell
                      component="th"
                      id={labelId}
                      scope="row"
                      onClick={_event => table.handleClick(_event, row.name)}
                      sx={{ cursor: 'pointer' }}
                    >
                      <Typography
                        variant="subtitle1"
                        sx={{ color: theme.palette.mode === 'dark' ? 'grey.600' : 'grey.900' }}
                      >
                        {' '}
                        #{row.id}{' '}
                      </Typography>
                    </TableCell>
                    <TableCell
                      component="th"
                      id={labelId}
                      scope="row"
                      onClick={_event => table.handleClick(_event, row.name)}
                      sx={{ cursor: 'pointer' }}
                    >
                      <Typography
                        variant="subtitle1"
                        sx={{ color: theme.palette.mode === 'dark' ? 'grey.600' : 'grey.900' }}
                      >
                        {' '}
                        {row.name}{' '}
                      </Typography>
                    </TableCell>
                    <TableCell>{row.company}</TableCell>
                    <TableCell>{row.type}</TableCell>
                    <TableCell align="right">{row.qty}</TableCell>
                    <TableCell align="center">{row.date}</TableCell>
                    <TableCell align="center">
                      {row.status === 1 && (
                        <Chip label="Complete" size="small" chipcolor="success" />
                      )}
                      {row.status === 2 && <Chip label="Pending" size="small" chipcolor="orange" />}
                      {row.status === 3 && (
                        <Chip label="Processing" size="small" chipcolor="primary" />
                      )}
                    </TableCell>
                    <TableCell align="center" sx={{ pr: 3 }}>
                      <IconButton color="primary" size="large" aria-label="view">
                        <VisibilityTwoToneIcon sx={{ fontSize: '1.3rem' }} />
                      </IconButton>
                      <IconButton color="secondary" size="large" aria-label="delete">
                        <EditTwoToneIcon sx={{ fontSize: '1.3rem' }} />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                );
              })}
            {table.emptyRows > 0 && (
              <TableRow
                style={{
                  height: 53 * table.emptyRows,
                }}
              >
                <TableCell colSpan={6} />
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* table pagination */}
      <TablePagination
        rowsPerPageOptions={[5, 10, 25]}
        component="div"
        count={table.rows.length}
        rowsPerPage={table.rowsPerPage}
        page={table.page}
        onPageChange={table.handleChangePage}
        onRowsPerPageChange={table.handleChangeRowsPerPage}
      />
    </MainCard>
  );
};

// Enterprise Pattern: Apply error boundary HOC
export default withErrorBoundary(OrderList);
