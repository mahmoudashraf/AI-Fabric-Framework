'use client';

// material-ui
import { Grid  } from '@mui/material';

// project imports
import CustomList from 'components/ui-elements/basic/UIList/CustomList';
import DisabledList from 'components/ui-elements/basic/UIList/DisabledList';
import FolderList from 'components/ui-elements/basic/UIList/FolderList';
import NestedList from 'components/ui-elements/basic/UIList/NestedList';
import RadioList from 'components/ui-elements/basic/UIList/RadioList';
import SelectedListItem from 'components/ui-elements/basic/UIList/SelectedListItem';
import SimpleList from 'components/ui-elements/basic/UIList/SimpleList';
import VirtualizedList from 'components/ui-elements/basic/UIList/VirtualizedList';
import { gridSpacing } from 'constants/index';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';

// ================================|| UI LIST ||================================ //

const UIList = () => (
  <MainCard>
    <MainCard.Header title="List" action={<SecondaryAction link="https://next.material-ui.com/components/lists/" />} />
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12, sm: 6, md: 4 }}>
        <SubCard title="Basic">
          <SimpleList />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6, md: 4 }}>
        <SubCard title="Nested List">
          <NestedList />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6, md: 4 }}>
        <SubCard title="Selected List Item">
          <SelectedListItem />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6, md: 4 }}>
        <SubCard title="Disabled List Item">
          <DisabledList />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6, md: 4 }}>
        <SubCard title="Radio Button List">
          <RadioList />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6, md: 4 }}>
        <SubCard title="Folder List">
          <FolderList />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Custom Aligned List">
          <CustomList />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Scrollable List">
          <VirtualizedList />
        </SubCard>
      </Grid>
    </Grid>
  </MainCard>
);

export default UIList;
