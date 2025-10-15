'use client';

// material-ui
import { Box, Stack } from '@mui/material';
import { DataGrid, GridColumnMenu, GridColumnMenuProps } from '@mui/x-data-grid';
import { useDemoData } from '@mui/x-data-grid-generator';

// project import
import CardSecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import CSVExport from 'views/forms/tables/tbl-exports';

function CustomColumnMenu(props: GridColumnMenuProps) {
  return (
    <GridColumnMenu
      {...props}
      slotProps={{
        // Swap positions of filter and sort items
        columnMenuFilterItem: {
          displayOrder: 0, // Previously `10`
        },
        columnMenuSortItem: {
          displayOrder: 10, // Previously `0`
        },
      }}
    />
  );
}

// ==============================|| REORDERING MENU ITEM DATA GRID ||============================== //

export default function ReorderMenu() {
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
      title="Reordering Menu Item"
      secondary={
        <Stack direction="row" spacing={2} alignItems="center">
          <CSVExport
            data={data.rows}
            filename={'reorder-menu-data-grid-table.csv'}
            header={headers}
          />
          <CardSecondaryAction link="https://mui.com/x/react-data-grid/column-menu/#reordering-menu-items" />
        </Stack>
      }
    >
      <Box sx={{ width: '100%' }}>
        <DataGrid autoHeight hideFooter {...data} slots={{ columnMenu: CustomColumnMenu }} />
      </Box>
    </MainCard>
  );
}
