'use client';

// material-ui
import { Grid  } from '@mui/material';

// project imports
import CustomizedDividers from 'components/ui-elements/advance/UIToggleButton/CustomizedDividers';
import ExclusiveToggleButtons from 'components/ui-elements/advance/UIToggleButton/ExclusiveToggleButtons';
import StandaloneToggleButton from 'components/ui-elements/advance/UIToggleButton/StandaloneToggleButton';
import ToggleButtonNotEmpty from 'components/ui-elements/advance/UIToggleButton/ToggleButtonNotEmpty';
import ToggleButtonSizes from 'components/ui-elements/advance/UIToggleButton/ToggleButtonSizes';
import ToggleButtonsMultiple from 'components/ui-elements/advance/UIToggleButton/ToggleButtonsMultiple';
import VerticalToggleButtons from 'components/ui-elements/advance/UIToggleButton/VerticalToggleButtons';
import { gridSpacing } from 'constants/index';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';

// ============================|| UI TOGGLE BUTTON ||============================ //

const UIToggleButton = () => (
  <MainCard
    title="Toggle Button"
    secondary={<SecondaryAction link="https://next.material-ui.com/components/toggle-button/" />}
  >
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Exclusive Selection">
          <ExclusiveToggleButtons />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Multiple Selection">
          <ToggleButtonsMultiple />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Standalone">
          <StandaloneToggleButton />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Sizes">
          <ToggleButtonSizes />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Enforce Value Set">
          <ToggleButtonNotEmpty />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Vertical">
          <VerticalToggleButtons />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <SubCard title="Customized">
          <CustomizedDividers />
        </SubCard>
      </Grid>
    </Grid>
  </MainCard>
);

export default UIToggleButton;
