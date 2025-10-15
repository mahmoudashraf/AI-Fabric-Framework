'use client';

import AddIcon from '@mui/icons-material/Add';
import CancelIcon from '@mui/icons-material/Close';
import DeleteIcon from '@mui/icons-material/DeleteOutlined';
import EditTwoToneIcon from '@mui/icons-material/EditTwoTone';
import SaveIcon from '@mui/icons-material/Save';
import { Box, Button, Stack } from '@mui/material';

// material-ui
import {
  GridRowsProp,
  GridRowModesModel,
  GridRowModes,
  DataGrid,
  GridColDef,
  GridActionsCellItem,
  GridEventListener,
  GridRowId,
  GridRowModel,
  GridRowEditStopReasons,
} from '@mui/x-data-grid';
import {
  randomCreatedDate,
  randomTraderName,
  randomId,
  randomArrayItem,
} from '@mui/x-data-grid-generator';
import * as React from 'react';

// project import
import CardSecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import CSVExport from 'views/forms/tables/tbl-exports';

// assets

// table data
const roles = ['Market', 'Finance', 'Development'];
const randomRole = () => {
  return randomArrayItem(roles);
};

const initialRows: GridRowsProp = [
  {
    id: randomId(),
    name: randomTraderName(),
    age: 25,
    joinDate: randomCreatedDate(),
    role: randomRole(),
  },
  {
    id: randomId(),
    name: randomTraderName(),
    age: 36,
    joinDate: randomCreatedDate(),
    role: randomRole(),
  },
  {
    id: randomId(),
    name: randomTraderName(),
    age: 19,
    joinDate: randomCreatedDate(),
    role: randomRole(),
  },
  {
    id: randomId(),
    name: randomTraderName(),
    age: 28,
    joinDate: randomCreatedDate(),
    role: randomRole(),
  },
  {
    id: randomId(),
    name: randomTraderName(),
    age: 23,
    joinDate: randomCreatedDate(),
    role: randomRole(),
  },
];

// ==============================|| FULL FEATURED DATA GRID ||============================== //

export default function FullFeaturedCrudGrid() {
  const [rows, setRows] = React.useState(initialRows);
  const [rowModesModel, setRowModesModel] = React.useState<GridRowModesModel>({});

  const handleRowEditStop: GridEventListener<'rowEditStop'> = (params, event) => {
    if (params.reason === GridRowEditStopReasons.rowFocusOut) {
      event.defaultMuiPrevented = true;
    }
  };

  const handleEditClick = (id: GridRowId) => () => {
    setRowModesModel({ ...rowModesModel, [id]: { mode: GridRowModes.Edit } });
  };

  const handleSaveClick = (id: GridRowId) => () => {
    setRowModesModel({ ...rowModesModel, [id]: { mode: GridRowModes.View } });
  };

  const handleDeleteClick = (id: GridRowId) => () => {
    setRows(rows.filter(row => row.id !== id));
  };

  const handleCancelClick = (id: GridRowId) => () => {
    setRowModesModel({
      ...rowModesModel,
      [id]: { mode: GridRowModes.View, ignoreModifications: true },
    });

    const editedRow = rows.find(row => row.id === id);
    if (editedRow!.isNew) {
      setRows(rows.filter(row => row.id !== id));
    }
  };

  const processRowUpdate = (newRow: GridRowModel) => {
    const updatedRow = { ...newRow, isNew: false };
    setRows(rows.map(row => (row.id === newRow.id ? updatedRow : row)));
    return updatedRow;
  };

  const handleRowModesModelChange = (newRowModesModel: GridRowModesModel) => {
    setRowModesModel(newRowModesModel);
  };

  const columns: GridColDef[] = [
    { field: 'name', headerName: 'Name', flex: 1, minWidth: 180, editable: true },
    {
      flex: 0.5,
      field: 'age',
      headerName: 'Age',
      type: 'number',
      minWidth: 100,
      align: 'left',
      headerAlign: 'left',
      editable: true,
    },
    {
      field: 'joinDate',
      headerName: 'Join date',
      type: 'date',
      flex: 0.75,
      minWidth: 180,
      editable: true,
    },
    {
      field: 'role',
      headerName: 'Department',
      flex: 0.75,
      minWidth: 220,
      editable: true,
      type: 'singleSelect',
      valueOptions: ['Market', 'Finance', 'Development'],
    },
    {
      field: 'actions',
      type: 'actions',
      headerName: 'Actions',
      flex: 0.75,
      minWidth: 100,
      cellClassName: 'actions',
      getActions: ({ id }) => {
        const isInEditMode = rowModesModel[id]?.mode === GridRowModes.Edit;

        if (isInEditMode) {
          return [
            <GridActionsCellItem
              key={`${id}-save`}
              icon={<SaveIcon color="secondary" sx={{ fontSize: '1.3rem' }} />}
              label="Save"
              onClick={handleSaveClick(id)}
              showInMenu={false}
            />,
            <GridActionsCellItem
              key={`${id}-cancel`}
              icon={<CancelIcon color="error" sx={{ fontSize: '1.3rem' }} />}
              label="Cancel"
              onClick={handleCancelClick(id)}
              showInMenu={false}
            />,
          ];
        }

        return [
          <GridActionsCellItem
            key={`${id}-edit`}
            icon={<EditTwoToneIcon color="secondary" sx={{ fontSize: '1.3rem' }} />}
            label="Edit"
            onClick={handleEditClick(id)}
            showInMenu={false}
          />,
          <GridActionsCellItem
            key={`${id}-delete`}
            icon={<DeleteIcon color="error" sx={{ fontSize: '1.3rem' }} />}
            label="Delete"
            onClick={handleDeleteClick(id)}
            showInMenu={false}
          />,
        ];
      },
    },
  ];

  let headers: any = [];
  columns.map(item => {
    return headers.push({ label: item.headerName, key: item.field });
  });

  const handleClick = () => {
    const id = randomId();
    setRows(oldRows => [...oldRows, { id, name: '', age: '', isNew: true }]);
    setRowModesModel(oldModel => ({
      ...oldModel,
      [id]: { mode: GridRowModes.Edit, fieldToFocus: 'name' },
    }));
  };

  return (
    <MainCard
      content={false}
      title="Full-featured CRUD"
      secondary={
        <Stack direction="row" spacing={2} alignItems="center">
          <Box>
            <Button color="primary" startIcon={<AddIcon />} onClick={handleClick}>
              Add record
            </Button>
          </Box>
          <CSVExport data={rows} filename={'full-featured-data-grid-table.csv'} header={headers} />
          <CardSecondaryAction link="https://mui.com/x/react-data-grid/editing/#full-featured-crud" />
        </Stack>
      }
    >
      <Box
        sx={{
          width: '100%',
          '& .MuiDataGrid-root': {
            '& .MuiDataGrid-cell--editing': {
              '& .MuiInputBase-root': {
                width: 150,
                '& .MuiSelect-select': {
                  pt: 0.75,
                  pb: 0.75,
                },
              },
            },
            '& .MuiDataGrid-row--editing': {
              boxShadow: 'none',
            },
          },
        }}
      >
        <DataGrid
          rows={rows}
          columns={columns}
          editMode="row"
          rowModesModel={rowModesModel}
          hideFooter
          autoHeight
          onRowModesModelChange={handleRowModesModelChange}
          onRowEditStop={handleRowEditStop}
          processRowUpdate={processRowUpdate}
          // Removed unsupported toolbar props in v7
        />
      </Box>
    </MainCard>
  );
}
