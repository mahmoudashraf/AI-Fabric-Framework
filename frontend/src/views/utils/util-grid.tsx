'use client';

// material-ui
import { Grid  } from '@mui/material';

// project imports
import AutoGrid from 'components/Grid/AutoGrid';
import BasicGrid from 'components/Grid/BasicGrid';
import ColumnsGrid from 'components/Grid/ColumnsGrid';
import ComplexGrid from 'components/Grid/ComplexGrid';
import MultipleBreakPoints from 'components/Grid/MultipleBreakPoints';
import NestedGrid from 'components/Grid/NestedGrid';
import SpacingGrid from 'components/Grid/SpacingGrid';
import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';

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
