// material-ui
import { Grid, LinearProgress, Typography  } from '@mui/material';

// project imports
import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';

// ===========================|| DATA WIDGET - TRAFFIC SOURCES ||=========================== //

const TrafficSources = () => (
  <MainCard title="Traffic Sources">
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12 }}>
        <Grid container alignItems="center" spacing={1}>
          <Grid size="grow" sx={{ minWidth: 0 }}>
            <Typography variant="body2">Direct</Typography>
          </Grid>
          <Grid>
            <Typography variant="body2" align="right">
              80%
            </Typography>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <LinearProgress
              variant="determinate"
              aria-label="traffic progress"
              value={80}
              color="primary"
            />
          </Grid>
        </Grid>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <Grid container alignItems="center" spacing={1}>
          <Grid size="grow" sx={{ minWidth: 0 }}>
            <Typography variant="body2">Social</Typography>
          </Grid>
          <Grid>
            <Typography variant="body2" align="right">
              50%
            </Typography>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <LinearProgress
              variant="determinate"
              aria-label="traffic progress"
              value={50}
              color="secondary"
            />
          </Grid>
        </Grid>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <Grid container alignItems="center" spacing={1}>
          <Grid size="grow" sx={{ minWidth: 0 }}>
            <Typography variant="body2">Referral</Typography>
          </Grid>
          <Grid>
            <Typography variant="body2" align="right">
              20%
            </Typography>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <LinearProgress
              variant="determinate"
              aria-label="traffic progress"
              value={20}
              color="primary"
            />
          </Grid>
        </Grid>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <Grid container alignItems="center" spacing={1}>
          <Grid size="grow" sx={{ minWidth: 0 }}>
            <Typography variant="body2">Bounce</Typography>
          </Grid>
          <Grid>
            <Typography variant="body2" align="right">
              58%
            </Typography>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <LinearProgress
              variant="determinate"
              aria-label="traffic progress"
              value={60}
              color="secondary"
            />
          </Grid>
        </Grid>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <Grid container alignItems="center" spacing={1}>
          <Grid size="grow" sx={{ minWidth: 0 }}>
            <Typography variant="body2">Internet</Typography>
          </Grid>
          <Grid>
            <Typography variant="body2" align="right">
              40%
            </Typography>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <LinearProgress
              variant="determinate"
              aria-label="traffic progress"
              value={40}
              color="primary"
            />
          </Grid>
        </Grid>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <Grid container alignItems="center" spacing={1}>
          <Grid size="grow" sx={{ minWidth: 0 }}>
            <Typography variant="body2">Social</Typography>
          </Grid>
          <Grid>
            <Typography variant="body2" align="right">
              90%
            </Typography>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <LinearProgress
              variant="determinate"
              aria-label="traffic progress"
              value={90}
              color="primary"
            />
          </Grid>
        </Grid>
      </Grid>
    </Grid>
  </MainCard>
);

export default TrafficSources;
