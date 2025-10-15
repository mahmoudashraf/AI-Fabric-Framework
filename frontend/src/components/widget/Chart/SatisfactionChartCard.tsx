import { Grid, Typography  } from '@mui/material';
import dynamic from 'next/dynamic';

// material-ui

// third-party
import { Props as ChartProps } from 'react-apexcharts';

// project imports
import useConfig from 'hooks/useConfig';
import MainCard from 'ui-component/cards/MainCard';

const ReactApexChart = dynamic(() => import('react-apexcharts'), { ssr: false });

// =========================|| SATISFACTION CHART CARD ||========================= //

const SatisfactionChartCard = ({ chartData }: { chartData: ChartProps }) => {
  const { rtlLayout } = useConfig();

  return (
    <MainCard>
      <Grid container direction="column" spacing={1}>
        <Grid>
          <Typography variant="subtitle1">Customer Satisfaction</Typography>
        </Grid>
        <Grid sx={{ '& .apexcharts-legend-text': { marginLeft: rtlLayout ? '8px' : 'initial' } }}
        >
          <ReactApexChart
            options={chartData.options || { chart: { type: 'bar' } } as any}
            series={(chartData.series as any) || []}
            type={(chartData.options?.chart?.type as ChartProps['type']) || 'bar'}
            height={(chartData.options?.chart?.height as any) || 260}
          />
        </Grid>
      </Grid>
    </MainCard>
  );
};

export default SatisfactionChartCard;
