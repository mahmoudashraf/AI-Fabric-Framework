'use client';

import { Grid  } from '@mui/material';
import { useEffect, useState } from 'react';

// material-ui

// project imports
import EarningCard from 'components/dashboard/Default/EarningCard';
import PopularCard from 'components/dashboard/Default/PopularCard';
import TotalGrowthBarChart from 'components/dashboard/Default/TotalGrowthBarChart';
import TotalIncomeDarkCard from 'components/dashboard/Default/TotalIncomeDarkCard';
import TotalIncomeLightCard from 'components/dashboard/Default/TotalIncomeLightCard';
import TotalOrderLineChartCard from 'components/dashboard/Default/TotalOrderLineChartCard';
import { gridSpacing } from 'constants/index';

// ==============================|| DEFAULT DASHBOARD ||============================== //

const Dashboard = () => {
  const [isLoading, setLoading] = useState(true);
  useEffect(() => {
    setLoading(false);
  }, []);

  return (
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12 }}>
        <Grid container spacing={gridSpacing}>
          <Grid size={{ xs: 12, sm: 6, md: 6, lg: 4 }}>
            <EarningCard isLoading={isLoading} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 6, lg: 4 }}>
            <TotalOrderLineChartCard isLoading={isLoading} />
          </Grid>
          <Grid size={{ xs: 12, sm: 12, md: 12, lg: 4 }}>
            <Grid container spacing={gridSpacing}>
              <Grid size={{ xs: 12, sm: 6, md: 6, lg: 12 }}>
                <TotalIncomeDarkCard isLoading={isLoading} />
              </Grid>
              <Grid size={{ xs: 12, sm: 6, md: 6, lg: 12 }}>
                <TotalIncomeLightCard isLoading={isLoading} />
              </Grid>
            </Grid>
          </Grid>
        </Grid>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <Grid container spacing={gridSpacing}>
          <Grid size={{ xs: 12, md: 8 }}>
            <TotalGrowthBarChart isLoading={isLoading} />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <PopularCard isLoading={isLoading} />
          </Grid>
        </Grid>
      </Grid>
    </Grid>
  );
};

export default Dashboard;
