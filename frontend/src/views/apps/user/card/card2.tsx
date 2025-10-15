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
import { UserSimpleCardProps } from 'types/user';
import MainCard from 'ui-component/cards/MainCard';
import UserSimpleCard from 'ui-component/cards/UserSimpleCard';

// Using Context API

// assets

// types

// ==============================|| USER CARD STYLE 2 ||============================== //

const CardStyle2 = () => {
  const theme = useTheme();
  
  // Using Context API
  const userContext = useUser();
  const notificationContext = useNotifications();
  
  // Use Context API directly
  
  const [users, setUsers] = React.useState<UserSimpleCardProps[]>([]);
  
  // Get simple cards data from Context API
  const simpleCards = userContext.state.simpleCards;
  
  const [anchorEl, setAnchorEl] = React.useState<Element | (() => Element) | null | undefined>(
    null
  );
  const handleClick = (event: React.MouseEvent) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  React.useEffect(() => {
    setUsers(simpleCards);
  }, [simpleCards]);

  // Enterprise Pattern: Async operation with retry
  const { execute: loadUsers } = useAsyncOperation(
    async () => {
      await userContext.getSimpleCards();
      return true as const;
    },
    {
      retryCount: 2,
      retryDelay: 500,
      onError: () => {
        notificationContext.showNotification({
          message: 'Failed to load simple user cards',
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

  const [search, setSearch] = React.useState<string | undefined>('');
  // Enterprise Pattern: Search with retry
  const { execute: searchUsers } = useAsyncOperation(
    async (query: string) => {
      if (query) {
        await userContext.filterSimpleCards(query);
      } else {
        await userContext.getSimpleCards();
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
        <UserSimpleCard {...user} />
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
              id="input-search-card-style2"
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
                color="secondary"
                endIcon={<ExpandMoreRoundedIcon />}
                onClick={handleClick}
              >
                10 Rows
              </Button>
              {anchorEl && (
                <Menu
                  id="menu-user-card-style2"
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
export default withErrorBoundary(CardStyle2);
