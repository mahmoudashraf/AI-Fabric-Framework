// material-ui
import { Grid, LinearProgress, Typography  } from '@mui/material';

// project imports
import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';

// ===========================|| WIDGET STATISTICS - CUSTOMER SATISFACTION ||=========================== //

const CustomerSatisfactionCard = () => (
  <MainCard title="Customer satisfaction">
    <Grid container spacing={3}>
      <Grid size={{ xs: 12 }}>
        <Typography variant="h3" align="center">
          89.73%
        </Typography>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <LinearProgress
          variant="determinate"
          aria-label="Customer satisfaction percentage"
          value={67}
          color="primary"
        />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <Grid container spacing={gridSpacing}>
          <Grid size={4}>
            <Grid container spacing={1}>
              <Grid size={{ xs: 12 }}>
                <Typography variant="subtitle2">previous</Typography>
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Typography variant="h5">56.75</Typography>
              </Grid>
            </Grid>
          </Grid>
          <Grid size={4}>
            <Grid container spacing={1}>
              <Grid size={{ xs: 12 }}>
                <Typography variant="subtitle2">Change</Typography>
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Typography variant="h5">+12.60</Typography>
              </Grid>
            </Grid>
          </Grid>
          <Grid size={4}>
            <Grid container spacing={1}>
              <Grid size={{ xs: 12 }}>
                <Typography variant="subtitle2">Trend</Typography>
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Typography variant="h5">23.78</Typography>
              </Grid>
            </Grid>
          </Grid>
        </Grid>
      </Grid>
    </Grid>
  </MainCard>
);

export default CustomerSatisfactionCard;
