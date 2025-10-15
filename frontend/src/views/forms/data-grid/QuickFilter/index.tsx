// material-ui
import { Grid  } from '@mui/material';

// project import
import { gridSpacing } from 'constants/index';

import CustomFilter from './CustomFilter';
import ExcludeHiddenColumns from './ExcludeHiddenColumns';
import Initialize from './Initialize';
import ParsingValues from './ParsingValues';

// ==============================|| QUICK FILTER DATA GRID ||============================== //

export default function QuickFilter() {
  return (
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12 }}>
        <Initialize />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <ExcludeHiddenColumns />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <CustomFilter />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <ParsingValues />
      </Grid>
    </Grid>
  );
}
