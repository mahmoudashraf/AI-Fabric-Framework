'use client';

// material-ui
import { Box, Stack } from '@mui/material';
import { DataGrid, GridRowModel, GridColDef, GridRowId, GridRowsProp } from '@mui/x-data-grid';
import { randomCreatedDate, randomTraderName, randomUpdatedDate } from '@mui/x-data-grid-generator';
import { useCallback } from 'react';

import { useNotifications } from 'contexts/NotificationContext';

// Using Context API
import CardSecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import CSVExport from 'views/forms/tables/tbl-exports';

interface User {
  name: string;
  age: number;
  id: GridRowId;
  dateCreated: Date;
  lastLogin: Date;
}

const useFakeMutation = () => {
  return useCallback(
    (user: Partial<User>) =>
      new Promise<Partial<User>>((resolve, reject) => {
        setTimeout(() => {
          if (user.name?.trim() === '') {
            reject(new Error("Error while saving user: name can't be empty."));
          } else {
            resolve({ ...user, name: user.name?.toUpperCase() ?? '' });
          }
        }, 200);
      }),
    []
  );
};

// ==============================|| SERVER VALIDATION COLUMN DATA GRID ||============================== //

export default function ServerSidePersistence() {
  const notificationContext = useNotifications();
  const mutateRow = useFakeMutation();

  const processRowUpdate = useCallback(
    async (newRow: GridRowModel) => {
      // Make the HTTP request to save in the backend
      const response = await mutateRow(newRow);
      notificationContext.showNotification({
        message: 'User Successfully Saved',
        variant: 'success',
        alert: {
          color: 'success',
          variant: 'filled',
        },
        close: true,
      });
      return response;
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [mutateRow]
  );

  const handleProcessRowUpdateError = useCallback((error: Error) => {
    notificationContext.showNotification({
      message: error.message,
      variant: 'error',
      alert: {
        color: 'error',
        variant: 'filled',
      },
      close: true,
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [notificationContext]);

  let headers: any = [];
  columns.map(item => {
    return headers.push({ label: item.headerName, key: item.field });
  });

  return (
    <MainCard
      content={false}
      title="Server-side Validation"
      secondary={
        <Stack direction="row" spacing={2} alignItems="center">
          <CSVExport
            data={rows}
            filename={'server-validation-data-grid-table.csv'}
            header={headers}
          />
          <CardSecondaryAction link="https://mui.com/x/react-data-grid/editing/#server-side-validation" />
        </Stack>
      }
    >
      <Box sx={{ width: '100%' }}>
        <DataGrid
          rows={rows}
          columns={columns}
          hideFooter
          autoHeight
          processRowUpdate={processRowUpdate}
          onProcessRowUpdateError={handleProcessRowUpdateError}
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
    flex: 1,
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
