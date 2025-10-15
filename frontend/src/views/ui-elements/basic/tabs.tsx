'use client';

// material-ui
import { Grid  } from '@mui/material';

// project imports
import ColorTabs from 'components/ui-elements/basic/UITabs/ColorTabs';
import DisabledTabs from 'components/ui-elements/basic/UITabs/DisabledTabs';
import IconTabs from 'components/ui-elements/basic/UITabs/IconTabs';
import SimpleTabs from 'components/ui-elements/basic/UITabs/SimpleTabs';
import VerticalTabs from 'components/ui-elements/basic/UITabs/VerticalTabs';
import { gridSpacing } from 'constants/index';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';

// ================================|| UI TABS ||================================ //

const UITabs = () => (
  <MainCard>
    <MainCard.Header title="Tabs" action={<SecondaryAction link="https://next.material-ui.com/components/tabs/" />} />
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Basic">
          <SimpleTabs />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Color Tab">
          <ColorTabs />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Icon Tabs">
          <IconTabs />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Disabled Tabs">
          <DisabledTabs />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <SubCard title="Vertical Tabs">
          <VerticalTabs />
        </SubCard>
      </Grid>
    </Grid>
  </MainCard>
);

export default UITabs;
