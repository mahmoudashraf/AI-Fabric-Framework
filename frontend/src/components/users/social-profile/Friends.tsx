'use client';
import { Grid, InputAdornment, OutlinedInput, Typography  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { IconSearch } from '@tabler/icons-react';
import React from 'react';

// material-ui

// project imports
import { useUser } from 'contexts/UserContext';
import { useNotifications } from 'contexts/NotificationContext';
// Using Context API
// Grid spacing constant (moved from store)
const gridSpacing = 3;
import { FriendsCardProps } from 'types/user';
import FriendsCard from 'ui-component/cards/FriendsCard';
import MainCard from 'ui-component/cards/MainCard';

// Using Context API

// types

// assets

// ==============================|| SOCIAL PROFILE - FRIENDS ||============================== //

const Friends = () => {
  const theme = useTheme();
  
  // Use Context API for user and notifications
  const userContext = useUser();
  const notificationContext = useNotifications();
  
  const [friends, setFriends] = React.useState<FriendsCardProps[]>([]);
  
  // Get friends data from context
  const friendsData = userContext.friends;

  React.useEffect(() => {
    setFriends(friendsData);
  }, [friendsData]);

  React.useEffect(() => {
    try {
      userContext.getFriends();
    } catch (error) {
      notificationContext.showNotification({
        open: true,
        message: 'Failed to load friends',
        variant: 'alert',
        alert: { color: 'error', variant: 'filled' },
        close: true,
      });
    }
  }, [userContext]);

  let friendsResult: React.ReactElement | React.ReactElement[] = <></>;
  if (friends) {
    friendsResult = friends.map((friend, index) => (
      <Grid key={index} size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
        <FriendsCard {...friend} />
      </Grid>
    ));
  }

  const [search, setSearch] = React.useState<string | undefined>('');
  const handleSearch = async (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement> | undefined
  ) => {
    const newString = event?.target.value;
    setSearch(newString);

    try {
      if (newString) {
        userContext.filterFriends(newString);
      } else {
        userContext.getFriends();
      }
    } catch (error) {
      notificationContext.showNotification({
        open: true,
        message: 'Failed to search friends',
        variant: 'alert',
        alert: { color: 'error', variant: 'filled' },
        close: true,
      });
    }
  };

  return (
    <MainCard
      title={
        <Grid container alignItems="center" justifyContent="space-between" spacing={gridSpacing}>
          <Grid>
            <Typography variant="h3">
              Friends{' '}
              <Typography
                variant="h3"
                component="span"
                sx={{ color: theme.palette.grey[300], fontWeight: 500 }}
              >
                (463)
              </Typography>
            </Typography>
          </Grid>
          <Grid>
            <OutlinedInput
              size="small"
              id="input-search-user-profile"
              placeholder="Search"
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
        {friendsResult}
      </Grid>
    </MainCard>
  );
};

export default Friends;
