'use client';

// material-ui
import { Box, Stack } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { DataGrid, GridColumnVisibilityModel } from '@mui/x-data-grid';
import { useDemoData } from '@mui/x-data-grid-generator';
import { useState } from 'react';

// project import
import CardSecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import CSVExport from 'views/forms/tables/tbl-exports';

// ==============================|| CONTROLLED COLUMN VISIBILITY DATA GRID ||============================== //

export default function VisibleColumnsModelControlled() {
  const theme = useTheme();
  const { data, loading } = useDemoData({
    dataSet: 'Commodity',
    rowLength: 20,
    maxColumns: 20,
  });

  const [columnVisibilityModel, setColumnVisibilityModel] = useState<GridColumnVisibilityModel>({
    id: false,
    brokerId: false,
    status: false,
  });

  let headers: any = [];
  data.columns.map(item => {
    return headers.push({ label: item.headerName, key: item.field });
  });

  return (
    <MainCard
      content={false}
      title="Controlled Visible Columns"
      secondary={
        <Stack direction="row" spacing={2} alignItems="center">
          <CSVExport
            data={data.rows}
            filename={'controlled-visibility-data-grid-table.csv'}
            header={headers}
          />
          <CardSecondaryAction link="https://mui.com/x/react-data-grid/column-visibility/#controlled-visible-columns" />
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
          initialState={{
            pagination: {
              paginationModel: {
                pageSize: 5,
              },
            },
          }}
          autoHeight
          hideFooterSelectedRowCount
          columnVisibilityModel={columnVisibilityModel}
          onColumnVisibilityModelChange={newModel => setColumnVisibilityModel(newModel)}
        />
      </Box>
    </MainCard>
  );
}
