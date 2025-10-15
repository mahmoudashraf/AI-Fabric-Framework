'use client';

// material-ui
import { Box, Stack } from '@mui/material';
import { DataGrid, GridColDef, GridValueSetter } from '@mui/x-data-grid';

// project import
import CardSecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import CSVExport from 'views/forms/tables/tbl-exports';

function getFullName(_: unknown, row: any) {
  return `${row.firstName || ''} ${row.lastName || ''}`;
}

const setFullName: GridValueSetter = (value, row) => {
  const [firstName, lastName] = String(value ?? '').toString().split(' ');
  return { ...row, firstName, lastName } as any;
};

function parseFullName(value: any) {
  return String(value)
    .split(' ')
    .map(str => (str.length > 0 ? str[0]?.toUpperCase() + str.slice(1) : ''))
    .join(' ');
}

// ==============================|| VALUE PARSER & SETTER DATA GRID ||============================== //

export default function ValueParserSetterGrid() {
  let headers: any = [];
  columns.map(item => {
    return headers.push({ label: item.headerName, key: item.field });
  });

  return (
    <MainCard
      content={false}
      title="Value Parser & Setter"
      secondary={
        <Stack direction="row" spacing={2} alignItems="center">
          <CSVExport
            data={defaultRows}
            filename={'value-parser-data-grid-table.csv'}
            header={headers}
          />
          <CardSecondaryAction link="https://mui.com/x/react-data-grid/editing/#value-parser-and-value-setter" />
        </Stack>
      }
    >
      <Box sx={{ width: '100%' }}>
        <DataGrid hideFooter autoHeight rows={defaultRows} columns={columns} />
      </Box>
    </MainCard>
  );
}

const columns: GridColDef[] = [
  { field: 'firstName', headerName: 'First name', flex: 1, minWidth: 130, editable: true },
  { field: 'lastName', headerName: 'Last name', flex: 0.75, minWidth: 130, editable: true },
  {
    field: 'fullName',
    headerName: 'Full name',
    flex: 0.75,
    minWidth: 160,
    editable: true,
    valueGetter: getFullName,
    valueSetter: setFullName,
    valueParser: parseFullName,
    sortComparator: (v1, v2) => v1!.toString().localeCompare(v2!.toString()),
  },
];

const defaultRows = [
  { id: 1, lastName: 'Snow', firstName: 'Jon' },
  { id: 2, lastName: 'Lannister', firstName: 'Cersei' },
  { id: 3, lastName: 'Lannister', firstName: 'Jaime' },
  { id: 4, lastName: 'Stark', firstName: 'Arya' },
  { id: 5, lastName: 'Targaryen', firstName: 'Daenerys' },
];
