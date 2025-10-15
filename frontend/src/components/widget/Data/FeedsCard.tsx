// material-ui
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionTwoTone';
import NotificationsNoneOutlinedIcon from '@mui/icons-material/NotificationsTwoTone';
import ShoppingCartOutlinedIcon from '@mui/icons-material/ShoppingCartTwoTone';
import { Box, Button, CardActions, CardContent, Divider, Grid, Typography  } from '@mui/material';

// project imports
import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';
import Avatar from 'ui-component/extended/Avatar';

// assets

// ==============================|| DATA WIDGET - FEEDS CARD ||============================== //

const FeedsCard = () => (
  <MainCard title="Feeds" content={false}>
    <CardContent>
      <Grid container spacing={gridSpacing}>
        <Grid size={{ xs: 12 }}>
          <Grid container spacing={2} alignItems="center" justifyContent="center">
            <Grid>
              <Box sx={{ position: 'relative' }}>
                <Avatar color="primary">
                  <NotificationsNoneOutlinedIcon />
                </Avatar>
              </Box>
            </Grid>
            <Grid size="grow" sx={{ minWidth: 0 }}>
              <Grid container spacing={1}>
                <Grid size="grow" sx={{ minWidth: 0 }}>
                  <Typography align="left" variant="body2">
                    You have 3 pending tasks.
                  </Typography>
                </Grid>
                <Grid>
                  <Typography align="left" variant="caption">
                    Just Now
                  </Typography>
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        </Grid>
        <Grid size={{ xs: 12 }}>
          <Grid container spacing={2} alignItems="center" justifyContent="center">
            <Grid>
              <Box sx={{ position: 'relative' }}>
                <Avatar color="error">
                  <ShoppingCartOutlinedIcon />
                </Avatar>
              </Box>
            </Grid>
            <Grid size="grow" sx={{ minWidth: 0 }}>
              <Grid container spacing={1}>
                <Grid size="grow" sx={{ minWidth: 0 }}>
                  <Typography align="left" variant="body2">
                    New order received
                  </Typography>
                </Grid>
                <Grid>
                  <Typography align="left" variant="caption">
                    Just Now
                  </Typography>
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        </Grid>
        <Grid size={{ xs: 12 }}>
          <Grid container spacing={2} alignItems="center" justifyContent="center">
            <Grid>
              <Box sx={{ position: 'relative' }}>
                <Avatar color="success">
                  <DescriptionOutlinedIcon />
                </Avatar>
              </Box>
            </Grid>
            <Grid size="grow" sx={{ minWidth: 0 }}>
              <Grid container spacing={1}>
                <Grid size="grow" sx={{ minWidth: 0 }}>
                  <Typography align="left" variant="body2">
                    You have 3 pending tasks.
                  </Typography>
                </Grid>
                <Grid>
                  <Typography align="left" variant="caption">
                    Just Now
                  </Typography>
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        </Grid>
        <Grid size={{ xs: 12 }}>
          <Grid container spacing={2} alignItems="center" justifyContent="center">
            <Grid>
              <Box sx={{ position: 'relative' }}>
                <Avatar color="primary">
                  <NotificationsNoneOutlinedIcon />
                </Avatar>
              </Box>
            </Grid>
            <Grid size="grow" sx={{ minWidth: 0 }}>
              <Grid container spacing={1}>
                <Grid size="grow" sx={{ minWidth: 0 }}>
                  <Typography align="left" variant="body2">
                    New order received
                  </Typography>
                </Grid>
                <Grid>
                  <Typography align="left" variant="caption">
                    Just Now
                  </Typography>
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        </Grid>
        <Grid size={{ xs: 12 }}>
          <Grid container spacing={2} alignItems="center" justifyContent="center">
            <Grid>
              <Box sx={{ position: 'relative' }}>
                <Avatar color="warning">
                  <ShoppingCartOutlinedIcon />
                </Avatar>
              </Box>
            </Grid>
            <Grid size="grow" sx={{ minWidth: 0 }}>
              <Grid container spacing={1}>
                <Grid size="grow" sx={{ minWidth: 0 }}>
                  <Typography align="left" variant="body2">
                    Order cancelled
                  </Typography>
                </Grid>
                <Grid>
                  <Typography align="left" variant="caption">
                    Just Now
                  </Typography>
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        </Grid>
      </Grid>
    </CardContent>
    <Divider />
    <CardActions sx={{ justifyContent: 'flex-end' }}>
      <Button variant="text" size="small">
        View all Feeds
      </Button>
    </CardActions>
  </MainCard>
);

export default FeedsCard;
