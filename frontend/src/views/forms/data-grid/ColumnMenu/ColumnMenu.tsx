'use client';

// material-ui
import { Box, Stack } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import { useDemoData } from '@mui/x-data-grid-generator';

// project import
import CardSecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import CSVExport from 'views/forms/tables/tbl-exports';

// ==============================|| COLUMN MENU DATA GRID ||============================== //

export default function ColumnMenu() {
  const { data } = useDemoData({
    dataSet: 'Commodity',
    rowLength: 10,
    maxColumns: 8,
  });

  let headers: any = [];
  data.columns.map(item => {
    return headers.push({ label: item.headerName, key: item.field });
  });

  return (
    <MainCard
      content={false}
      title="Column Menu"
      secondary={
        <Stack direction="row" spacing={2} alignItems="center">
          <CSVExport
            data={data.rows}
            filename={'column-menu-data-grid-table.csv'}
            header={headers}
          />
          <CardSecondaryAction link="https://mui.com/x/react-data-grid/column-menu/#column-menu" />
        </Stack>
      }
    >
      <Box sx={{ width: '100%' }}>
        <DataGrid autoHeight hideFooter {...data} />
      </Box>
    </MainCard>
  );
}
