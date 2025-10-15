'use client';

import AddIcon from '@mui/icons-material/AddTwoTone';
import DeleteIcon from '@mui/icons-material/Delete';
import FileCopyIcon from '@mui/icons-material/FileCopyTwoTone';
import FilterListIcon from '@mui/icons-material/FilterListTwoTone';
import MoreHorizOutlinedIcon from '@mui/icons-material/MoreHorizOutlined';
import PrintIcon from '@mui/icons-material/PrintTwoTone';
import SearchIcon from '@mui/icons-material/Search';
import { CardContent,
  Checkbox,
  Fab,
  Grid,
  IconButton,
  InputAdornment,
  Menu,
  MenuItem,
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
import Link from 'next/link';
import React from 'react';

// material-ui

// third-party
import { format } from 'date-fns';

// project imports
import { useProduct } from 'contexts/ProductContext';
import { useNotifications } from 'contexts/NotificationContext';
import { useTableLogic, useAsyncOperation } from '@/hooks/enterprise';
import { Products } from 'types/e-commerce';
import MainCard from 'ui-component/cards/MainCard';
import Avatar from 'ui-component/extended/Avatar';
import Chip from 'ui-component/extended/Chip';

// Using Context API

// types
import {
  HeadCell,
  EnhancedTableHeadProps,
  EnhancedTableToolbarProps,
} from 'types';

// assets

const prodImage = '/assets/images/e-commerce';

// table header options
const headCells: HeadCell[] = [
  {
    id: 'id',
    numeric: true,
    label: '#',
    align: 'center',
  },
  {
    id: 'name',
    numeric: false,
    label: 'Product Name',
    align: 'left',
  },
  {
    id: 'created',
    numeric: false,
    label: 'Created',
    align: 'left',
  },
  {
    id: 'price',
    numeric: true,
    label: 'Price',
    align: 'right',
  },
  {
    id: 'sale-price',
    numeric: true,
    label: 'Sale Price',
    align: 'right',
  },
  {
    id: 'status',
    numeric: true,
    label: 'Status',
    align: 'center',
  },
];

// ==============================|| TABLE HEADER TOOLBAR ||============================== //

const EnhancedTableToolbar = ({ numSelected }: EnhancedTableToolbarProps) => (
  <Toolbar
    sx={{
      p: 0,
      pl: 2,
      pr: 1,
      color: numSelected > 0 ? 'secondary.main' : 'inherit',
    }}
  >
    {numSelected > 0 ? (
      <Typography sx={{ flex: '1 1 100%' }} color="inherit" variant="h4" component="div">
        {numSelected} Selected
      </Typography>
    ) : (
      <Typography sx={{ flex: '1 1 100%' }} variant="h6" id="tableTitle" component="div">
        Nutrition
      </Typography>
    )}

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
}: EnhancedTableHeadProps) {
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
            <EnhancedTableToolbar numSelected={numSelected} />
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
                  <Typography component="span" sx={{ display: 'none' }}>
                    {order === 'desc' ? 'sorted descending' : 'sorted ascending'}
                  </Typography>
                ) : null}
              </TableSortLabel>
            </TableCell>
          ))}
        {numSelected <= 0 && (
          <TableCell sortDirection={false} align="center" sx={{ pr: 3 }}>
            Action
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
  const productContext = useProduct();
  const notificationContext = useNotifications();
  
  // Use Context API directly

  // Get products data from Context API
  const products = productContext.state.products;

  const [anchorEl, setAnchorEl] = React.useState<Element | (() => Element) | null | undefined>(
    null
  );

  const handleMenuClick = (event: React.MouseEvent<HTMLButtonElement> | undefined) => {
    setAnchorEl(event?.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  // Enterprise Pattern: Async operation with retry and notifications
  const { execute: loadProducts } = useAsyncOperation(
    async () => {
      await productContext.getProducts();
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
  type ProductRow = Omit<Products, 'id'> & { id: string | number };
  const table = useTableLogic<ProductRow>({
    data: products,
    searchFields: ['name', 'description', 'rating', 'salePrice', 'offerPrice', 'gender'],
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
                sx={{ boxShadow: 'none', ml: 1, width: 32, height: 32, minHeight: 32 }}
              >
                <AddIcon fontSize="small" />
              </Fab>
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
            onRequestSort={(e, p) => table.handleRequestSort(e as any, p as any)}
            rowCount={table.rows.length}
          />
          <TableBody>
            {table.rows
              .map((row, index) => {
                if (!row) return null;
                const isItemSelected = table.selected.includes(row.name as any);
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
                      onClick={() => table.handleRowClick(row.name as any)}
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
                      onClick={() => table.handleRowClick(row.name as any)}
                      sx={{ cursor: 'pointer' }}
                    >
                      <Avatar
                        src={row.image && `${prodImage}/${row.image}`}
                        size="md"
                        variant="rounded"
                        alt="product images"
                      />
                    </TableCell>
                    <TableCell component="th" id={labelId} scope="row" sx={{ cursor: 'pointer' }}>
                      <Typography
                        component={Link}
                        href={`/apps/e-commerce/product-details/${row.id}`}
                        variant="subtitle1"
                        sx={{
                          color:
                            theme.palette.mode === 'dark' ? theme.palette.grey[600] : 'grey.900',
                          textDecoration: 'none',
                        }}
                      >
                        {row.name}
                      </Typography>
                    </TableCell>
                    <TableCell>{format(new Date(row.created), 'E, MMM d yyyy')}</TableCell>
                    <TableCell align="right">${row.offerPrice}</TableCell>
                    <TableCell align="right">${row.salePrice}</TableCell>
                    <TableCell align="center">
                      <Chip
                        size="small"
                        label={row.isStock ? 'In Stock' : 'Out of Stock'}
                        chipcolor={row.isStock ? 'success' : 'error'}
                        sx={{ borderRadius: '4px', textTransform: 'capitalize' }}
                      />
                    </TableCell>
                    <TableCell align="center" sx={{ pr: 3 }}>
                      <IconButton onClick={handleMenuClick} size="large" aria-label="more option">
                        <MoreHorizOutlinedIcon
                          fontSize="small"
                          aria-controls="menu-popular-card-1"
                          aria-haspopup="true"
                          sx={{ color: 'grey.500' }}
                        />
                      </IconButton>
                      <Menu
                        id="menu-popular-card-1"
                        anchorEl={anchorEl}
                        keepMounted
                        open={Boolean(anchorEl)}
                        onClose={handleClose}
                        variant="selectedMenu"
                        anchorOrigin={{
                          vertical: 'bottom',
                          horizontal: 'right',
                        }}
                        transformOrigin={{
                          vertical: 'top',
                          horizontal: 'right',
                        }}
                        sx={{
                          '& .MuiMenu-paper': {
                            boxShadow: theme.customShadows.z1,
                          },
                        }}
                      >
                        <MenuItem onClick={handleClose}> Edit</MenuItem>
                        <MenuItem onClick={handleClose}> Delete</MenuItem>
                      </Menu>
                    </TableCell>
                  </TableRow>
                );
              })}
            {/* Intentionally omit empty row filler for simplicity */}
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
import { withErrorBoundary } from '@/components/enterprise/HOCs';
export default withErrorBoundary(ProductList);
