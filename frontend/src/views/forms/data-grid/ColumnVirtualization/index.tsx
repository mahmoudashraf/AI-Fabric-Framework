// material-ui
import { Grid  } from '@mui/material';

// project import
import { gridSpacing } from 'constants/index';

import Virtualization from './Virtualization';

// ==============================|| COLUMN VIRTUALIZATION DATA GRID ||============================== //

export default function ColumnVirtualization() {
  return (
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12 }}>
        <Virtualization />
      </Grid>
    </Grid>
  );
}
