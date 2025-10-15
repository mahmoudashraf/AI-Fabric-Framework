'use client';

// material-ui
import {
  Box,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Stack,
  DialogContentText,
  Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { DataGrid, GridRowModel, GridColDef, GridRowId, GridRowsProp } from '@mui/x-data-grid';
import { randomCreatedDate, randomTraderName, randomUpdatedDate } from '@mui/x-data-grid-generator';
import * as React from 'react';

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
  return React.useCallback(
    (user: Partial<User>) =>
      new Promise<Partial<User>>((resolve, reject) => {
        setTimeout(() => {
          if (user.name?.trim() === '') {
            reject();
          } else {
            resolve(user);
          }
        }, 200);
      }),
    []
  );
};

function computeMutation(newRow: GridRowModel, oldRow: GridRowModel) {
  if (newRow.name !== oldRow.name) {
    return `Name from '${oldRow.name}' to '${newRow.name}'`;
  }
  if (newRow.age !== oldRow.age) {
    return `Age from '${oldRow.age || ''}' to '${newRow.age || ''}'`;
  }
  return null;
}

// ==============================|| CONFIRMATION AFTER EDIT DATA GRID ||============================== //

export default function AskConfirmationBeforeSave() {
  const theme = useTheme();
  const notificationContext = useNotifications();
  const mutateRow = useFakeMutation();
  const noButtonRef = React.useRef<HTMLButtonElement>(null);
  const [promiseArguments, setPromiseArguments] = React.useState<any>(null);

  let headers: any = [];
  columns.map(item => {
    return headers.push({ label: item.headerName, key: item.field });
  });

  const processRowUpdate = React.useCallback(
    (newRow: GridRowModel, oldRow: GridRowModel) =>
      new Promise<GridRowModel>((resolve, reject) => {
        const mutation = computeMutation(newRow, oldRow);
        if (mutation) {
          // Save the arguments to resolve or reject the promise later
          setPromiseArguments({ resolve, reject, newRow, oldRow });
        } else {
          resolve(oldRow); // Nothing was changed
        }
      }),
    []
  );

  const handleNo = () => {
    const { oldRow, resolve } = promiseArguments;
    resolve(oldRow); // Resolve with the old row to not update the internal state
    setPromiseArguments(null);
  };

  const handleYes = async () => {
    const { newRow, oldRow, reject, resolve } = promiseArguments;

    try {
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
      resolve(response);
      setPromiseArguments(null);
    } catch (error) {
      notificationContext.showNotification({
        message: 'Name can not be empty',
        variant: 'error',
        alert: {
          color: 'error',
          variant: 'filled',
        },
        close: true,
      });
      reject(oldRow);
      setPromiseArguments(null);
    }
  };

  const handleEntered = () => {
    // The `autoFocus` is not used because, if used, the same Enter that saves
    // the cell triggers "No". Instead, we manually focus the "No" button once
    // the dialog is fully open.
    // noButtonRef.current?.focus();
  };

  const renderConfirmDialog = () => {
    if (!promiseArguments) {
      return null;
    }

    const { newRow, oldRow } = promiseArguments;
    const mutation = computeMutation(newRow, oldRow);

    return (
      <Dialog
        maxWidth="xs"
        TransitionProps={{ onEntered: handleEntered }}
        open={!!promiseArguments}
      >
        <DialogTitle>Are you sure?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            <Typography
              variant="body2"
              component="span"
            >{`Pressing 'Yes' will change ${mutation}.`}</Typography>
          </DialogContentText>
        </DialogContent>
        <DialogActions sx={{ pr: 2.5 }}>
          <Button
            ref={noButtonRef}
            sx={{ color: theme.palette.error.dark }}
            autoFocus
            onClick={handleNo}
            color="secondary"
          >
            No
          </Button>
          <Button variant="contained" size="small" onClick={handleYes} autoFocus>
            Yes
          </Button>
        </DialogActions>
      </Dialog>
    );
  };

  return (
    <MainCard
      content={false}
      title="Confirmation After Edit"
      secondary={
        <Stack direction="row" spacing={2} alignItems="center">
          <CSVExport
            data={rows}
            filename={'confirmation-save-data-grid-table.csv'}
            header={headers}
          />
          <CardSecondaryAction link="https://mui.com/x/react-data-grid/editing/#confirm-before-saving" />
        </Stack>
      }
    >
      <Box sx={{ width: '100%' }}>
        {renderConfirmDialog()}
        <DataGrid
          hideFooter
          autoHeight
          rows={rows}
          columns={columns}
          processRowUpdate={processRowUpdate}
        />
      </Box>
    </MainCard>
  );
}

const columns: GridColDef[] = [
  { field: 'name', headerName: 'Name', flex: 1, minWidth: 180, editable: true },
  { field: 'age', headerName: 'Age', flex: 0.5, minWidth: 100, type: 'number', editable: true },
  {
    field: 'dateCreated',
    headerName: 'Date Created',
    type: 'date',
    flex: 0.75,
    minWidth: 180,
  },
  {
    field: 'lastLogin',
    headerName: 'Last Login',
    type: 'dateTime',
    flex: 0.75,
    minWidth: 220,
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
