// material-ui
import FiberManualRecordIcon from '@mui/icons-material/FiberManualRecord';
import WatchLaterTwoToneIcon from '@mui/icons-material/WatchLaterTwoTone';
import { Avatar,
  Box,
  Badge,
  Button,
  CardActions,
  CardContent,
  Divider,
  Grid,
  Typography,
 } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project imports
import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';

// assets

const Avatar1 = '/assets/images/users/avatar-1.png';
const Avatar2 = '/assets/images/users/avatar-2.png';
const Avatar3 = '/assets/images/users/avatar-3.png';

// ===========================|| DATA WIDGET - USER ACTIVITY CARD ||=========================== //

const UserActivity = () => {
  const theme = useTheme();

  const iconSX = {
    fontSize: '0.875rem',
    marginRight: 0.2,
    verticalAlign: 'sub',
  };

  return (
    <MainCard title="User Activity" content={false}>
      <CardContent>
        <Grid container spacing={gridSpacing} alignItems="center">
          <Grid size={{ xs: 12 }}>
            <Grid container spacing={2}>
              <Grid>
                <Box sx={{ position: 'relative' }}>
                  <Badge
                    overlap="circular"
                    badgeContent={
                      <FiberManualRecordIcon
                        sx={{ color: theme.palette.success.main, fontSize: '0.875rem' }}
                      />
                    }
                    anchorOrigin={{
                      vertical: 'bottom',
                      horizontal: 'right',
                    }}
                  >
                    <Avatar alt="image" src={Avatar1} />
                  </Badge>
                </Box>
              </Grid>
              <Grid size={{ xs: 12 }} sx={{ minWidth: 0 }}>
                <Typography align="left" component="div" variant="subtitle1">
                  John Deo
                </Typography>
                <Typography align="left" component="div" variant="subtitle2">
                  Lorem Ipsum is simply dummy text.
                </Typography>
              </Grid>
              <Grid>
                <Typography align="left" variant="caption">
                  <WatchLaterTwoToneIcon sx={iconSX} />2 min ago
                </Typography>
              </Grid>
            </Grid>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Grid container spacing={2}>
              <Grid>
                <Box sx={{ position: 'relative' }}>
                  <Badge
                    overlap="circular"
                    badgeContent={
                      <FiberManualRecordIcon
                        sx={{ color: theme.palette.error.main, fontSize: '0.875rem' }}
                      />
                    }
                    anchorOrigin={{
                      vertical: 'bottom',
                      horizontal: 'right',
                    }}
                  >
                    <Avatar alt="image" src={Avatar2} />
                  </Badge>
                </Box>
              </Grid>
              <Grid size={{ xs: 12 }} sx={{ minWidth: 0 }}>
                <Typography align="left" variant="subtitle1">
                  John Deo
                </Typography>
                <Typography align="left" variant="subtitle2">
                  Lorem Ipsum is simply dummy text.
                </Typography>
              </Grid>
              <Grid>
                <Typography align="left" variant="caption">
                  <WatchLaterTwoToneIcon sx={iconSX} />2 min ago
                </Typography>
              </Grid>
            </Grid>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Grid container spacing={2}>
              <Grid>
                <Box sx={{ position: 'relative' }}>
                  <Badge
                    overlap="circular"
                    badgeContent={
                      <FiberManualRecordIcon
                        sx={{ color: theme.palette.warning.main, fontSize: '0.875rem' }}
                      />
                    }
                    anchorOrigin={{
                      vertical: 'bottom',
                      horizontal: 'right',
                    }}
                  >
                    <Avatar alt="image" src={Avatar3} />
                  </Badge>
                </Box>
              </Grid>
              <Grid size={{ xs: 12 }} sx={{ minWidth: 0 }}>
                <Typography align="left" component="div" variant="subtitle1">
                  John Deo
                </Typography>
                <Typography align="left" component="div" variant="subtitle2">
                  Lorem Ipsum is simply dummy text.
                </Typography>
              </Grid>
              <Grid>
                <Typography align="left" variant="caption">
                  <WatchLaterTwoToneIcon sx={iconSX} />2 min ago
                </Typography>
              </Grid>
            </Grid>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Grid container spacing={2}>
              <Grid>
                <Box sx={{ position: 'relative' }}>
                  <Badge
                    overlap="circular"
                    badgeContent={
                      <FiberManualRecordIcon
                        sx={{ color: theme.palette.success.main, fontSize: '0.875rem' }}
                      />
                    }
                    anchorOrigin={{
                      vertical: 'bottom',
                      horizontal: 'right',
                    }}
                  >
                    <Avatar alt="image" src={Avatar1} />
                  </Badge>
                </Box>
              </Grid>
              <Grid size={{ xs: 12 }} sx={{ minWidth: 0 }}>
                <Typography align="left" component="div" variant="subtitle1">
                  John Deo
                </Typography>
                <Typography align="left" component="div" variant="subtitle2">
                  Lorem Ipsum is simply dummy text.
                </Typography>
              </Grid>
              <Grid>
                <Typography align="left" variant="caption">
                  <WatchLaterTwoToneIcon sx={iconSX} />2 min ago
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
};

export default UserActivity;
