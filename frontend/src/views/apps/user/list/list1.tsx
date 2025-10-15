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
import UserList from 'components/users/list/Style1/UserList';
import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';

// assets

// ==============================|| USER LIST STYLE 1 ||============================== //

const ListStylePage1 = () => {
  const theme = useTheme();
  const [anchorEl, setAnchorEl] = React.useState<Element | (() => Element) | null | undefined>(
    null
  );
  const handleClick = (event: React.MouseEvent) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };
  return (
    <MainCard
      title={
        <Grid container alignItems="center" justifyContent="space-between" spacing={gridSpacing}>
          <Grid>
            <Typography variant="h3">List</Typography>
          </Grid>
          <Grid>
            <OutlinedInput
              id="input-search-list-style1"
              placeholder="Search"
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
      content={false}
    >
      <UserList />
      <Grid size={{ xs: 12 }} sx={{ p: 3 }}>
        <Grid container justifyContent="space-between" spacing={gridSpacing}>
          <Grid>
            <Pagination count={10} color="primary" />
          </Grid>
          <Grid>
            <Button
              size="large"
              sx={{ color: theme.palette.grey[900] }}
              color="secondary"
              endIcon={<ExpandMoreRoundedIcon />}
              onClick={handleClick}
            >
              10 Rows
            </Button>
            <Menu
              id="menu-user-list-style1"
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
          </Grid>
        </Grid>
      </Grid>
    </MainCard>
  );
};

// Enterprise Pattern: Apply error boundary HOC
import { withErrorBoundary } from '@/components/enterprise';
export default withErrorBoundary(ListStylePage1);
