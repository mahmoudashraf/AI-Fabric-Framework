'use client';

// material-ui
import { Box, Button, Stack } from '@mui/material';
import {
  GridColDef,
  GridRowsProp,
  DataGrid,
  GridRowId,
  GridCellModes,
  GridEventListener,
  GridCellModesModel,
} from '@mui/x-data-grid';
import { randomCreatedDate, randomTraderName, randomUpdatedDate } from '@mui/x-data-grid-generator';
import * as React from 'react';

// project import
import CardSecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import CSVExport from 'views/forms/tables/tbl-exports';

interface SelectedCellParams {
  id: GridRowId;
  field: string;
}

// ==============================|| CONTROLLED EDITING DATA GRID ||============================== //

export default function StartEditButtonGrid() {
  const [selectedCellParams, setSelectedCellParams] = React.useState<SelectedCellParams | null>(
    null
  );
  const [cellModesModel, setCellModesModel] = React.useState<GridCellModesModel>({});

  const handleCellFocus = React.useCallback((event: React.FocusEvent<HTMLDivElement>) => {
    const row = event.currentTarget.parentElement;
    const id = row!.dataset.id!;
    const field = event.currentTarget.dataset.field!;
    setSelectedCellParams({ id, field });
  }, []);

  const cellMode = React.useMemo(() => {
    if (!selectedCellParams) {
      return 'view';
    }
    const { id, field } = selectedCellParams;
    return cellModesModel[id]?.[field]?.mode || 'view';
  }, [cellModesModel, selectedCellParams]);

  const handleCellKeyDown = React.useCallback<GridEventListener<'cellKeyDown'>>(
    (_params, event) => {
      if (cellMode === 'edit') {
        // Prevents calling event.preventDefault() if Tab is pressed on a cell in edit mode
        event.defaultMuiPrevented = true;
      }
    },
    [cellMode]
  );

  const handleCellEditStop = React.useCallback<GridEventListener<'cellEditStop'>>(
    (_params, event) => {
      event.defaultMuiPrevented = true;
    },
    []
  );

  let headers: any = [];
  columns.map(item => {
    return headers.push({ label: item.headerName, key: item.field });
  });

  const handleSaveOrEdit = () => {
    if (!selectedCellParams) {
      return;
    }
    const { id, field } = selectedCellParams;
    if (cellMode === 'edit') {
      setCellModesModel({
        ...cellModesModel,
        [id]: { ...cellModesModel[id], [field]: { mode: GridCellModes.View } },
      });
    } else {
      setCellModesModel({
        ...cellModesModel,
        [id]: { ...cellModesModel[id], [field]: { mode: GridCellModes.Edit } },
      });
    }
  };

  const handleCancel = () => {
    if (!selectedCellParams) {
      return;
    }
    const { id, field } = selectedCellParams;
    setCellModesModel({
      ...cellModesModel,
      [id]: {
        ...cellModesModel[id],
        [field]: { mode: GridCellModes.View, ignoreModifications: true },
      },
    });
  };

  const handleMouseDown = (event: React.MouseEvent) => {
    // Keep the focus in the cell
    event.preventDefault();
  };

  return (
    <MainCard
      content={false}
      title="Controlled Editing"
      secondary={
        <Stack direction="row" spacing={2} alignItems="center">
          <Box sx={{ px: 1 }}>
            <Button
              onClick={handleSaveOrEdit}
              onMouseDown={handleMouseDown}
              disabled={!selectedCellParams}
              variant="outlined"
            >
              {cellMode === 'edit' ? 'Save' : 'Edit'}
            </Button>
            <Button
              onClick={handleCancel}
              onMouseDown={handleMouseDown}
              disabled={cellMode === 'view'}
              variant="outlined"
              sx={{ ml: 1 }}
            >
              Cancel
            </Button>
          </Box>
          <CSVExport data={rows} filename={'controlled-data-grid-table.csv'} header={headers} />
          <CardSecondaryAction link="https://mui.com/x/react-data-grid/editing/#controlled-model" />
        </Stack>
      }
    >
      <Box sx={{ width: '100%' }}>
        <DataGrid
          rows={rows}
          columns={columns}
          hideFooter
          autoHeight
          onCellKeyDown={handleCellKeyDown}
          cellModesModel={cellModesModel}
          onCellEditStop={handleCellEditStop}
          onCellModesModelChange={model => setCellModesModel(model)}
          slotProps={{
            cell: {
              onFocus: handleCellFocus,
            },
          }}
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
    minWidth: 180,
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
