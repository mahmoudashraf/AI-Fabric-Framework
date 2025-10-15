'use client';

import ChevronRightOutlinedIcon from '@mui/icons-material/ChevronRightOutlined';
import KeyboardArrowDownOutlinedIcon from '@mui/icons-material/KeyboardArrowDownOutlined';
import KeyboardArrowUpOutlinedIcon from '@mui/icons-material/KeyboardArrowUpOutlined';
import MoreHorizOutlinedIcon from '@mui/icons-material/MoreHorizOutlined';
import { Avatar,
  Button,
  CardActions,
  CardContent,
  Divider,
  Grid,
  Menu,
  MenuItem,
  Typography,
 } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import React from 'react';

// material-ui

// project imports
import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';
import SkeletonPopularCard from 'ui-component/cards/Skeleton/PopularCard';

import BajajAreaChartCard from './BajajAreaChartCard';

// assets

// ==============================|| DASHBOARD DEFAULT - POPULAR CARD ||============================== //

interface PopularCardProps {
  isLoading: boolean;
}

const PopularCard = ({ isLoading }: PopularCardProps) => {
  const theme = useTheme();

  const [anchorEl, setAnchorEl] = React.useState<Element | (() => Element) | null | undefined>(
    null
  );

  const handleClick = (event: React.SyntheticEvent) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  return (
    <>
      {isLoading ? (
        <SkeletonPopularCard />
      ) : (
        <MainCard content={false}>
          <CardContent>
            <Grid container spacing={gridSpacing}>
              <Grid size={{ xs: 12 }}>
                <Grid container alignContent="center" justifyContent="space-between">
                  <Grid>
                    <Typography variant="h4">Popular Stocks</Typography>
                  </Grid>
                  <Grid>
                    <MoreHorizOutlinedIcon
                      fontSize="small"
                      sx={{
                        color: theme.palette.primary[200],
                        cursor: 'pointer',
                      }}
                      aria-controls="menu-popular-card"
                      aria-haspopup="true"
                      onClick={handleClick}
                    />
                    <Menu
                      id="menu-popular-card"
                      anchorEl={anchorEl}
                      keepMounted
                      open={Boolean(anchorEl)}
                      onClose={handleClose}
                      variant="selectedMenu"
                      anchorOrigin={{
                        vertical: 'bottom',
                        horizontal: 'right',
                      }}
                      transformOrigin={{
                        vertical: 'top',
                        horizontal: 'right',
                      }}
                    >
                      <MenuItem onClick={handleClose}> Today</MenuItem>
                      <MenuItem onClick={handleClose}> This Month</MenuItem>
                      <MenuItem onClick={handleClose}> This Year </MenuItem>
                    </Menu>
                  </Grid>
                </Grid>
              </Grid>
              <Grid size={{ xs: 12 }} sx={{ pt: '16px !important' }}>
                <BajajAreaChartCard />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Grid container direction="column">
                  <Grid>
                    <Grid container alignItems="center" justifyContent="space-between">
                      <Grid>
                        <Typography variant="subtitle1" color="inherit">
                          Bajaj Finery
                        </Typography>
                      </Grid>
                      <Grid>
                        <Grid container alignItems="center" justifyContent="space-between">
                          <Grid>
                            <Typography variant="subtitle1" color="inherit">
                              $1839.00
                            </Typography>
                          </Grid>
                          <Grid>
                            <Avatar
                              variant="rounded"
                              sx={{
                                width: 16,
                                height: 16,
                                borderRadius: '5px',
                                backgroundColor: theme.palette.success.light,
                                color: theme.palette.success.dark,
                                ml: 2,
                              }}
                            >
                              <KeyboardArrowUpOutlinedIcon fontSize="small" color="inherit" />
                            </Avatar>
                          </Grid>
                        </Grid>
                      </Grid>
                    </Grid>
                  </Grid>
                  <Grid>
                    <Typography variant="subtitle2" sx={{ color: 'success.dark' }}>
                      10% Profit
                    </Typography>
                  </Grid>
                </Grid>
                <Divider sx={{ my: 1.5 }} />
                <Grid container direction="column">
                  <Grid>
                    <Grid container alignItems="center" justifyContent="space-between">
                      <Grid>
                        <Typography variant="subtitle1" color="inherit">
                          TTML
                        </Typography>
                      </Grid>
                      <Grid>
                        <Grid container alignItems="center" justifyContent="space-between">
                          <Grid>
                            <Typography variant="subtitle1" color="inherit">
                              $100.00
                            </Typography>
                          </Grid>
                          <Grid>
                            <Avatar
                              variant="rounded"
                              sx={{
                                width: 16,
                                height: 16,
                                borderRadius: '5px',
                                backgroundColor: theme.palette.orange.light,
                                color: theme.palette.orange.dark,
                                marginLeft: 1.875,
                              }}
                            >
                              <KeyboardArrowDownOutlinedIcon fontSize="small" color="inherit" />
                            </Avatar>
                          </Grid>
                        </Grid>
                      </Grid>
                    </Grid>
                  </Grid>
                  <Grid>
                    <Typography variant="subtitle2" sx={{ color: theme.palette.orange.dark }}>
                      10% loss
                    </Typography>
                  </Grid>
                </Grid>
                <Divider sx={{ my: 1.5 }} />
                <Grid container direction="column">
                  <Grid>
                    <Grid container alignItems="center" justifyContent="space-between">
                      <Grid>
                        <Typography variant="subtitle1" color="inherit">
                          Reliance
                        </Typography>
                      </Grid>
                      <Grid>
                        <Grid container alignItems="center" justifyContent="space-between">
                          <Grid>
                            <Typography variant="subtitle1" color="inherit">
                              $200.00
                            </Typography>
                          </Grid>
                          <Grid>
                            <Avatar
                              variant="rounded"
                              sx={{
                                width: 16,
                                height: 16,
                                borderRadius: '5px',
                                backgroundColor: theme.palette.success.light,
                                color: theme.palette.success.dark,
                                ml: 2,
                              }}
                            >
                              <KeyboardArrowUpOutlinedIcon fontSize="small" color="inherit" />
                            </Avatar>
                          </Grid>
                        </Grid>
                      </Grid>
                    </Grid>
                  </Grid>
                  <Grid>
                    <Typography variant="subtitle2" sx={{ color: theme.palette.success.dark }}>
                      10% Profit
                    </Typography>
                  </Grid>
                </Grid>
                <Divider sx={{ my: 1.5 }} />
                <Grid container direction="column">
                  <Grid>
                    <Grid container alignItems="center" justifyContent="space-between">
                      <Grid>
                        <Typography variant="subtitle1" color="inherit">
                          TTML
                        </Typography>
                      </Grid>
                      <Grid>
                        <Grid container alignItems="center" justifyContent="space-between">
                          <Grid>
                            <Typography variant="subtitle1" color="inherit">
                              $189.00
                            </Typography>
                          </Grid>
                          <Grid>
                            <Avatar
                              variant="rounded"
                              sx={{
                                width: 16,
                                height: 16,
                                borderRadius: '5px',
                                backgroundColor: theme.palette.orange.light,
                                color: theme.palette.orange.dark,
                                ml: 2,
                              }}
                            >
                              <KeyboardArrowDownOutlinedIcon fontSize="small" color="inherit" />
                            </Avatar>
                          </Grid>
                        </Grid>
                      </Grid>
                    </Grid>
                  </Grid>
                  <Grid>
                    <Typography variant="subtitle2" sx={{ color: theme.palette.orange.dark }}>
                      10% loss
                    </Typography>
                  </Grid>
                </Grid>
                <Divider sx={{ my: 1.5 }} />
                <Grid container direction="column">
                  <Grid>
                    <Grid container alignItems="center" justifyContent="space-between">
                      <Grid>
                        <Typography variant="subtitle1" color="inherit">
                          Stolon
                        </Typography>
                      </Grid>
                      <Grid>
                        <Grid container alignItems="center" justifyContent="space-between">
                          <Grid>
                            <Typography variant="subtitle1" color="inherit">
                              $189.00
                            </Typography>
                          </Grid>
                          <Grid>
                            <Avatar
                              variant="rounded"
                              sx={{
                                width: 16,
                                height: 16,
                                borderRadius: '5px',
                                backgroundColor: theme.palette.orange.light,
                                color: theme.palette.orange.dark,
                                ml: 2,
                              }}
                            >
                              <KeyboardArrowDownOutlinedIcon fontSize="small" color="inherit" />
                            </Avatar>
                          </Grid>
                        </Grid>
                      </Grid>
                    </Grid>
                  </Grid>
                  <Grid>
                    <Typography variant="subtitle2" sx={{ color: theme.palette.orange.dark }}>
                      10% loss
                    </Typography>
                  </Grid>
                </Grid>
              </Grid>
            </Grid>
          </CardContent>
          <CardActions sx={{ p: 1.25, pt: 0, justifyContent: 'center' }}>
            <Button size="small" disableElevation>
              View All
              <ChevronRightOutlinedIcon />
            </Button>
          </CardActions>
        </MainCard>
      )}
    </>
  );
};

export default PopularCard;
