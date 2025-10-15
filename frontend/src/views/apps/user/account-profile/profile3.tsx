'use client';

import { Box, Tab, Tabs } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import Link from 'next/link';
import React from 'react';

// material-ui

// project imports
import Billing from 'components/users/account-profile/Profile3/Billing';
import Notifications from 'components/users/account-profile/Profile3/Notifications';
import Profile from 'components/users/account-profile/Profile3/Profile';
import Security from 'components/users/account-profile/Profile3/Security';
import { TabsProps } from 'types';
import MainCard from 'ui-component/cards/MainCard';

// types

// tabs
function TabPanel({ children, value, index, ...other }: TabsProps) {
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`simple-tabpanel-${index}`}
      aria-labelledby={`simple-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ p: 0 }}>{children}</Box>}
    </div>
  );
}

function a11yProps(index: number) {
  return {
    id: `simple-tab-${index}`,
    'aria-controls': `simple-tabpanel-${index}`,
  };
}

// ==============================|| PROFILE 3 ||============================== //

const Profile3 = () => {
  const theme = useTheme();
  const [value, setValue] = React.useState(0);
  const handleChange = (_event: React.SyntheticEvent, newValue: number) => {
    setValue(newValue);
  };

  return (
    <MainCard title="Account">
      <div>
        <Tabs
          value={value}
          indicatorColor="primary"
          onChange={handleChange}
          sx={{
            mb: 3,
            minHeight: 'auto',
            '& button': {
              minWidth: 100,
            },
            '& a': {
              minHeight: 'auto',
              minWidth: 10,
              py: 1.5,
              px: 1,
              mr: 2.25,
              color: theme.palette.mode === 'dark' ? 'grey.600' : 'grey.900',
            },
            '& a.Mui-selected': {
              color: 'primary.main',
            },
          }}
          aria-label="simple tabs example"
          variant="scrollable"
        >
          <Tab component={Link} href="#" label="Profile" {...a11yProps(0)} />
          <Tab component={Link} href="#" label="Billing" {...a11yProps(1)} />
          <Tab component={Link} href="#" label="Security" {...a11yProps(2)} />
          <Tab component={Link} href="#" label="Notifications" {...a11yProps(3)} />
        </Tabs>
        <TabPanel value={value} index={0}>
          <Profile />
        </TabPanel>
        <TabPanel value={value} index={1}>
          <Billing />
        </TabPanel>
        <TabPanel value={value} index={2}>
          <Security />
        </TabPanel>
        <TabPanel value={value} index={3}>
          <Notifications />
        </TabPanel>
      </div>
    </MainCard>
  );
};

// Enterprise Pattern: Apply error boundary HOC
import { withErrorBoundary } from '@/components/enterprise';
export default withErrorBoundary(Profile3);
