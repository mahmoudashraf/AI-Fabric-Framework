'use client';

// material-ui
import { Grid  } from '@mui/material';

// project imports
import OpenIconSpeedDial from 'components/ui-elements/advance/UISpeeddial/OpenIconSpeedDial';
import SimpleSpeedDials from 'components/ui-elements/advance/UISpeeddial/SimpleSpeedDials';
import SpeedDialTooltipOpen from 'components/ui-elements/advance/UISpeeddial/SpeedDialTooltipOpen';
import { gridSpacing } from 'constants/index';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';

// =============================|| UI SPEEDDIAL ||============================= //

const UISpeeddial = () => (
  <MainCard>
    <MainCard.Header title="Speeddial" action={<SecondaryAction link="https://next.material-ui.com/components/speed-dial/" />} />
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12, md: 12 }}>
        <SubCard title="Basic">
          <SimpleSpeedDials />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Custom Close Icon">
          <OpenIconSpeedDial />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Persistent Icon">
          <SpeedDialTooltipOpen />
        </SubCard>
      </Grid>
    </Grid>
  </MainCard>
);
export default UISpeeddial;
