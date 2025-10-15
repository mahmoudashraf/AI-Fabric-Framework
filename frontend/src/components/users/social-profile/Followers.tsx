'use client';
import { Grid, InputAdornment, OutlinedInput, Typography  } from '@mui/material';
import { IconSearch } from '@tabler/icons-react';
import React from 'react';

// material-ui

// project imports
import { useUser } from 'contexts/UserContext';
import { useNotifications } from 'contexts/NotificationContext';
import { gridSpacing } from 'constants/index';
import { FollowerCardProps } from 'types/user';
import FollowerCard from 'ui-component/cards/FollowerCard';
import MainCard from 'ui-component/cards/MainCard';

// assets

// types

// ==============================|| SOCIAL PROFILE - FOLLOWERS ||============================== //

const Followers = () => {
  // Using Context API
  const userContext = useUser();
  const notificationContext = useNotifications();
  
  // Use Context API directly
  
  const [followers, setFollowers] = React.useState<FollowerCardProps[]>([]);
  
  // Use Context API directly
  const followersData = userContext.state.followers;

  React.useEffect(() => {
    setFollowers(followersData);
  }, [followersData]);

  React.useEffect(() => {
    try {
      userContext.getFollowers();
    } catch (error) {
      notificationContext.showNotification({
        message: 'Failed to load followers',
        variant: 'error',
        alert: { color: 'error', variant: 'filled' },
        close: true,
      });
    }
  }, [userContext, notificationContext]);

  const [search, setSearch] = React.useState<string | undefined>('');
  const handleSearch = async (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement> | undefined
  ) => {
    const newString = event?.target.value;
    setSearch(newString);

    try {
      if (newString) {
        userContext.filterFollowers(newString);
      } else {
        userContext.getFollowers();
      }
    } catch (error) {
      notificationContext.showNotification({
        message: 'Failed to search followers',
        variant: 'error',
        alert: { color: 'error', variant: 'filled' },
        close: true,
      });
    }
  };

  let followersResult: React.ReactElement | React.ReactElement[] = <></>;
  if (followers) {
    followersResult = followers.map((follower, index) => (
      <Grid key={index} size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
        <FollowerCard {...follower} />
      </Grid>
    ));
  }

  return (
    <MainCard
      title={
        <Grid container alignItems="center" justifyContent="space-between" spacing={gridSpacing}>
          <Grid>
            <Typography variant="h3">Followers</Typography>
          </Grid>
          <Grid>
            <OutlinedInput
              size="small"
              id="input-search-user-profile"
              placeholder="Search Followers"
              value={search}
              onChange={handleSearch}
              startAdornment={
                <InputAdornment position="start">
                  <IconSearch stroke={1.5} size="16px" />
                </InputAdornment>
              }
            />
          </Grid>
        </Grid>
      }
    >
      <Grid container direction="row" spacing={gridSpacing}>
        {followersResult}
      </Grid>
    </MainCard>
  );
};

export default Followers;
