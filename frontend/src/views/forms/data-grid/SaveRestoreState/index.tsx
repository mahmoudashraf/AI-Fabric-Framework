// material-ui
import { Grid  } from '@mui/material';

// project import
import { gridSpacing } from 'constants/index';

import InitialState from './InitialState';
import UseGridSelector from './UseGridSelector';

// ==============================|| SAVE & RESTORE STATE DATA GRID ||============================== //

export default function SaveRestoreState() {
  return (
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12 }}>
        <InitialState />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <UseGridSelector />
      </Grid>
    </Grid>
  );
}
