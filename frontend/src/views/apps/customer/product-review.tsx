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
  Rating,
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
import ReviewEdit from 'components/application/customer/ProductReview/ReviewEdit';
import { useCustomer } from 'contexts/CustomerContext';
import { useNotifications } from 'contexts/NotificationContext';
import { ProductReview } from 'types/customer';
import MainCard from 'ui-component/cards/MainCard';
import Chip from 'ui-component/extended/Chip';

// Enterprise hooks
import { useTableLogic, useAsyncOperation } from '@/hooks/enterprise';

// types
import {
  EnhancedTableHeadProps,
  EnhancedTableToolbarProps,
  HeadCell,
} from 'types';

// table header options
const headCells: HeadCell[] = [
  {
    id: 'name',
    numeric: false,
    label: 'Product Name',
    align: 'left',
  },
  {
    id: 'author',
    numeric: true,
    label: 'Author',
    align: 'left',
  },
  {
    id: 'review',
    numeric: true,
    label: 'Review',
    align: 'left',
  },
  {
    id: 'rating',
    numeric: true,
    label: 'Rating',
    align: 'center',
  },
  {
    id: 'date',
    numeric: true,
    label: 'Date',
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

interface ProReviewEnhancedTableHeadProps extends EnhancedTableHeadProps {
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
}: ProReviewEnhancedTableHeadProps) {
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

// ==============================|| PRODUCT REVIEW LIST ||============================== //

const ProductReviewList = () => {
  const theme = useTheme();
  
  // Enterprise Pattern: Context hooks
  const customerContext = useCustomer();
  const notificationContext = useNotifications();

  // open dialog to edit review
  const [open, setOpen] = React.useState(false);
  const handleClickOpenDialog = () => {
    setOpen(true);
  };
  const handleCloseDialog = () => {
    setOpen(false);
  };

  // Get product reviews data from Context API
  const productreviews = customerContext.state.productreviews;

  // Enterprise Pattern: Generic table logic hook
  const table = useTableLogic<ProductReview>({
    data: productreviews,
    searchFields: ['name', 'author', 'review'],
    defaultOrderBy: 'name',
    defaultRowsPerPage: 5,
    rowIdentifier: 'name',
  });

  // Enterprise Pattern: Async operation with retry and notifications
  const { execute: loadReviews } = useAsyncOperation(
    async () => {
      await customerContext.getProductReviews();
      return true as const;
    },
    {
      retryCount: 2,
      retryDelay: 500,
      onError: () => {
        notificationContext.showNotification({
          message: 'Failed to load product reviews',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      },
    }
  );

  React.useEffect(() => {
    loadReviews();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <MainCard title="Product Review" content={false}>
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
            onRequestSort={table.handleRequestSort as any}
            rowCount={table.rows.length}
            selected={table.selected as string[]}
          />
          <TableBody>
            {table.rows.map((row, index) => {
                const isItemSelected = table.selected.includes(row.name);
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
                      onClick={() => table.handleRowClick(row.name)}
                      sx={{ pl: 3 }}
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
                      onClick={() => table.handleRowClick(row.name)}
                      sx={{ cursor: 'pointer' }}
                    >
                      <Typography
                        variant="body2"
                        sx={{ color: theme.palette.mode === 'dark' ? 'grey.600' : 'grey.900' }}
                      >
                        {row.name}
                      </Typography>
                    </TableCell>
                    <TableCell>{row.author}</TableCell>
                    <TableCell>{row.review}</TableCell>
                    <TableCell align="center">
                      <Rating name="read-only" value={row.rating} precision={0.5} readOnly />
                    </TableCell>
                    <TableCell align="center">{row.date}</TableCell>
                    <TableCell align="center">
                      {row.status === 1 && (
                        <Chip label="Complete" chipcolor="success" size="small" />
                      )}
                      {row.status === 2 && (
                        <Chip label="Processing" chipcolor="orange" size="small" />
                      )}
                      {row.status === 3 && (
                        <Chip label="Confirm" chipcolor="primary" size="small" />
                      )}
                    </TableCell>
                    <TableCell align="center" sx={{ pr: 3 }}>
                      <IconButton color="primary" size="large" aria-label="view">
                        <VisibilityTwoToneIcon sx={{ fontSize: '1.3rem' }} />
                      </IconButton>
                      <IconButton
                        color="secondary"
                        onClick={handleClickOpenDialog}
                        size="large"
                        aria-label="delete"
                      >
                        <EditTwoToneIcon sx={{ fontSize: '1.3rem' }} />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                );
              })}
            {table.page > 0 && Math.max(0, (1 + table.page) * table.rowsPerPage - table.rows.length) > 0 && (
              <TableRow
                style={{
                  height: 53 * Math.max(0, (1 + table.page) * table.rowsPerPage - table.rows.length),
                }}
              >
                <TableCell colSpan={6} />
              </TableRow>
            )}
          </TableBody>
        </Table>

        {/* review edit dialog */}
        <ReviewEdit open={open} handleCloseDialog={handleCloseDialog} />
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
export default withErrorBoundary(ProductReviewList);
