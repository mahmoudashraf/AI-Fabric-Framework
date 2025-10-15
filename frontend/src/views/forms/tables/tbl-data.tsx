'use client';

import DeleteIcon from '@mui/icons-material/Delete';
import {
  Box,
  Checkbox,
  IconButton,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableSortLabel,
  TableRow,
  Toolbar,
  Tooltip,
  Typography,
  Stack,
} from '@mui/material';
import { visuallyHidden } from '@mui/utils';
import React from 'react';

// material-ui

// project imports
import { useTableLogic } from '@/hooks/enterprise';
import {
  EnhancedTableHeadProps,
  HeadCell,
} from 'types';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';

import { header } from './tbl-basic';
import CSVExport from './tbl-exports';

// types

// assets

// table data
type CreateDataType = {
  name: string;
  calories: number;
  fat: number;
  carbs: number;
  protein: number;
};
function createData(name: string, calories: number, fat: number, carbs: number, protein: number) {
  return {
    name,
    calories,
    fat,
    carbs,
    protein,
  };
}

const rows: CreateDataType[] = [
  createData('Cupcake', 305, 3.7, 67, 4.3),
  createData('Donut', 452, 25.0, 51, 4.9),
  createData('Eclair', 262, 16.0, 24, 6.0),
  createData('Frozen yoghurt', 159, 6.0, 24, 4.0),
  createData('Gingerbread', 356, 16.0, 49, 3.9),
  createData('Honeycomb', 408, 3.2, 87, 6.5),
  createData('Ice cream sandwich', 237, 9.0, 37, 4.3),
  createData('Jelly Bean', 375, 0.0, 94, 0.0),
  createData('KitKat', 518, 26.0, 65, 7.0),
  createData('Lollipop', 392, 0.2, 98, 0.0),
  createData('Marshmallow', 318, 0, 81, 2.0),
  createData('Nougat', 360, 19.0, 9, 37.0),
  createData('Oreo', 437, 18.0, 63, 4.0),
];

// table header
const headCells: HeadCell[] = [
  {
    id: 'name',
    numeric: false,
    disablePadding: true,
    label: 'Dessert (100g serving)',
  },
  {
    id: 'calories',
    numeric: true,
    disablePadding: false,
    label: 'Calories',
  },
  {
    id: 'fat',
    numeric: true,
    disablePadding: false,
    label: 'Fat (g)',
  },
  {
    id: 'carbs',
    numeric: true,
    disablePadding: false,
    label: 'Carbs (g)',
  },
  {
    id: 'protein',
    numeric: true,
    disablePadding: false,
    label: 'Protein (g)',
  },
];

// ==============================|| TABLE - HEADER ||============================== //

interface TableDataEnhancedTableHead extends EnhancedTableHeadProps {}

function EnhancedTableHead({
  onSelectAllClick,
  order,
  orderBy,
  numSelected,
  rowCount,
  onRequestSort,
}: TableDataEnhancedTableHead) {
  const createSortHandler = (property: string) => (_event: React.SyntheticEvent) => {
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
        {headCells.map(headCell => (
          <TableCell
            key={headCell.id}
            align={headCell.numeric ? 'right' : 'left'}
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
      </TableRow>
    </TableHead>
  );
}

// ==============================|| TABLE - HEADER TOOLBAR ||============================== //

const EnhancedTableToolbar = ({ numSelected }: { numSelected: number }) => (
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
      <Typography color="inherit" variant="subtitle1">
        {numSelected} selected
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
          <DeleteIcon />
        </IconButton>
      </Tooltip>
    )}
  </Toolbar>
);

// ==============================|| TABLE - DATA TABLE ||============================== //

function EnhancedTable() {
  const [dense] = React.useState(false);
  const [selectedValue, setSelectedValue] = React.useState<CreateDataType[]>([]);

  // Enterprise Pattern: Generic table logic hook
  const table = useTableLogic<CreateDataType>({
    data: rows,
    searchFields: ['name', 'calories', 'fat', 'carbs', 'protein'],
    defaultOrderBy: 'calories',
    defaultRowsPerPage: 5,
    rowIdentifier: 'name',
  });

  // Track selected row data for export
  React.useEffect(() => {
    const selectedRowData = rows.filter(row => table.selected.includes(row.name));
    setSelectedValue(selectedRowData);
  }, [table.selected]);

  return (
    <MainCard
      content={false}
      title="Data Tables"
      secondary={
        <Stack direction="row" spacing={2} alignItems="center">
          <CSVExport
            data={selectedValue.length > 0 ? selectedValue : rows}
            filename={'data-tables.csv'}
            header={header}
          />
          <SecondaryAction link="https://next.material-ui.com/components/tables/" />
        </Stack>
      }
    >
      <Paper sx={{ width: '100%', mb: 2 }}>
        <EnhancedTableToolbar numSelected={table.selected.length} />

        {/* table */}
        <TableContainer>
          <Table
            sx={{ minWidth: 750 }}
            aria-labelledby="tableTitle"
            size={dense ? 'small' : 'medium'}
          >
            <EnhancedTableHead
              numSelected={table.selected.length}
              order={table.order}
              orderBy={table.orderBy}
              onSelectAllClick={table.handleSelectAllClick}
              onRequestSort={table.handleRequestSort as any}
              rowCount={table.rows.length}
            />
            <TableBody>
              {table.rows.map((row, index) => {
                  const isItemSelected = table.selected.includes(row.name);
                  const labelId = `enhanced-table-checkbox-${index}`;
                  return (
                    <TableRow
                      hover
                      onClick={() => table.handleRowClick(row.name)}
                      role="checkbox"
                      aria-checked={isItemSelected}
                      tabIndex={-1}
                      key={row.name}
                      selected={isItemSelected}
                    >
                      <TableCell padding="checkbox" sx={{ pl: 3 }}>
                        <Checkbox
                          color="primary"
                          checked={isItemSelected}
                          inputProps={{
                            'aria-labelledby': labelId,
                          }}
                        />
                      </TableCell>
                      <TableCell component="th" id={labelId} scope="row" padding="none">
                        {row.name}
                      </TableCell>
                      <TableCell align="right">{row.calories}</TableCell>
                      <TableCell align="right">{row.fat}</TableCell>
                      <TableCell align="right">{row.carbs}</TableCell>
                      <TableCell sx={{ pr: 3 }} align="right">
                        {row.protein}
                      </TableCell>
                    </TableRow>
                  );
                })}
              {table.page > 0 && Math.max(0, (1 + table.page) * table.rowsPerPage - table.rows.length) > 0 && (
                <TableRow
                  style={{
                    height: (dense ? 33 : 53) * Math.max(0, (1 + table.page) * table.rowsPerPage - table.rows.length),
                  }}
                >
                  <TableCell colSpan={6} />
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>

        {/* table data */}
        <TablePagination
          rowsPerPageOptions={[5, 10, 25]}
          component="div"
          count={table.rows.length}
          rowsPerPage={table.rowsPerPage}
          page={table.page}
          onPageChange={table.handleChangePage}
          onRowsPerPageChange={table.handleChangeRowsPerPage}
        />
      </Paper>
    </MainCard>
  );
}

// Enterprise Pattern: Apply error boundary HOC
import { withErrorBoundary } from '@/components/enterprise';
export default withErrorBoundary(EnhancedTable);
