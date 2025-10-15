// material-ui
import { Grid, Box, Typography  } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// third party
import dynamic from 'next/dynamic';
import { Props as ChartProps } from 'react-apexcharts';
const ReactApexChart = dynamic(() => import('react-apexcharts'), { 
  ssr: false,
  loading: () => <div>Loading chart...</div>
});

// project imports
import MainCard from 'ui-component/cards/MainCard';

// assets
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import { IconBrandFacebook, IconBrandYoutube, IconBrandTwitter } from '@tabler/icons-react';

// ===========================|| MARKET SHARE CHART CARD ||=========================== //

const MarketChartCard = ({ chartData }: { chartData: ChartProps }) => {
  const theme = useTheme();

  return (
    <MainCard sx={{ '&>div': { p: 0, pb: '0px !important' } }}>
      <Box sx={{ p: 3 }}>
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
          <Grid container justifyContent="space-around" alignItems="center" spacing={3}>
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
                      bgcolor:
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
      <ReactApexChart
        options={chartData.options || { chart: { type: 'bar' } } as any}
        series={(chartData.series as any) || []}
        type={(chartData.options?.chart?.type as ChartProps['type']) || 'bar'}
        height={(chartData.options?.chart?.height as any) || 260}
      />
    </MainCard>
  );
};

export default MarketChartCard;
