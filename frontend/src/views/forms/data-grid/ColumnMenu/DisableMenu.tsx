'use client';

// material-ui
import { Box, Stack } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import { useDemoData } from '@mui/x-data-grid-generator';

// project import
import CardSecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import CSVExport from 'views/forms/tables/tbl-exports';

// ==============================|| DISABLE MENU DATA GRID ||============================== //

export default function DisableColumnMenu() {
  const { data } = useDemoData({
    dataSet: 'Commodity',
    rowLength: 5,
    maxColumns: 8,
  });

  let headers: any = [];
  data.columns.map(item => {
    return headers.push({ label: item.headerName, key: item.field });
  });

  return (
    <MainCard
      content={false}
      title="Disable Column Menu"
      secondary={
        <Stack direction="row" spacing={2} alignItems="center">
          <CSVExport
            data={data.rows}
            filename={'disable-menu-data-grid-table.csv'}
            header={headers}
          />
          <CardSecondaryAction link="https://mui.com/x/react-data-grid/column-menu/#disable-column-menu" />
        </Stack>
      }
    >
      <Box sx={{ width: '100%' }}>
        <DataGrid autoHeight hideFooter {...data} disableColumnMenu />
      </Box>
    </MainCard>
  );
}
