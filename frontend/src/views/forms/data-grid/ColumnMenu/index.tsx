// material-ui
import { Grid  } from '@mui/material';

// project import
import { gridSpacing } from 'constants/index';

import AddMenuItem from './AddMenuItem';
import ColumnMenu from './ColumnMenu';
import CustomMenu from './CustomMenu';
import DisableMenu from './DisableMenu';
import HideMenuItem from './HideMenuItem';
import OverrideMenu from './OverrideMenu';
import ReorderingMenu from './ReorderingMenu';

// ==============================|| COLUMN MENU DATA GRID ||============================== //

export default function ColumnMenuDemu() {
  return (
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12 }}>
        <ColumnMenu />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <AddMenuItem />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <OverrideMenu />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <HideMenuItem />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <ReorderingMenu />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <CustomMenu />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <DisableMenu />
      </Grid>
    </Grid>
  );
}
