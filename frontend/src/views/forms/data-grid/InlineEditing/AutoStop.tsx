'use client';

// material-ui
import { Box, Stack } from '@mui/material';
import Select, { SelectChangeEvent } from '@mui/material/Select';
import { DataGrid, GridRenderCellParams, GridColDef, useGridApiContext } from '@mui/x-data-grid';

// project import
import CardSecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import CSVExport from 'views/forms/tables/tbl-exports';

function SelectEditInputCell(props: GridRenderCellParams) {
  const { id, value, field } = props;
  const apiRef = useGridApiContext();

  const handleChange = async (event: SelectChangeEvent) => {
    await apiRef.current.setEditCellValue({ id, field, value: event.target.value });
    apiRef.current.stopCellEditMode({ id, field });
  };

  return (
    <Select value={value} onChange={handleChange} size="small" sx={{ height: 1 }} native autoFocus>
      <option>Back-end Developer</option>
      <option>Front-end Developer</option>
      <option>UX Designer</option>
    </Select>
  );
}

const renderSelectEditInputCell: GridColDef['renderCell'] = params => {
  return <SelectEditInputCell {...params} />;
};

// ==============================|| AUTO STOP EDIT DATA GRID ||============================== //

export default function AutoStopEditComponent() {
  let headers: any = [];
  columns.map(item => {
    return headers.push({ label: item.headerName, key: item.field });
  });
  return (
    <MainCard
      content={false}
      title="With Auto Stop"
      secondary={
        <Stack direction="row" spacing={2} alignItems="center">
          <CSVExport data={rows} filename={'auto-stop-edit-data-grid-table.csv'} header={headers} />
          <CardSecondaryAction link="https://mui.com/x/react-data-grid/editing/#with-auto-stop" />
        </Stack>
      }
    >
      <Box sx={{ width: '100%' }}>
        <DataGrid hideFooter autoHeight rows={rows} columns={columns} />
      </Box>
    </MainCard>
  );
}

const columns = [
  {
    field: 'name',
    headerName: 'Name',
    flex: 0.75,
    minWidth: 120,
  },
  {
    field: 'role',
    headerName: 'Role',
    renderEditCell: renderSelectEditInputCell,
    editable: true,
    flex: 1,
    minWidth: 180,
  },
];

const rows = [
  {
    id: 1,
    name: 'Olivier',
    role: 'Back-end Developer',
  },
  {
    id: 2,
    name: 'Danail',
    role: 'UX Designer',
  },
  {
    id: 3,
    name: 'Matheus',
    role: 'Front-end Developer',
  },
];
