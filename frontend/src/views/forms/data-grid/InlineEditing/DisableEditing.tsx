'use client';

// material-ui
import { Box, Stack } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { DataGrid, GridColDef, GridRowsProp } from '@mui/x-data-grid';
import { randomCreatedDate, randomTraderName, randomUpdatedDate } from '@mui/x-data-grid-generator';

// project import
import CardSecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import CSVExport from 'views/forms/tables/tbl-exports';

// ==============================|| DISABLE DITING DATA GRID ||============================== //

export default function IsCellEditableGrid() {
  const theme = useTheme();
  let headers: any = [];
  columns.map(item => {
    return headers.push({ label: item.headerName, key: item.field });
  });
  return (
    <MainCard
      content={false}
      title="Disable Editing"
      secondary={
        <Stack direction="row" spacing={2} alignItems="center">
          <CSVExport
            data={rows}
            filename={'disable-editing-data-grid-table.csv'}
            header={headers}
          />
          <CardSecondaryAction link="https://mui.com/x/react-data-grid/editing/#disable-editing-of-specific-cells-within-a-row" />
        </Stack>
      }
    >
      <Box
        sx={{
          width: '100%',
          '& .MuiDataGrid-cell--editable': {
            bgcolor:
              theme.palette.mode === 'dark' ? theme.palette.success.dark + 30 : 'success.light',
          },
        }}
      >
        <DataGrid
          hideFooter
          autoHeight
          rows={rows}
          columns={columns}
          isCellEditable={params => params.row.age % 2 === 0}
        />
      </Box>
    </MainCard>
  );
}

const columns: GridColDef[] = [
  { field: 'name', headerName: 'Name', flex: 1, minWidth: 180, editable: true },
  {
    field: 'age',
    headerName: 'Age',
    type: 'number',
    editable: true,
    align: 'left',
    flex: 0.5,
    minWidth: 100,
    headerAlign: 'left',
  },
  {
    field: 'dateCreated',
    headerName: 'Date Created',
    type: 'date',
    flex: 0.75,
    minWidth: 180,
    editable: true,
  },
  {
    field: 'lastLogin',
    headerName: 'Last Login',
    type: 'dateTime',
    flex: 0.75,
    minWidth: 220,
    editable: true,
  },
];

const rows: GridRowsProp = [
  {
    id: 1,
    name: randomTraderName(),
    age: 25,
    dateCreated: randomCreatedDate(),
    lastLogin: randomUpdatedDate(),
  },
  {
    id: 2,
    name: randomTraderName(),
    age: 36,
    dateCreated: randomCreatedDate(),
    lastLogin: randomUpdatedDate(),
  },
  {
    id: 3,
    name: randomTraderName(),
    age: 19,
    dateCreated: randomCreatedDate(),
    lastLogin: randomUpdatedDate(),
  },
  {
    id: 4,
    name: randomTraderName(),
    age: 28,
    dateCreated: randomCreatedDate(),
    lastLogin: randomUpdatedDate(),
  },
  {
    id: 5,
    name: randomTraderName(),
    age: 23,
    dateCreated: randomCreatedDate(),
    lastLogin: randomUpdatedDate(),
  },
];
