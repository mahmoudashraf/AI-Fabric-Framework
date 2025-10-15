'use client';

// material-ui
import { Box, Grid, Stack  } from '@mui/material';
import { DataGrid, GridColDef, GridRowsProp } from '@mui/x-data-grid';
import { useState } from 'react';

// project import
import { gridSpacing } from 'constants/index';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import CSVExport from 'views/forms/tables/tbl-exports';

// table columns
export const columns: GridColDef[] = [
  { field: 'id', headerName: 'ID', width: 100 },
  {
    field: 'fullName',
    headerName: 'Full name',
    description: 'This column has a value getter and is not sortable.',
    sortable: false,
    flex: 2,
    minWidth: 160,
    valueGetter: (_value, row) => `${row.firstName || ''} ${row.lastName || ''}`,
  },
  { field: 'firstName', headerName: 'First name', flex: 1, minWidth: 160 },
  { field: 'lastName', headerName: 'Last name', flex: 0.75, minWidth: 160 },
  {
    field: 'age',
    headerName: 'Age',
    flex: 0.5,
    minWidth: 90,
  },
];

// table rows
export const rows: GridRowsProp = [
  { id: 1, lastName: 'Snow', firstName: 'Jon', age: 35 },
  { id: 2, lastName: 'Lancaster', firstName: 'Cersei', age: 42 },
  { id: 3, lastName: 'Lancaster', firstName: 'Jaime', age: 45 },
  { id: 4, lastName: 'Stark', firstName: 'Arya', age: 16 },
  { id: 5, lastName: 'Targaryen', firstName: 'Daenerys', age: null },
  { id: 6, lastName: 'Melisandre', firstName: null, age: 150 },
  { id: 7, lastName: 'Clifford', firstName: 'Ferrara', age: 44 },
  { id: 8, lastName: 'Frances', firstName: 'Rossini', age: 36 },
  { id: 9, lastName: 'Roxie', firstName: 'Harvey', age: 65 },
];

function TableDataGrid({ Selected }: { Selected: any }) {
  return (
    <Box sx={{ width: '100%' }}>
      <DataGrid
        rows={rows}
        columns={columns}
        autoHeight
        initialState={{
          pagination: {
            paginationModel: {
              pageSize: 5,
            },
          },
        }}
        pageSizeOptions={[5]}
        checkboxSelection
        onRowSelectionModelChange={newSelectionModel => {
          const selectedIDs = new Set(newSelectionModel as unknown as (string | number)[]);
          const selectedRowData = rows.filter(row => selectedIDs.has(row.id));
          Selected(selectedRowData);
        }}
      />
    </Box>
  );
}

// ==============================|| TABLE - BASIC DATA GRID ||============================== //

export default function DataGridBasic() {
  let headers: any = [];
  columns.map(item => {
    return headers.push({ label: item.headerName, key: item.field });
  });

  const [selectedValue, setSelectedValue] = useState([]);
  const handlerClick = (data: any) => {
    setSelectedValue(data);
  };

  let NewValue = selectedValue.length > 0 ? selectedValue : rows;

  return (
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12 }}>
        <MainCard
          content={false}
          title="Basic Data Grid"
          secondary={
            <Stack direction="row" spacing={2} alignItems="center">
              <CSVExport data={NewValue} filename={'data-grid-table.csv'} header={headers} />
              <SecondaryAction link="https://material-ui.com/components/data-grid/" />
            </Stack>
          }
        >
          <TableDataGrid Selected={handlerClick} />
        </MainCard>
      </Grid>
    </Grid>
  );
}
