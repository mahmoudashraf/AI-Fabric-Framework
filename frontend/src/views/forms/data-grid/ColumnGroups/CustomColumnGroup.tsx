'use client';

import BuildIcon from '@mui/icons-material/Build';
import PersonIcon from '@mui/icons-material/Person';
import { Box, Stack } from '@mui/material';
import { styled, useTheme } from '@mui/material/styles';
import {
  DataGrid,
  GridColDef,
  GridColumnGroupHeaderParams,
  GridColumnGroupingModel,
} from '@mui/x-data-grid';
import { useState } from 'react';

// material-ui

// project import
import CardSecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import CSVExport from 'views/forms/tables/tbl-exports';

// assets

// table data
const columns: GridColDef[] = [
  { field: 'id', headerName: 'ID', width: 150 },
  {
    field: 'firstName',
    headerName: 'First name',
    flex: 1,
    minWidth: 150,
  },
  {
    field: 'lastName',
    headerName: 'Last name',
    flex: 1,
    minWidth: 150,
  },
  {
    field: 'age',
    headerName: 'Age',
    flex: 0.5,
    minWidth: 110,
  },
];

const rows = [
  { id: 1, lastName: 'Snow', firstName: 'Jon', age: 35 },
  { id: 2, lastName: 'Lannister', firstName: 'Cersei', age: 42 },
  { id: 3, lastName: 'Lannister', firstName: 'Jaime', age: 45 },
  { id: 4, lastName: 'Stark', firstName: 'Arya', age: 16 },
  { id: 5, lastName: 'Targaryen', firstName: 'Daenerys', age: null },
  { id: 6, lastName: 'Melisandre', firstName: null, age: 150 },
  { id: 7, lastName: 'Clifford', firstName: 'Ferrara', age: 44 },
  { id: 8, lastName: 'Frances', firstName: 'Rossini', age: 36 },
  { id: 9, lastName: 'Roxie', firstName: 'Harvey', age: 65 },
];

interface HeaderWithIconProps extends GridColumnGroupHeaderParams {
  icon: React.ReactNode;
}

const HeaderWithIconRoot = styled('div')(({ theme }) => ({
  overflow: 'hidden',
  display: 'flex',
  alignItems: 'center',
  '& span': {
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    marginRight: theme.spacing(0.5),
  },
}));

function HeaderWithIcon(props: HeaderWithIconProps) {
  const { icon, ...params } = props;

  return (
    <HeaderWithIconRoot>
      <span>{params.headerName ?? params.groupId}</span> {icon}
    </HeaderWithIconRoot>
  );
}

const columnGroupingModel: GridColumnGroupingModel = [
  {
    groupId: 'internal_data',
    headerName: 'Internal',
    description: '',
    renderHeaderGroup: params => (
      <HeaderWithIcon {...params} icon={<BuildIcon color="disabled" sx={{ fontSize: 16 }} />} />
    ),
    children: [{ field: 'id' }],
  },
  {
    groupId: 'character',
    description: 'Information about the character',
    headerName: 'Basic info',
    renderHeaderGroup: params => (
      <HeaderWithIcon {...params} icon={<PersonIcon color="disabled" sx={{ fontSize: 16 }} />} />
    ),
    children: [
      {
        groupId: 'naming',
        headerName: 'Names',
        headerClassName: 'my-super-theme--naming-group',
        children: [{ field: 'lastName' }, { field: 'firstName' }],
      },
      { field: 'age' },
    ],
  },
];

function CustomizationDemo({ Selected }: { Selected: any }) {
  const theme = useTheme();
  return (
    <Box
      sx={{
        width: '100%',
        '& .my-super-theme--naming-group': {
          bgcolor: theme.palette.mode === 'dark' ? 'secondary.main' : 'secondary.light',
        },
      }}
    >
      <DataGrid
        rows={rows}
        columns={columns}
        autoHeight
        checkboxSelection
        initialState={{
          pagination: {
            paginationModel: {
              pageSize: 5,
            },
          },
        }}
        pageSizeOptions={[5]}
        onRowSelectionModelChange={newSelectionModel => {
          const selectedIDs = new Set(newSelectionModel as unknown as (string | number)[]);
          const selectedRowData = rows.filter(row => selectedIDs.has(row.id));
          Selected(selectedRowData);
        }}
        columnGroupingModel={columnGroupingModel}
      />
    </Box>
  );
}

// ==============================|| CUSTOM COLUMN GROUP DATA GRID ||============================== //

export default function Customization() {
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
    <MainCard
      content={false}
      title="Custom Column Group"
      secondary={
        <Stack direction="row" spacing={2} alignItems="center">
          <CSVExport
            data={NewValue}
            filename={'custom-group-data-grid-table.csv'}
            header={headers}
          />
          <CardSecondaryAction link="https://mui.com/x/react-data-grid/column-groups/#customize-column-group" />
        </Stack>
      }
    >
      <CustomizationDemo Selected={handlerClick} />
    </MainCard>
  );
}
