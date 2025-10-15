'use client';

import ExpandMoreRoundedIcon from '@mui/icons-material/ExpandMoreRounded';
import { Button,
  Grid,
  InputAdornment,
  Menu,
  MenuItem,
  OutlinedInput,
  Pagination,
  Typography,
 } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { IconSearch } from '@tabler/icons-react';
import React from 'react';

// material-ui

// project imports
import { useUser } from 'contexts/UserContext';
import { useNotifications } from 'contexts/NotificationContext';
import { useAsyncOperation } from '@/hooks/enterprise';
import { gridSpacing } from 'constants/index';
import { UserProfile } from 'types/user-profile';
import MainCard from 'ui-component/cards/MainCard';
import UserDetailsCard from 'ui-component/cards/UserDetailsCard';

// Using Context API

// assets

// types

// ==============================|| USER CARD STYLE 1 ||============================== //

const CardStyle1 = () => {
  const theme = useTheme();
  
  // Using Context API
  const userContext = useUser();
  const notificationContext = useNotifications();
  
  // Use Context API directly
  
  const [users, setUsers] = React.useState<UserProfile[]>([]);
  
  // Get user detail cards data from Context API
  const detailCards = userContext.state.detailCards;

  React.useEffect(() => {
    setUsers(detailCards);
  }, [detailCards]);

  // Enterprise Pattern: Async operation with retry
  const { execute: loadUsers } = useAsyncOperation(
    async () => {
      await userContext.getDetailCards();
      return true as const;
    },
    {
      retryCount: 2,
      retryDelay: 500,
      onError: () => {
        notificationContext.showNotification({
          message: 'Failed to load user detail cards',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      },
    }
  );

  React.useEffect(() => {
    loadUsers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const [anchorEl, setAnchorEl] = React.useState<Element | (() => Element) | null | undefined>(
    null
  );
  const handleClick = (event: React.MouseEvent) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const [search, setSearch] = React.useState<string | undefined>('');
  // Enterprise Pattern: Search with retry
  const { execute: searchUsers } = useAsyncOperation(
    async (query: string) => {
      if (query) {
        await userContext.filterDetailCards(query);
      } else {
        await userContext.getDetailCards();
      }
      return true;
    },
    {
      retryCount: 1,
      retryDelay: 300,
      onError: () => {
        notificationContext.showNotification({
          message: 'Failed to search users',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      },
    }
  );

  const handleSearch = async (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement> | undefined
  ) => {
    const newString = event?.target.value;
    setSearch(newString);
    searchUsers(newString || '');
  };

  let usersResult: React.ReactElement | React.ReactElement[] = <></>;
  if (users) {
    usersResult = users.map((user, index) => (
      <Grid key={index} size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
        <UserDetailsCard {...user} />
      </Grid>
    ));
  }

  return (
    <MainCard
      title={
        <Grid container alignItems="center" justifyContent="space-between" spacing={gridSpacing}>
          <Grid>
            <Typography variant="h3">Cards</Typography>
          </Grid>
          <Grid>
            <OutlinedInput
              id="input-search-card-style1"
              placeholder="Search"
              value={search}
              onChange={handleSearch}
              startAdornment={
                <InputAdornment position="start">
                  <IconSearch stroke={1.5} size="16px" />
                </InputAdornment>
              }
              size="small"
            />
          </Grid>
        </Grid>
      }
    >
      <Grid container direction="row" spacing={gridSpacing}>
        {usersResult}
        <Grid size={{ xs: 12 }}>
          <Grid container justifyContent="space-between" spacing={gridSpacing}>
            <Grid>
              <Pagination count={10} color="primary" />
            </Grid>
            <Grid>
              <Button
                variant="text"
                size="large"
                sx={{ color: theme.palette.grey[900] }}
                color="inherit"
                endIcon={<ExpandMoreRoundedIcon />}
                onClick={handleClick}
              >
                10 Rows
              </Button>
              {anchorEl && (
                <Menu
                  id="menu-user-card-style1"
                  anchorEl={anchorEl}
                  keepMounted
                  open={Boolean(anchorEl)}
                  onClose={handleClose}
                  variant="selectedMenu"
                  anchorOrigin={{
                    vertical: 'top',
                    horizontal: 'right',
                  }}
                  transformOrigin={{
                    vertical: 'bottom',
                    horizontal: 'right',
                  }}
                >
                  <MenuItem onClick={handleClose}> 10 Rows</MenuItem>
                  <MenuItem onClick={handleClose}> 20 Rows</MenuItem>
                  <MenuItem onClick={handleClose}> 30 Rows </MenuItem>
                </Menu>
              )}
            </Grid>
          </Grid>
        </Grid>
      </Grid>
    </MainCard>
  );
};

// Enterprise Pattern: Apply error boundary HOC
import { withErrorBoundary } from '@/components/enterprise';
export default withErrorBoundary(CardStyle1);
