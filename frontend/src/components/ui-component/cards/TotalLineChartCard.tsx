import { Box, Card, Grid, Typography  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import dynamic from 'next/dynamic';

// material-ui

// third party
import { Props as ChartProps } from 'react-apexcharts';

const ReactApexChart = dynamic(() => import('react-apexcharts'), { ssr: false });

interface TotalLineChartCardProps {
  bgColor?: string;
  chartData?: ChartProps;
  title: string;
  percentage: string;
  value: number;
}

// ============================|| TOTAL LINE CHART CARD ||============================ //

const TotalLineChartCard = ({
  bgColor,
  chartData,
  title,
  percentage,
  value,
}: TotalLineChartCardProps) => {
  const theme = useTheme();

  return (
    <Card>
      <Box sx={{ color: '#fff', bgcolor: bgColor || theme.palette.primary.dark }}>
        <Box sx={{ p: 2.5 }}>
          <Grid container direction="column">
            <Grid container justifyContent="space-between" alignItems="center">
              {value && (
                <Grid>
                  <Typography variant="h3" color="inherit">
                    {value}
                  </Typography>
                </Grid>
              )}
              {percentage && (
                <Grid>
                  <Typography variant="body2" color="inherit">
                    {percentage}
                  </Typography>
                </Grid>
              )}
            </Grid>
            {title && (
              <Grid>
                <Typography variant="body2" color="inherit">
                  {title}
                </Typography>
              </Grid>
            )}
          </Grid>
        </Box>
        {chartData && (
          <ReactApexChart
            options={chartData.options || { chart: { type: 'bar' } } as any}
            series={(chartData.series as any) || []}
            type={(chartData.options?.chart?.type as ChartProps['type']) || 'bar'}
            height={(chartData.options?.chart?.height as any) || 260}
          />
        )}
      </Box>
    </Card>
  );
};

export default TotalLineChartCard;
