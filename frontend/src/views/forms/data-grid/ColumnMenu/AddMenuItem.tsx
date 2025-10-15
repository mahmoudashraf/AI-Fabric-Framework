'use client';

// material-ui
import SettingsApplicationsIcon from '@mui/icons-material/SettingsApplications';
import { Box, ListItemIcon, ListItemText, MenuItem, Stack } from '@mui/material';
import {
  DataGrid,
  GridColumnMenu,
  GridColumnMenuProps,
  GridColumnMenuItemProps,
} from '@mui/x-data-grid';
import { useDemoData } from '@mui/x-data-grid-generator';

// project import
import CardSecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import CSVExport from 'views/forms/tables/tbl-exports';

// assets

function CustomUserItem(props: GridColumnMenuItemProps) {
  const { myCustomHandler, myCustomValue } = props;
  return (
    <MenuItem onClick={myCustomHandler}>
      <ListItemIcon>
        <SettingsApplicationsIcon fontSize="small" />
      </ListItemIcon>
      <ListItemText>{myCustomValue}</ListItemText>
    </MenuItem>
  );
}

function CustomColumnMenu(props: GridColumnMenuProps) {
  return (
    <GridColumnMenu
      {...props}
      slots={{
        // Add new item
        columnMenuUserItem: CustomUserItem,
      }}
      slotProps={{
        columnMenuUserItem: {
          // set `displayOrder` for new item
          displayOrder: 15,
          // pass additional props
          myCustomValue: 'Do custom action',
          myCustomHandler: () => alert('Custom handler fired'),
        },
      }}
    />
  );
}

// ==============================|| ADD MENU ITEM DATA GRID ||============================== //

export default function AddMenuItem() {
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
      title="Adding Menu Item"
      secondary={
        <Stack direction="row" spacing={2} alignItems="center">
          <CSVExport
            data={data.rows}
            filename={'add-menu-item-data-grid-table.csv'}
            header={headers}
          />
          <CardSecondaryAction link="https://mui.com/x/react-data-grid/column-menu/#adding-a-menu-item" />
        </Stack>
      }
    >
      <Box sx={{ width: '100%' }}>
        <DataGrid autoHeight hideFooter {...data} slots={{ columnMenu: CustomColumnMenu }} />
      </Box>
    </MainCard>
  );
}
