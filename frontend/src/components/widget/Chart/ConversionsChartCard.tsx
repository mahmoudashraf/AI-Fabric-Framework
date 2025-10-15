import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import { Box, Grid, Typography  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import dynamic from 'next/dynamic';

// material-ui

// third-party
import { Props as ChartProps } from 'react-apexcharts';

// project imports
import MainCard from 'ui-component/cards/MainCard';

// assets

const ReactApexChart = dynamic(() => import('react-apexcharts'), { ssr: false });

// =========================|| CONVERSIONS CHART CARD ||========================= //

const ConversionsChartCard = ({ chartData }: { chartData: ChartProps }) => {
  const theme = useTheme();

  return (
    <MainCard content={false}>
      <Box sx={{ p: 3 }}>
        <Grid container spacing={1} alignItems="center">
          <Grid>
            <Typography variant="subtitle1">New Stock</Typography>
          </Grid>
          <Grid>
            <Typography variant="caption">(Purchased)</Typography>
          </Grid>
        </Grid>
        <Grid container spacing={2} alignItems="center">
          <Grid>
            <Typography variant="h4">0.85%</Typography>
          </Grid>
          <Grid>
            <Grid
              container
              spacing={1}
              alignItems="center"
              style={{ color: theme.palette.info.main }}
            >
              <ArrowUpwardIcon color="inherit" />
              <Typography variant="h4" color="inherit">
                0.50%
              </Typography>
            </Grid>
          </Grid>
        </Grid>
      </Box>
      <ReactApexChart
        options={chartData.options || { chart: { type: 'bar' } } as any}
        series={(chartData.series as any) || []}
        type={(chartData.options?.chart?.type as ChartProps['type']) || 'bar'}
        height={(chartData.options?.chart?.height as any) || 260}
      />
    </MainCard>
  );
};

export default ConversionsChartCard;
