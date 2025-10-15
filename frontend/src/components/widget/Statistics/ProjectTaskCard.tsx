// material-ui
import { Grid, LinearProgress, Typography  } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project imports
import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';

// ===========================|| WIDGET STATISTICS - PROJECT TASK CARD ||=========================== //

const ProjectTaskCard = () => {
  const theme = useTheme();
  return (
    <MainCard>
      <Grid container alignItems="center" spacing={gridSpacing}>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <Grid container spacing={1}>
            <Grid size={{ xs: 12 }}>
              <Typography variant="subtitle2" align="left">
                Published Project
              </Typography>
            </Grid>
            <Grid size={{ xs: 12 }}>
              <Typography variant="h3" align="left">
                532
              </Typography>
            </Grid>
            <Grid size={{ xs: 12 }}>
              <LinearProgress
                aria-label="project progress"
                variant="determinate"
                value={40}
                color="secondary"
              />
            </Grid>
          </Grid>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <Grid container spacing={1}>
            <Grid size={{ xs: 12 }}>
              <Typography variant="subtitle2" align="left">
                Completed Task
              </Typography>
            </Grid>
            <Grid size={{ xs: 12 }}>
              <Typography variant="h3" align="left">
                4,569
              </Typography>
            </Grid>
            <Grid size={{ xs: 12 }}>
              {/** had wrong colour, colour is an enum not string */}
              <LinearProgress
                variant="determinate"
                value={70}
                sx={{
                  bgcolor: theme.palette.success.light,
                  '& .MuiLinearProgress-bar': { bgcolor: theme.palette.success.dark },
                }}
              />
            </Grid>
          </Grid>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <Grid container spacing={1}>
            <Grid size={{ xs: 12 }}>
              <Typography variant="subtitle2" align="left">
                Pending Task
              </Typography>
            </Grid>
            <Grid size={{ xs: 12 }}>
              <Typography variant="h3" align="left">
                1,005
              </Typography>
            </Grid>
            <Grid size={{ xs: 12 }}>
              {/** had wrong colour, colour is an enum not string */}
              <LinearProgress
                variant="determinate"
                value={30}
                sx={{
                  bgcolor: theme.palette.orange.light,
                  '& .MuiLinearProgress-bar': { bgcolor: theme.palette.orange.main },
                }}
              />
            </Grid>
          </Grid>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <Grid container spacing={1}>
            <Grid size={{ xs: 12 }}>
              <Typography variant="subtitle2" align="left">
                Issues
              </Typography>
            </Grid>
            <Grid size={{ xs: 12 }}>
              <Typography variant="h3" align="left">
                365
              </Typography>
            </Grid>
            <Grid size={{ xs: 12 }}>
              <LinearProgress
                variant="determinate"
                value={10}
                sx={{
                  bgcolor: theme.palette.error.light,
                  '& .MuiLinearProgress-bar': { bgcolor: theme.palette.error.main },
                }}
              />
            </Grid>
          </Grid>
        </Grid>
      </Grid>
    </MainCard>
  );
};

export default ProjectTaskCard;
