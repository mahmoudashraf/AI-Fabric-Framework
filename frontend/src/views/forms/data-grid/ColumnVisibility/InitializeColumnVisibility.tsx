'use client';

// material-ui
import { Box, Stack } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { DataGrid } from '@mui/x-data-grid';
import { useDemoData } from '@mui/x-data-grid-generator';

// project import
import CardSecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import CSVExport from 'views/forms/tables/tbl-exports';

// ==============================|| INITIALIZE COLUMN VISIBILITY DATA GRID ||============================== //

export default function VisibleColumnsModelInitialState() {
  const theme = useTheme();
  const { data, loading } = useDemoData({
    dataSet: 'Commodity',
    rowLength: 20,
    maxColumns: 20,
  });

  let headers: any = [];
  data.columns.map(item => {
    return headers.push({ label: item.headerName, key: item.field });
  });

  return (
    <MainCard
      content={false}
      title="Initialize Column Visibility"
      secondary={
        <Stack direction="row" spacing={2} alignItems="center">
          <CSVExport
            data={data.rows}
            filename={'column-visibility-data-grid-table.csv'}
            header={headers}
          />
          <CardSecondaryAction link="https://mui.com/x/react-data-grid/column-visibility/#initialize-the-visible-columns" />
        </Stack>
      }
    >
      <Box
        sx={{
          width: '100%',
          '& .MuiDataGrid-root': {
            '& .MuiDataGrid-cell--withRenderer > .positive': {
              color:
                theme.palette.mode === 'dark'
                  ? theme.palette.success.dark
                  : theme.palette.success.main,
            },
            '& .MuiDataGrid-cell--withRenderer > .negative': {
              color:
                theme.palette.mode === 'dark' ? theme.palette.error.dark : theme.palette.error.main,
            },
          },
        }}
      >
        <DataGrid
          {...data}
          loading={loading}
          autoHeight
          hideFooterSelectedRowCount
          initialState={{
            ...data.initialState,
            pagination: {
              paginationModel: {
                pageSize: 5,
              },
            },
            columns: {
              ...data.initialState?.columns,
              columnVisibilityModel: {
                id: false,
                brokerId: false,
                status: false,
              },
            },
          }}
        />
      </Box>
    </MainCard>
  );
}
