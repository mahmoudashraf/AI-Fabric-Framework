'use client';
import { AvatarGroup,
  Button,
  Grid,
  LinearProgress,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableRow,
  Typography,
 } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import React from 'react';

// material-ui

// project imports
import { useUser } from 'contexts/UserContext';
import { useNotifications } from 'contexts/NotificationContext';
// Using Context API
import { gridSpacing } from 'constants/index';
import { UserProfileStyle2 } from 'types/user';
import Avatar from 'ui-component/extended/Avatar';

// Using Context API

// types

// asset
const Avatar1 = '/assets/images/users/avatar-1.png';
const Avatar2 = '/assets/images/users/avatar-2.png';
const Avatar3 = '/assets/images/users/avatar-3.png';
const Avatar4 = '/assets/images/users/avatar-4.png';
const Avatar5 = '/assets/images/users/avatar-5.png';

import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ChatBubbleTwoToneIcon from '@mui/icons-material/ChatBubbleTwoTone';
import BlockTwoToneIcon from '@mui/icons-material/BlockTwoTone';

// ==============================|| USER LIST 2 ||============================== //

const UserCard = () => {
  const theme = useTheme();
  
  // Fully migrated to Context system
  const userContext = useUser();
  const notificationContext = useNotifications();

  const [rows, setRows] = React.useState<UserProfileStyle2[]>([]);
  
  // Use Context data directly
  const usersS2 = userContext.usersS2;

  React.useEffect(() => {
    setRows(usersS2);
  }, [usersS2]);

  React.useEffect(() => {
    try {
      userContext.getUsersListStyle2();
    } catch (error) {
      notificationContext.showNotification({
          open: true,
          message: 'Failed to load users style 2',
          variant: 'alert',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userContext]);

  return (
    <TableContainer>
      <Table
        sx={{
          '& td': {
            whiteSpace: 'nowrap',
          },
          '& td:first-of-type': {
            pl: 0,
          },
          '& td:last-of-type': {
            pr: 0,
            minWidth: 260,
          },
          '& tbody tr:last-of-type  td': {
            borderBottom: 'none',
          },
          [theme.breakpoints.down('xl')]: {
            '& tr:not(:last-of-type)': {
              borderBottom: '1px solid',
              borderBottomColor:
                theme.palette.mode === 'dark' ? 'rgb(132, 146, 196, .2)' : 'rgba(224, 224, 224, 1)',
            },
            '& td': {
              display: 'inline-block',
              borderBottom: 'none',
              pl: 0,
            },
            '& td:first-of-type': {
              display: 'block',
            },
          },
        }}
      >
        <TableBody>
          {rows &&
            rows.map((row, index) => (
              <TableRow key={index}>
                <TableCell>
                  <Grid container spacing={2}>
                    <Grid>
                      <Avatar
                        alt="User 1"
                        src={`/assets/images/users/${row.image}`}
                        sx={{ width: 60, height: 60 }}
                      />
                    </Grid>
                    <Grid size={{ xs: 12, sm: 9 }} sx={{ minWidth: 0 }}>
                      <Grid container spacing={1}>
                        <Grid size={{ xs: 12 }}>
                          <Typography align="left" variant="subtitle1">
                            {row.name}{' '}
                            {row.badgeStatus === 'active' && (
                              <CheckCircleIcon
                                sx={{ color: 'success.dark', width: 14, height: 14 }}
                              />
                            )}
                          </Typography>
                          <Typography
                            align="left"
                            variant="subtitle2"
                            sx={{ whiteSpace: 'break-spaces' }}
                          >
                            {row.designation}
                          </Typography>
                        </Grid>
                        <Grid size={{ xs: 12 }}>
                          <Typography
                            align="left"
                            variant="body2"
                            sx={{ whiteSpace: 'break-spaces' }}
                          >
                            {row.subContent}
                          </Typography>
                        </Grid>
                      </Grid>
                    </Grid>
                  </Grid>
                </TableCell>
                <TableCell>
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12 }}>
                      <Typography variant="caption">Email</Typography>
                      <Typography variant="h6">{row.email}</Typography>
                    </Grid>
                    <Grid size={{ xs: 12 }}>
                      <Typography variant="caption">Phone</Typography>
                      <Typography variant="h6">{row.phone}</Typography>
                    </Grid>
                  </Grid>
                </TableCell>
                <TableCell>
                  <Grid container spacing={1}>
                    <Grid size={{ xs: 12 }}>
                      <Typography variant="caption">Location</Typography>
                      <Typography variant="h6">{row.location}</Typography>
                    </Grid>
                    <Grid size={{ xs: 12 }}>
                      <Grid container>
                        <AvatarGroup
                          max={4}
                          sx={{
                            '& .MuiAvatar-root': {
                              width: 32,
                              height: 32,
                              fontSize: '1rem',
                            },
                          }}
                        >
                          <Avatar alt="User 1" src={Avatar1} />
                          <Avatar alt="User 2" src={Avatar2} />
                          <Avatar alt="User 3" src={Avatar3} />
                          <Avatar alt="User 4" src={Avatar4} />
                          <Avatar alt="User 5" src={Avatar5} />
                        </AvatarGroup>
                      </Grid>
                    </Grid>
                  </Grid>
                </TableCell>
                <TableCell>
                  <Grid container spacing={3}>
                    <Grid size={{ xs: 12 }}>
                      <Grid container alignItems="center" spacing={gridSpacing}>
                        <Grid>
                          <Typography variant="caption">Progress</Typography>
                        </Grid>
                        <Grid size={{ xs: 12, sm: 9 }} sx={{ minWidth: 0 }}>
                          <LinearProgress
                            variant="determinate"
                            value={56}
                            color="primary"
                            sx={{ minWidth: 90 }}
                          />
                        </Grid>
                        <Grid>
                          <Typography variant="h6" component="div">
                            {row.progressValue}
                          </Typography>
                        </Grid>
                      </Grid>
                    </Grid>
                    <Grid size={{ xs: 12 }} container spacing={1}>
                      <Grid size={6}>
                        <Button
                          variant="outlined"
                          fullWidth
                          size="small"
                          sx={{ minWidth: 120 }}
                          startIcon={<ChatBubbleTwoToneIcon />}
                        >
                          Message
                        </Button>
                      </Grid>
                      <Grid size={6}>
                        <Button
                          variant="outlined"
                          color="error"
                          fullWidth
                          size="small"
                          sx={{ minWidth: 120 }}
                          startIcon={<BlockTwoToneIcon />}
                        >
                          Block
                        </Button>
                      </Grid>
                    </Grid>
                  </Grid>
                </TableCell>
              </TableRow>
            ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
};

export default UserCard;
