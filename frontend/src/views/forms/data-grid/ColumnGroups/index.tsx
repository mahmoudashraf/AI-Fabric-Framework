// material-ui
import { Grid  } from '@mui/material';

// project import
import { gridSpacing } from 'constants/index';

import BasicColumnGroup from './BasicColumnGroup';
import CustomColumnGroup from './CustomColumnGroup';

// ==============================|| COLUMN GROUPING DATA GRID ||============================== //

export default function ColumnGroups() {
  return (
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12 }}>
        <BasicColumnGroup />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <CustomColumnGroup />
      </Grid>
    </Grid>
  );
}
