'use client';

// material-ui
import AccountCircleTwoTone from '@mui/icons-material/AccountCircleTwoTone';
import DescriptionTwoToneIcon from '@mui/icons-material/DescriptionTwoTone';
import MonetizationOnTwoToneIcon from '@mui/icons-material/MonetizationOnTwoTone';
import { Grid, Typography, useMediaQuery  } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project imports
import { IconShare, IconAccessPoint, IconCircles, IconCreditCard } from '@tabler/icons-react';

import LatestCustomerTableCard from 'components/dashboard/Analytics/LatestCustomerTableCard';
import MarketShareAreaChartCard from 'components/dashboard/Analytics/MarketShareAreaChartCard';
import TotalRevenueCard from 'components/dashboard/Analytics/TotalRevenueCard';
import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';
import RevenueCard from 'ui-component/cards/RevenueCard';
import UserCountCard from 'ui-component/cards/UserCountCard';

// assets

// ==============================|| ANALYTICS DASHBOARD ||============================== //

const Analytics = () => {
  const theme = useTheme();
  const matchDownXs = useMediaQuery(theme.breakpoints.down('sm'));

  const blockSX = {
    p: 2.5,
    borderLeft: '1px solid ',
    borderBottom: '1px solid ',
    borderLeftColor:
      theme.palette.mode === 'dark' ? theme.palette.dark.main : theme.palette.grey[200],
    borderBottomColor:
      theme.palette.mode === 'dark' ? theme.palette.dark.main : theme.palette.grey[200],
  };

  return (
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12, md: 6, lg: 8 }}>
        <Grid container spacing={gridSpacing}>
          <Grid size={{ xs: 12 }}>
            <MarketShareAreaChartCard />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 6, lg: 6 }}>
            <RevenueCard
              primary="Revenue"
              secondary="$42,562"
              content="$50,032 Last Month"
              iconPrimary={MonetizationOnTwoToneIcon}
              color={theme.palette.secondary.main}
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 6, lg: 6 }}>
            <RevenueCard
              primary="Orders Received"
              secondary="486"
              content="20% Increase"
              iconPrimary={AccountCircleTwoTone}
              color={theme.palette.primary.main}
            />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <LatestCustomerTableCard />
          </Grid>
        </Grid>
      </Grid>
      <Grid size={{ xs: 12, md: 6, lg: 4 }}>
        <Grid container spacing={gridSpacing}>
          <Grid size={{ xs: 12 }}>
            <MainCard
              content={false}
              sx={{
                '& svg': {
                  width: 50,
                  height: 50,
                  color: theme.palette.secondary.main,
                  borderRadius: '14px',
                  p: 1.25,
                  bgcolor:
                    theme.palette.mode === 'dark'
                      ? theme.palette.background.default
                      : 'primary.light',
                },
              }}
            >
              <Grid container alignItems="center" spacing={0}>
                <Grid size={{ xs: 12, sm: 6, md: 6, lg: 6 }} sx={blockSX}>
                  <Grid
                    container
                    alignItems="center"
                    spacing={1}
                    justifyContent={matchDownXs ? 'space-between' : 'center'}
                  >
                    <Grid>
                      <IconShare stroke={1.5} />
                    </Grid>
                    <Grid size="grow" sx={{ minWidth: 0 }}>
                      <Typography variant="h5" align="center">
                        1000
                      </Typography>
                      <Typography variant="subtitle2" align="center">
                        SHARES
                      </Typography>
                    </Grid>
                  </Grid>
                </Grid>
                <Grid size={{ xs: 12, sm: 6, md: 6, lg: 6 }} sx={blockSX}>
                  <Grid
                    container
                    alignItems="center"
                    spacing={1}
                    justifyContent={matchDownXs ? 'space-between' : 'center'}
                  >
                    <Grid>
                      <IconAccessPoint stroke={1.5} />
                    </Grid>
                    <Grid size="grow" sx={{ minWidth: 0 }}>
                      <Typography variant="h5" align="center">
                        600
                      </Typography>
                      <Typography variant="subtitle2" align="center">
                        NETWORK
                      </Typography>
                    </Grid>
                  </Grid>
                </Grid>
              </Grid>
              <Grid container alignItems="center" spacing={0}>
                <Grid size={{ xs: 12, sm: 6, md: 6, lg: 6 }} sx={blockSX}>
                  <Grid
                    container
                    alignItems="center"
                    spacing={1}
                    justifyContent={matchDownXs ? 'space-between' : 'center'}
                  >
                    <Grid>
                      <IconCircles stroke={1.5} />
                    </Grid>
                    <Grid size="grow" sx={{ minWidth: 0 }}>
                      <Typography variant="h5" align="center">
                        3550
                      </Typography>
                      <Typography variant="subtitle2" align="center">
                        RETURNS
                      </Typography>
                    </Grid>
                  </Grid>
                </Grid>
                <Grid size={{ xs: 12, sm: 6, md: 6, lg: 6 }} sx={blockSX}>
                  <Grid
                    container
                    alignItems="center"
                    spacing={1}
                    justifyContent={matchDownXs ? 'space-between' : 'center'}
                  >
                    <Grid>
                      <IconCreditCard stroke={1.5} />
                    </Grid>
                    <Grid size="grow" sx={{ minWidth: 0 }}>
                      <Typography variant="h5" align="center">
                        100%
                      </Typography>
                      <Typography variant="subtitle2" align="center">
                        ORDER
                      </Typography>
                    </Grid>
                  </Grid>
                </Grid>
              </Grid>
            </MainCard>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <TotalRevenueCard />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <UserCountCard
              primary="Daily user"
              secondary="1,658"
              iconPrimary={AccountCircleTwoTone}
              color={theme.palette.secondary.main}
            />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <UserCountCard
              primary="Daily page view"
              secondary="1K"
              iconPrimary={DescriptionTwoToneIcon}
              color={theme.palette.primary.main}
            />
          </Grid>
        </Grid>
      </Grid>
    </Grid>
  );
};

export default Analytics;
