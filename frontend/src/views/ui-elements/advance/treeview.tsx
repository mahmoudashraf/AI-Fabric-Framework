'use client';

// material-ui
import { Grid  } from '@mui/material';

// project imports
import ControlledTreeView from 'components/ui-elements/advance/UITreeview/ControlledTreeView';
import CustomizedTreeView from 'components/ui-elements/advance/UITreeview/CustomizedTreeView';
import FileSystemNavigator from 'components/ui-elements/advance/UITreeview/FileSystemNavigator';
import GmailTreeView from 'components/ui-elements/advance/UITreeview/GmailTreeView';
import MultiSelectTreeView from 'components/ui-elements/advance/UITreeview/MultiSelectTreeView';
import RecursiveTreeView from 'components/ui-elements/advance/UITreeview/RecursiveTreeView';
import { gridSpacing } from 'constants/index';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';

// ==============================|| UI TREEVIEW ||============================== //

const UITreeview = () => (
  <MainCard
    title="Treeview"
    secondary={<SecondaryAction link="https://next.material-ui.com/components/tree-view/" />}
  >
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Basic">
          <FileSystemNavigator />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Multi Selection">
          <MultiSelectTreeView />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Controlled">
          <ControlledTreeView />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Recursive">
          <RecursiveTreeView />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Customized">
          <CustomizedTreeView />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Gmail Clone">
          <GmailTreeView />
        </SubCard>
      </Grid>
    </Grid>
  </MainCard>
);

export default UITreeview;
