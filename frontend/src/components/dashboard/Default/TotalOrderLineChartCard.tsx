'use client';

import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import LocalMallOutlinedIcon from '@mui/icons-material/LocalMallOutlined';
import { Avatar, Box, Button, Grid, Typography  } from '@mui/material';
import { useTheme, styled } from '@mui/material/styles';
import { ApexOptions } from 'apexcharts';
import React from 'react';

// material-ui

// third-party
import { Props as ChartProps } from 'react-apexcharts';

// project imports
import MainCard from 'ui-component/cards/MainCard';
import SkeletonTotalOrderCard from 'ui-component/cards/Skeleton/EarningCard';
import ChartWrapper from 'ui-component/charts/ChartWrapper';

// assets

const CardWrapper = styled(MainCard)(({ theme }) => ({
  backgroundColor:
    theme.palette.mode === 'dark' ? theme.palette.dark.dark : theme.palette.primary.dark,
  color: '#fff',
  overflow: 'hidden',
  position: 'relative',
  '&>div': {
    position: 'relative',
    zIndex: 5,
  },
  '&:after': {
    content: '""',
    position: 'absolute',
    width: 210,
    height: 210,
    background:
      theme.palette.mode === 'dark'
        ? `linear-gradient(210.04deg, ${theme.palette.primary.dark} -50.94%, rgba(144, 202, 249, 0) 95.49%)`
        : theme.palette.primary[800],
    borderRadius: '50%',
    zIndex: 1,
    top: -85,
    right: -95,
    [theme.breakpoints.down('sm')]: {
      top: -105,
      right: -140,
    },
  },
  '&:before': {
    content: '""',
    position: 'absolute',
    zIndex: 1,
    width: 210,
    height: 210,
    background:
      theme.palette.mode === 'dark'
        ? `linear-gradient(140.9deg, ${theme.palette.primary.dark} -14.02%, rgba(144, 202, 249, 0) 82.50%)`
        : theme.palette.primary[800],
    borderRadius: '50%',
    top: -125,
    right: -15,
    opacity: 0.5,
    [theme.breakpoints.down('sm')]: {
      top: -155,
      right: -70,
    },
  },
}));

const chartOptions: ChartProps = {
  chart: {
    type: 'line',
    height: 90,
    sparkline: {
      enabled: true,
    },
  },
  dataLabels: {
    enabled: false,
  },
  colors: ['#fff'],
  fill: {
    type: 'solid',
    opacity: 1,
  },
  stroke: {
    curve: 'smooth',
    width: 3,
  },
  yaxis: {
    min: 0,
    max: 100,
  },
  tooltip: {
    theme: 'dark',
    fixed: {
      enabled: false,
    },
    x: {
      show: false,
    },
    y: {
      title: {
        formatter: () => 'Total Order',
      },
    },
    marker: {
      show: false,
    },
  },
};

// ==============================|| DASHBOARD - TOTAL ORDER LINE CHART CARD ||============================== //

interface TotalOrderLineChartCardProps {
  isLoading: boolean;
}

const TotalOrderLineChartCard = ({ isLoading }: TotalOrderLineChartCardProps) => {
  const theme = useTheme();

  const yearSeries = [
    {
      name: 'series1',
      data: [35, 44, 9, 54, 45, 66, 41, 69, 25, 50, 35, 80],
    },
  ];

  const monthSeries = [
    {
      name: 'series1',
      data: [45, 66, 41, 89, 25, 44, 9],
    },
  ];

  const [series, setSeries] = React.useState(yearSeries);

  const [timeValue, setTimeValue] = React.useState<boolean>(false);
  const handleChangeTime = (
    _event: React.MouseEvent<HTMLButtonElement, MouseEvent>,
    newValue: boolean
  ) => {
    setTimeValue(newValue);
    setSeries(timeValue ? yearSeries : monthSeries);
  };

  return (
    <>
      {isLoading ? (
        <SkeletonTotalOrderCard />
      ) : (
        <CardWrapper border={false} content={false}>
          <Box sx={{ p: 2.25 }}>
            <Grid container direction="column">
              <Grid>
                <Grid container justifyContent="space-between">
                  <Grid>
                    <Avatar
                      variant="rounded"
                      sx={{
                        ...theme.typography.commonAvatar,
                        ...theme.typography.largeAvatar,
                        backgroundColor:
                          theme.palette.mode === 'dark'
                            ? theme.palette.dark.main
                            : theme.palette.primary[800],
                        color: '#fff',
                        mt: 1,
                      } as any}
                    >
                      <LocalMallOutlinedIcon fontSize="inherit" />
                    </Avatar>
                  </Grid>
                  <Grid>
                    <Button
                      disableElevation
                      variant={timeValue ? 'contained' : 'text'}
                      size="small"
                      sx={{ color: 'inherit' }}
                      onClick={e => handleChangeTime(e, true)}
                    >
                      Month
                    </Button>
                    <Button
                      disableElevation
                      variant={!timeValue ? 'contained' : 'text'}
                      size="small"
                      sx={{ color: 'inherit' }}
                      onClick={e => handleChangeTime(e, false)}
                    >
                      Year
                    </Button>
                  </Grid>
                </Grid>
              </Grid>
              <Grid sx={{ mb: 0.75 }}>
                <Grid container alignItems="center">
                  <Grid size={6}>
                    <Grid container alignItems="center">
                      <Grid>
                        {timeValue ? (
                          <Typography
                            sx={{
                              fontSize: '2.125rem',
                              fontWeight: 500,
                              mr: 1,
                              mt: 1.75,
                              mb: 0.75,
                            }}
                          >
                            $108
                          </Typography>
                        ) : (
                          <Typography
                            sx={{
                              fontSize: '2.125rem',
                              fontWeight: 500,
                              mr: 1,
                              mt: 1.75,
                              mb: 0.75,
                            }}
                          >
                            $961
                          </Typography>
                        )}
                      </Grid>
                      <Grid>
                        <Avatar
                          sx={{
                            ...theme.typography.smallAvatar,
                            cursor: 'pointer',
                            backgroundColor: theme.palette.primary[200],
                            color: theme.palette.primary.dark,
                          } as any}
                        >
                          <ArrowDownwardIcon
                            fontSize="inherit"
                            sx={{ transform: 'rotate3d(1, 1, 1, 45deg)' }}
                          />
                        </Avatar>
                      </Grid>
                      <Grid size={{ xs: 12 }}>
                        <Typography
                          sx={{
                            fontSize: '1rem',
                            fontWeight: 500,
                            color:
                              theme.palette.mode === 'dark'
                                ? theme.palette.text.secondary
                                : theme.palette.primary[200],
                          }}
                        >
                          Total Order
                        </Typography>
                      </Grid>
                    </Grid>
                  </Grid>
                  <Grid size={6}>
                    <ChartWrapper
                      options={chartOptions as ApexOptions}
                      series={series}
                      type="line"
                      height={90}
                    />
                  </Grid>
                </Grid>
              </Grid>
            </Grid>
          </Box>
        </CardWrapper>
      )}
    </>
  );
};

export default TotalOrderLineChartCard;
