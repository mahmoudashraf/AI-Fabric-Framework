'use client';

// material-ui
import { Grid  } from '@mui/material';

// project imports
import { gridSpacing } from 'constants/index';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import SkeletonEarningCard from 'ui-component/cards/Skeleton/EarningCard';
import SkeletonPopularCard from 'ui-component/cards/Skeleton/PopularCard';
import SkeletonChartCard from 'ui-component/cards/Skeleton/TotalGrowthBarChart';
import SkeletonTotalIncomeCard from 'ui-component/cards/Skeleton/TotalIncomeCard';
import SubCard from 'ui-component/cards/SubCard';

// ==============================|| UI SKELETON ||============================== //

const UISkeleton = () => (
  <MainCard>
    <MainCard.Header title="Skeleton" action={<SecondaryAction link="https://next.material-ui.com/components/skeleton/" />} />
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Example  1">
          <SkeletonEarningCard />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Example 2">
          <Grid container spacing={gridSpacing}>
            <Grid size={{ xs: 12 }}>
              <SkeletonTotalIncomeCard />
            </Grid>
            <Grid size={{ xs: 12 }}>
              <SkeletonTotalIncomeCard />
            </Grid>
          </Grid>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 12, md: 8 }}>
        <SubCard title="Example 3">
          <SkeletonChartCard />
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 12, md: 4 }}>
        <SubCard title="Example 4">
          <SkeletonPopularCard />
        </SubCard>
      </Grid>
    </Grid>
  </MainCard>
);

export default UISkeleton;
