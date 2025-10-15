// material-ui
import { Avatar, Button, CardActions, CardContent, Divider, Grid, Typography  } from '@mui/material';

// project imports
import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';

// assets
const Avatar1 = '/assets/images/users/avatar-1.png';
const Avatar2 = '/assets/images/users/avatar-2.png';
const Avatar3 = '/assets/images/users/avatar-3.png';
const Avatar4 = '/assets/images/users/avatar-4.png';

// ===========================|| DATA WIDGET - TEAM MEMBERS CARD ||=========================== //

const TeamMembers = () => (
  <MainCard title="Team Members" content={false}>
    <CardContent>
      <Grid container spacing={gridSpacing} alignItems="center">
        <Grid size={{ xs: 12 }}>
          <Grid container spacing={2} alignItems="center">
            <Grid>
              <Avatar alt="User 1" src={Avatar1} />
            </Grid>
            <Grid size="grow" sx={{ minWidth: 0 }}>
              <Typography align="left" component="div" variant="subtitle1">
                David Jones
              </Typography>
              <Typography align="left" component="div" variant="subtitle2">
                Developer
              </Typography>
            </Grid>
            <Grid>
              <Typography align="left" variant="caption">
                5 min ago
              </Typography>
            </Grid>
          </Grid>
        </Grid>
        <Grid size={{ xs: 12 }}>
          <Grid container spacing={2} alignItems="center">
            <Grid>
              <Avatar alt="User 1" src={Avatar2} />
            </Grid>
            <Grid size="grow" sx={{ minWidth: 0 }}>
              <Typography align="left" component="div" variant="subtitle1">
                David Jones
              </Typography>
              <Typography align="left" component="div" variant="subtitle2">
                Developer
              </Typography>
            </Grid>
            <Grid>
              <Typography align="left" variant="caption">
                Today
              </Typography>
            </Grid>
          </Grid>
        </Grid>
        <Grid size={{ xs: 12 }}>
          <Grid container spacing={2} alignItems="center">
            <Grid>
              <Avatar alt="User 1" src={Avatar3} />
            </Grid>
            <Grid size="grow" sx={{ minWidth: 0 }}>
              <Typography align="left" component="div" variant="subtitle1">
                David Jones
              </Typography>
              <Typography align="left" component="div" variant="subtitle2">
                Developer
              </Typography>
            </Grid>
            <Grid>
              <Typography align="left" variant="caption">
                Yesterday
              </Typography>
            </Grid>
          </Grid>
        </Grid>
        <Grid size={{ xs: 12 }}>
          <Grid container spacing={2} alignItems="center">
            <Grid>
              <Avatar alt="User 1" src={Avatar4} />
            </Grid>
            <Grid size="grow" sx={{ minWidth: 0 }}>
              <Typography align="left" component="div" variant="subtitle1">
                David Jones
              </Typography>
              <Typography align="left" component="div" variant="subtitle2">
                Developer
              </Typography>
            </Grid>
            <Grid>
              <Typography align="left" variant="caption">
                02-05-2021
              </Typography>
            </Grid>
          </Grid>
        </Grid>
      </Grid>
    </CardContent>
    <Divider />
    <CardActions sx={{ justifyContent: 'flex-end' }}>
      <Button variant="text" size="small">
        View all Projects
      </Button>
    </CardActions>
  </MainCard>
);

export default TeamMembers;
