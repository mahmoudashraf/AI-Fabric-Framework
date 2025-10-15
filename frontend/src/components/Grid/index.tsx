// material-ui
import { Grid } from '@mui/material';

// project imports
import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';

import AutoGrid from './AutoGrid';
import BasicGrid from './BasicGrid';
import ColumnsGrid from './ColumnsGrid';
import ComplexGrid from './ComplexGrid';
import MultipleBreakPoints from './MultipleBreakPoints';
import NestedGrid from './NestedGrid';
import SpacingGrid from './SpacingGrid';

// ===============================|| GRID SYSTEM||=============================== //

const GridSystem = () => (
  <Grid container spacing={gridSpacing}>
    <Grid size={{ xs: 12, md: 6 }}>
      <MainCard title="Basic Grid">
        <BasicGrid />
      </MainCard>
    </Grid>
    <Grid size={{ xs: 12, md: 6 }}>
      <MainCard title="Multiple Breakpoints">
        <MultipleBreakPoints />
      </MainCard>
    </Grid>
    <Grid size={{ xs: 12, md: 6 }}>
      <MainCard title="Spacing Grid">
        <SpacingGrid />
      </MainCard>
    </Grid>
    <Grid size={{ xs: 12, md: 6 }}>
      <MainCard title="Complex Grid">
        <ComplexGrid />
      </MainCard>
    </Grid>
    <Grid size={{ xs: 12, md: 6 }}>
      <MainCard title="Auto Grid">
        <AutoGrid />
      </MainCard>
    </Grid>
    <Grid size={{ xs: 12, md: 6 }}>
      <MainCard title="Column Grid">
        <ColumnsGrid />
      </MainCard>
    </Grid>
    <Grid size={{ xs: 12 }}>
      <MainCard title="Nested Grid">
        <NestedGrid />
      </MainCard>
    </Grid>
  </Grid>
);

export default GridSystem;
