'use client';

import { Card, Grid, Typography  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useState } from 'react';

// material-ui

// third-party
import { Props as ChartProps } from 'react-apexcharts';

// project imports
import useConfig from 'hooks/useConfig';
import { ApexOptions } from 'apexcharts';
import ChartWrapper from 'ui-component/charts/ChartWrapper';

const chartOptions: ChartProps = {
  chart: {
    id: 'support-chart',
    type: 'area',
    height: 95,
    sparkline: {
      enabled: true,
    },
  },
  dataLabels: {
    enabled: false,
  },
  stroke: {
    curve: 'smooth',
    width: 1,
  },
  tooltip: {
    fixed: {
      enabled: false,
    },
    x: {
      show: false,
    },
    y: {
      title: {
        formatter: () => 'Ticket ',
      },
    },
    marker: {
      show: false,
    },
  },
};

// ===========================|| DASHBOARD DEFAULT - BAJAJ AREA CHART CARD ||=========================== //

const BajajAreaChartCard = () => {
  const theme = useTheme();

  const [series] = useState([
    {
      data: [0, 15, 10, 50, 30, 40, 25],
    },
  ]);

  const { navType } = useConfig();

  const orangeDark = theme.palette.secondary[800];

  const [options, setOptions] = useState<ChartProps>(chartOptions);

  useEffect(() => {
    setOptions((prevState: any) => ({
      ...prevState,
      colors: [orangeDark],
      tooltip: {
        theme: navType === 'dark' ? 'dark' : 'light',
      },
    }));
  }, [navType, orangeDark]);

  return (
    <Card sx={{ bgcolor: 'secondary.light' }}>
      <Grid container sx={{ p: 2, pb: 0, color: '#fff' }}>
        <Grid size={{ xs: 12 }}>
          <Grid container alignItems="center" justifyContent="space-between">
            <Grid>
              <Typography variant="subtitle1" sx={{ color: theme.palette.secondary.dark }}>
                Bajaj Finery
              </Typography>
            </Grid>
            <Grid>
              <Typography variant="h4" sx={{ color: theme.palette.grey[800] }}>
                $1839.00
              </Typography>
            </Grid>
          </Grid>
        </Grid>
        <Grid size={{ xs: 12 }}>
          <Typography variant="subtitle2" sx={{ color: theme.palette.grey[800] }}>
            10% Profit
          </Typography>
        </Grid>
      </Grid>
      <ChartWrapper options={options as ApexOptions} series={series} type="area" height={95} />
    </Card>
  );
};

export default BajajAreaChartCard;
