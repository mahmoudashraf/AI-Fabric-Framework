'use client';

// material-ui
import { Grid  } from '@mui/material';

// project imports
import AlternateTimeline from 'components/ui-elements/advance/UITimeline/AlternateTimeline';
import BasicTimeline from 'components/ui-elements/advance/UITimeline/BasicTimeline';
import ColorsTimeline from 'components/ui-elements/advance/UITimeline/ColorsTimeline';
import CustomizedTimeline from 'components/ui-elements/advance/UITimeline/CustomizedTimeline';
import OppositeContentTimeline from 'components/ui-elements/advance/UITimeline/OppositeContentTimeline';
import OutlinedTimeline from 'components/ui-elements/advance/UITimeline/OutlinedTimeline';
import RightAlignedTimeline from 'components/ui-elements/advance/UITimeline/RightAlignedTimeline';
import { gridSpacing } from 'constants/index';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';

// ==============================|| UI TIMELINE ||============================== //

const UITimeline = () => (
  <MainCard>
    <MainCard.Header title="Timeline" action={<SecondaryAction link="https://next.material-ui.com/components/timeline/" />} />
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Basic">
          <BasicTimeline />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Right Aligned">
          <RightAlignedTimeline />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Alternating">
          <AlternateTimeline />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Color">
          <ColorsTimeline />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Outlined">
          <OutlinedTimeline />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Opposite Content">
          <OppositeContentTimeline />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <SubCard title="Customized">
          <CustomizedTimeline />
        </SubCard>
      </Grid>
    </Grid>
  </MainCard>
);

export default UITimeline;
