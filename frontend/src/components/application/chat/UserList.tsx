'use client';

// material-ui
import { Chip,
  Divider,
  Grid,
  List,
  ListItemButton,
  ListItemAvatar,
  ListItemText,
  Typography,
 } from '@mui/material';
import { useEffect, useState, Fragment } from 'react';

// project imports
import { useChat } from 'contexts/ChatContext';
import { useNotifications } from 'contexts/NotificationContext';

// types
import { UserProfile } from 'types/user-profile';

import UserAvatar from './UserAvatar';

// ==============================|| CHAT USER LIST ||============================== //

interface UserListProps {
  setUser: (u: UserProfile) => void;
}

const UserList = ({ setUser }: UserListProps) => {
  // Pure Context API usage
  const chatContext = useChat();
  const notificationContext = useNotifications();
  
  const [data, setData] = useState<UserProfile[]>([]);
  
  // Use Context API directly
  const users = chatContext.state.users;

  useEffect(() => {
    // Use ChatContext methods directly
    try {
      chatContext.getUsers();
    } catch (error) {
      notificationContext.showNotification({
        message: 'Failed to load users',
        variant: 'error',
        alert: { color: 'error', variant: 'filled' },
        close: true,
      });
    }
  }, [chatContext, notificationContext]);

  useEffect(() => {
    setData(users);
  }, [users]);

  return (
    <List component="nav">
      {data.map(user => (
        <Fragment key={user.id}>
          <ListItemButton
            onClick={() => {
              setUser(user);
            }}
          >
            <ListItemAvatar>
              <UserAvatar user={user} />
            </ListItemAvatar>
            <ListItemText
              primary={
                <Grid container alignItems="center" spacing={1} component="span">
                  <Grid size="grow" sx={{ minWidth: 0 }} component="span">
                    <Typography
                      variant="h5"
                      color="inherit"
                      component="span"
                      sx={{
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                        display: 'block',
                      }}
                    >
                      {user.name}
                    </Typography>
                  </Grid>
                  <Grid component="span">
                    <Typography component="span" variant="subtitle2">
                      {user.lastMessage}
                    </Typography>
                  </Grid>
                </Grid>
              }
              secondary={
                <Grid container alignItems="center" spacing={1} component="span">
                  <Grid size="grow" sx={{ minWidth: 0 }} component="span">
                    <Typography
                      variant="caption"
                      component="span"
                      sx={{
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                        display: 'block',
                      }}
                    >
                      {user.status}
                    </Typography>
                  </Grid>
                  <Grid component="span">
                    {user.unReadChatCount !== 0 && (
                      <Chip
                        label={user.unReadChatCount}
                        component="span"
                        color="secondary"
                        sx={{
                          width: 20,
                          height: 20,
                          '& .MuiChip-label': {
                            px: 0.5,
                          },
                        }}
                      />
                    )}
                  </Grid>
                </Grid>
              }
            />
          </ListItemButton>
          <Divider />
        </Fragment>
      ))}
    </List>
  );
};

export default UserList;
