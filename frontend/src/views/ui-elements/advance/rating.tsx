'use client';

// material-ui
import { Grid  } from '@mui/material';

// project imports
import CustomizedRatings from 'components/ui-elements/advance/UIRating/CustomizedRatings';
import HalfRating from 'components/ui-elements/advance/UIRating/HalfRating';
import HoverRating from 'components/ui-elements/advance/UIRating/HoverRating';
import SimpleRating from 'components/ui-elements/advance/UIRating/SimpleRating';
import SizeRating from 'components/ui-elements/advance/UIRating/SizeRating';
import { gridSpacing } from 'constants/index';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';

// ===============================|| UI RATING ||=============================== //

const UIRating = () => (
  <MainCard>
    <MainCard.Header title="Rating" action={<SecondaryAction link="https://next.material-ui.com/components/rating/" />} />
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Basic">
          <SimpleRating />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Customized Icon">
          <CustomizedRatings />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Size">
          <SizeRating />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Half">
          <HalfRating />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Hover">
          <HoverRating />
        </SubCard>
      </Grid>
    </Grid>
  </MainCard>
);

export default UIRating;
