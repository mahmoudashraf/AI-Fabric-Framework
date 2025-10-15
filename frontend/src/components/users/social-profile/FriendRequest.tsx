'use client';
import { Grid, InputAdornment, OutlinedInput, Typography  } from '@mui/material';
import { IconSearch } from '@tabler/icons-react';
import React from 'react';

// material-ui

// project imports
import { useUser } from 'contexts/UserContext';
import { useNotifications } from 'contexts/NotificationContext';
// Using Context API
// Grid spacing constant (moved from store)
const gridSpacing = 3;
import { FriendRequestCardProps } from 'types/user';
import FriendRequestCard from 'ui-component/cards/FriendRequestCard';
import MainCard from 'ui-component/cards/MainCard';

// Using Context API

// types

// assets

// ==============================|| SOCIAL PROFILE - FRIEND REQUEST ||============================== //

const FriendRequest = () => {
  // Use Context API for user and notifications
  const userContext = useUser();
  const notificationContext = useNotifications();
  
  const [friendRequest, setFriendRequest] = React.useState<FriendRequestCardProps[]>([]);

  const [search, setSearch] = React.useState<string | undefined>('');

  const handleSearch = async (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement> | undefined
  ) => {
    const newString = event?.target.value;
    setSearch(newString);

    try {
      if (newString) {
        userContext.filterFriendRequests(newString);
      } else {
        userContext.getFriendRequests();
      }
    } catch (error) {
      notificationContext.showNotification({
        open: true,
        message: 'Failed to search friend requests',
        variant: 'alert',
        alert: {
          color: 'error',
          variant: 'filled',
        },
        close: true,
      });
    }
  };

  React.useEffect(() => {
    const friendRequestData = userContext.friendRequests;
    setFriendRequest(friendRequestData);
  }, [userContext.friendRequests]);

  React.useEffect(() => {
    try {
      userContext.getFriendRequests();
    } catch (error) {
      notificationContext.showNotification({
        open: true,
        message: 'Failed to load friend requests',
        variant: 'alert',
        alert: {
          color: 'error',
          variant: 'filled',
        },
        close: true,
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userContext]);

  let friendRequestResult: React.ReactElement | React.ReactElement[] = <></>;
  if (friendRequest) {
    friendRequestResult = friendRequest.map((friend, index) => (
      <Grid key={index} size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
        <FriendRequestCard {...friend} />
      </Grid>
    ));
  }

  return (
    <MainCard
      title={
        <Grid container alignItems="center" justifyContent="space-between" spacing={gridSpacing}>
          <Grid>
            <Typography variant="h3">Friend Request</Typography>
          </Grid>
          <Grid>
            <OutlinedInput
              size="small"
              id="input-search-user-profile"
              placeholder="Search Friends"
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
        {friendRequestResult}
      </Grid>
    </MainCard>
  );
};

export default FriendRequest;
