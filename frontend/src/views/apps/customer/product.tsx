'use client';

import AddIcon from '@mui/icons-material/AddTwoTone';
import DeleteIcon from '@mui/icons-material/Delete';
import FileCopyIcon from '@mui/icons-material/FileCopyTwoTone';
import FilterListIcon from '@mui/icons-material/FilterListTwoTone';
import MoreHorizOutlinedIcon from '@mui/icons-material/MoreHorizOutlined';
import PrintIcon from '@mui/icons-material/PrintTwoTone';
import SearchIcon from '@mui/icons-material/Search';
import { Box,
  CardContent,
  Checkbox,
  Fab,
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

// material-ui

// project imports
import ProductAdd from 'components/application/customer/Product/ProductAdd';
import { useCustomer } from 'contexts/CustomerContext';
import { useNotifications } from 'contexts/NotificationContext';
import { useTableLogic } from '@/hooks/useTableLogic';
import { useAsyncOperation } from '@/hooks/enterprise';

// Using Context API
import { Product } from 'types/customer';
import MainCard from 'ui-component/cards/MainCard';

// types
import {
  HeadCell,
  EnhancedTableHeadProps,
  EnhancedTableToolbarProps,
} from 'types';

// assets

// Note: Table sorting logic moved to useTableLogic hook

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
    label: 'Product Name',
    align: 'left',
  },
  {
    id: 'category',
    numeric: false,
    label: 'Category',
    align: 'left',
  },
  {
    id: 'price',
    numeric: true,
    label: 'Price',
    align: 'right',
  },
  {
    id: 'date',
    numeric: true,
    label: 'Date',
    align: 'center',
  },
  {
    id: 'qty',
    numeric: true,
    label: 'QTY',
    align: 'right',
  },
];

// ==============================|| TABLE HEADER TOOLBAR ||============================== //

interface ProEnhancedTableHeadProps extends EnhancedTableHeadProps {
  selected: string[];
}

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

function EnhancedTableHead({
  onSelectAllClick,
  order,
  orderBy,
  numSelected,
  rowCount,
  onRequestSort,
  selected,
}: ProEnhancedTableHeadProps) {
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
          <TableCell padding="none" colSpan={7}>
            <EnhancedTableToolbar numSelected={selected.length} />
          </TableCell>
        )}
        {numSelected <= 0 &&
          headCells.map(headCell => (
            <TableCell
              key={headCell.id}
              align={headCell.align || 'left'}
              padding={headCell.disablePadding ? 'none' : 'normal'}
              sortDirection={orderBy === headCell.id ? (order === 'asc' ? 'asc' : 'desc') : false}
            >
              <TableSortLabel
                active={orderBy === headCell.id}
                direction={orderBy === headCell.id ? (order === 'asc' ? 'asc' : 'desc') : 'asc'}
                onClick={createSortHandler(headCell.id)}
              >
                {headCell.label}
                {orderBy === headCell?.id ? (
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

// ==============================|| PRODUCT LIST ||============================== //

const ProductList = () => {
  const theme = useTheme();
  
  // Using Context API
  const customerContext = useCustomer();
  const notificationContext = useNotifications();
  
  // Use Context API directly
  
  // show a right sidebar when clicked on new product
  const [open, setOpen] = React.useState(false);
  const handleClickOpenDialog = () => {
    setOpen(true);
  };
  const handleCloseDialog = () => {
    setOpen(false);
  };

  // Get products data from Context API
  const products = customerContext.state.products;

  // Enterprise Pattern: Async operation with retry and notifications
  const { execute: loadProducts } = useAsyncOperation(
    async () => {
      await customerContext.getProducts();
      return true as const;
    },
    {
      retryCount: 2,
      retryDelay: 500,
      onError: () => {
        notificationContext.showNotification({
          message: 'Failed to load products',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      },
    }
  );

  React.useEffect(() => {
    loadProducts();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Enterprise Pattern: Use generic table logic hook
  const table = useTableLogic<Product>({
    data: products,
    searchFields: ['name', 'category', 'price', 'qty', 'id'],
    defaultOrderBy: 'id',
    defaultRowsPerPage: 5,
    rowIdentifier: 'name',
  });

  return (
    <MainCard title="Product List" content={false}>
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
              placeholder="Search Product"
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

            {/* product add & dialog */}
            <Tooltip title="Add Product">
              <Fab
                color="primary"
                size="small"
                onClick={handleClickOpenDialog}
                sx={{ boxShadow: 'none', ml: 1, width: 32, height: 32, minHeight: 32 }}
              >
                <AddIcon fontSize="small" />
              </Fab>
            </Tooltip>
            <ProductAdd open={open} handleCloseDialog={handleCloseDialog} />
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
              .map((row, index) => {
                /** Make sure no display bugs if row isn't an OrderData object */
                if (typeof row === 'number' || !row) return null;
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
                      align="center"
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
                    <TableCell>{row.category}</TableCell>
                    <TableCell align="right">{row.price}$</TableCell>
                    <TableCell align="center">{row.date}</TableCell>
                    <TableCell align="right">{row.qty}</TableCell>
                    <TableCell align="center" sx={{ pr: 3 }}>
                      <IconButton size="large" aria-label="more options">
                        <MoreHorizOutlinedIcon sx={{ fontSize: '1.3rem' }} />
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
import { withErrorBoundary } from '@/components/enterprise';
export default withErrorBoundary(ProductList);
