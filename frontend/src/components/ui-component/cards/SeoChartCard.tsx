import { Box, Grid, Typography, useMediaQuery  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import dynamic from 'next/dynamic';
import { ReactNode } from 'react';

// material-ui

// third party
import { Props as ChartProps } from 'react-apexcharts';

// project imports
import MainCard from './MainCard';

const ReactApexChart = dynamic(() => import('react-apexcharts'), { ssr: false });

// =============================|| SEO CHART CARD ||============================= //

interface SeoChartCardProps {
  chartData: ChartProps;
  value?: string | number;
  title?: string;
  icon?: ReactNode | string;
  type?: number;
}

const SeoChartCard = ({ chartData, value, title, icon, type }: SeoChartCardProps) => {
  const theme = useTheme();
  const matchDownMd = useMediaQuery(theme.breakpoints.down('lg'));

  return (
    <MainCard>
      <Grid container justifyContent="space-between" spacing={2}>
        <Grid size={{ xs: 12 }}>
          <Grid
            container
            direction={type === 1 ? 'column-reverse' : 'column'}
            spacing={type === 1 ? 0 : 1}
          >
            {value && (
              <Grid>
                <Typography variant={matchDownMd ? 'h4' : 'h3'}>{value}</Typography>
              </Grid>
            )}
            {(title || icon) && (
              <Grid container justifyContent="flex-start" alignContent="center">
                {title && <Typography variant="body1">{title}</Typography>}
                {icon && (
                  <Box
                    sx={{
                      ml: 1,
                      '& .MuiSvgIcon-root': {
                        mt: -0.5,
                      },
                    }}
                  >
                    {icon}
                  </Box>
                )}
              </Grid>
            )}
          </Grid>
        </Grid>
        {chartData && (
          <Grid size={{ xs: 12 }}>
            <ReactApexChart
              options={chartData.options || { chart: { type: 'bar' } } as any}
              series={(chartData.series as any) || []}
              type={(chartData.options?.chart?.type as ChartProps['type']) || 'bar'}
              height={(chartData.options?.chart?.height as any) || 260}
            />
          </Grid>
        )}
      </Grid>
    </MainCard>
  );
};

export default SeoChartCard;
