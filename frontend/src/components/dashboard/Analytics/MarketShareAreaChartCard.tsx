'use client';

import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import { Grid, Box, Typography  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { IconBrandFacebook, IconBrandYoutube, IconBrandTwitter } from '@tabler/icons-react';
import dynamic from 'next/dynamic';
import { useEffect, useState } from 'react';

// material-ui

// third-party
import { Props as ChartProps } from 'react-apexcharts';

// project imports
import useConfig from 'hooks/useConfig';
import MainCard from 'ui-component/cards/MainCard';
import { ApexOptions } from 'apexcharts';

// assets

const ReactApexChart = dynamic(() => import('react-apexcharts'), { ssr: false });

const chartOptions: ChartProps = {
  chart: {
    height: 200,
    type: 'area',
    id: 'market-share-area-chart',
    toolbar: {
      show: false,
    },
    zoom: {
      enabled: false,
    },
    sparkline: {
      enabled: true,
    },
  },
  dataLabels: {
    enabled: false,
  },
  stroke: {
    curve: 'smooth',
    width: 2,
  },
  fill: {
    type: 'gradient',
    gradient: {
      shadeIntensity: 1,
      opacityFrom: 0.5,
      opacityTo: 0,
      stops: [0, 80, 100],
    },
  },
  legend: {
    show: false,
  },
  yaxis: {
    min: 1,
    max: 100,
    labels: {
      show: false,
    },
  },
};

// ===========================|| DASHBOARD ANALYTICS - MARKET SHARE AREA CHART CARD ||=========================== //

const MarketShareAreaChartCard = () => {
  const theme = useTheme();

  const [series] = useState([
    {
      name: 'Youtube',
      data: [10, 90, 65, 85, 40, 80, 30],
    },
    {
      name: 'Facebook',
      data: [50, 30, 25, 15, 60, 10, 25],
    },
    {
      name: 'Twitter',
      data: [5, 50, 40, 55, 20, 40, 20],
    },
  ]);

  const { navType } = useConfig();

  const secondaryMain = theme.palette.secondary.main;
  const errorMain = theme.palette.error.main;
  const primaryDark = theme.palette.primary.dark;

  const [options, setOptions] = useState<ChartProps>(chartOptions);

  useEffect(() => {
    setOptions((prevState: any) => ({
      ...prevState,
      colors: [secondaryMain, errorMain, primaryDark],
      tooltip: {
        theme: navType === 'dark' ? 'dark' : 'light',
      },
    }));
  }, [navType, secondaryMain, errorMain, primaryDark]);

  return (
    <MainCard sx={{ '&>div': { p: 0, pb: '0px !important' } }}>
      <Box
        sx={{
          p: 3,
        }}
      >
        <Grid container direction="column" spacing={3}>
          <Grid container spacing={1} alignItems="center">
            <Grid>
              <Typography variant="h3">Market Share</Typography>
            </Grid>
            <Grid size="grow" sx={{ minWidth: 0 }} />
            <Grid>
              <TrendingDownIcon fontSize="large" color="error" />
            </Grid>
            <Grid>
              <Typography variant="h3">27, 695.65</Typography>
            </Grid>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Typography sx={{ mt: -2.5, fontWeight: 400 }} color="inherit" variant="h5">
              Department wise monthly sales report
            </Typography>
          </Grid>
          <Grid container alignItems="center" spacing={3}>
            <Grid>
              <Grid container alignItems="center" spacing={1}>
                <Grid>
                  <Typography
                    sx={{
                      width: 40,
                      height: 40,
                      color: theme.palette.secondary.main,
                      borderRadius: '12px',
                      padding: 1,
                      backgroundColor:
                        theme.palette.mode === 'dark'
                          ? theme.palette.background.default
                          : theme.palette.secondary.light,
                    }}
                  >
                    <IconBrandFacebook stroke={1.5} />
                  </Typography>
                </Grid>
                <Grid size="grow" sx={{ minWidth: 0 }}>
                  <Typography variant="h4">+ 45.36%</Typography>
                </Grid>
              </Grid>
            </Grid>
            <Grid>
              <Grid container alignItems="center" spacing={1}>
                <Grid>
                  <Typography
                    sx={{
                      width: 40,
                      height: 40,
                      color: theme.palette.primary.main,
                      borderRadius: '12px',
                      padding: 1,
                      backgroundColor:
                        theme.palette.mode === 'dark'
                          ? theme.palette.background.default
                          : theme.palette.primary.light,
                    }}
                  >
                    <IconBrandTwitter stroke={1.5} />
                  </Typography>
                </Grid>
                <Grid size="grow" sx={{ minWidth: 0 }}>
                  <Typography variant="h4">- 50.69%</Typography>
                </Grid>
              </Grid>
            </Grid>
            <Grid>
              <Grid container alignItems="center" spacing={1}>
                <Grid>
                  <Typography
                    sx={{
                      width: 40,
                      height: 40,
                      color: theme.palette.error.main,
                      borderRadius: '12px',
                      padding: 1,
                      backgroundColor:
                        theme.palette.mode === 'dark'
                          ? theme.palette.background.default
                          : '#ffe9e9',
                    }}
                  >
                    <IconBrandYoutube stroke={2} />
                  </Typography>
                </Grid>
                <Grid size="grow" sx={{ minWidth: 0 }}>
                  <Typography variant="h4">+ 16.85%</Typography>
                </Grid>
              </Grid>
            </Grid>
            <Grid size="grow" sx={{ minWidth: 0 }} />
          </Grid>
        </Grid>
      </Box>
      <ReactApexChart options={options as ApexOptions} series={series} type="area" height={200} />
    </MainCard>
  );
};
export default MarketShareAreaChartCard;
