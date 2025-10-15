// material-ui
import { Grid  } from '@mui/material';

// project import
import { gridSpacing } from 'constants/index';

import ControlledVisibility from './ControlledVisibility';
import InitializeColumnVisibility from './InitializeColumnVisibility';
import VisibilityPanel from './VisibilityPanel';

// ==============================|| COLUMN VISIBILITY DATA GRID ||============================== //

export default function ColumnVisibility() {
  return (
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12 }}>
        <InitializeColumnVisibility />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <ControlledVisibility />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <VisibilityPanel />
      </Grid>
    </Grid>
  );
}
