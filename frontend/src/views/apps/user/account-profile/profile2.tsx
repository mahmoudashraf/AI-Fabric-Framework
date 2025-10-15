'use client';

import CreditCardTwoToneIcon from '@mui/icons-material/CreditCardTwoTone';
import DescriptionTwoToneIcon from '@mui/icons-material/DescriptionTwoTone';
import PersonOutlineTwoToneIcon from '@mui/icons-material/PersonOutlineTwoTone';
import VpnKeyTwoToneIcon from '@mui/icons-material/VpnKeyTwoTone';
import { Button,
  CardActions,
  CardContent,
  Divider,
  Grid,
  Tab,
  Tabs,
  Typography,
 } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import React from 'react';

// material-ui

// project imports
import Billing from 'components/users/account-profile/Profile2/Billing';
import ChangePassword from 'components/users/account-profile/Profile2/ChangePassword';
import Payment from 'components/users/account-profile/Profile2/Payment';
import UserProfile from 'components/users/account-profile/Profile2/UserProfile';
import useConfig from 'hooks/useConfig';
import { gridSpacing } from 'constants/index';
import { TabsProps } from 'types';
import MainCard from 'ui-component/cards/MainCard';
import AnimateButton from 'ui-component/extended/AnimateButton';

// assets

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
      {value === index && <div>{children}</div>}
    </div>
  );
}

function a11yProps(index: number) {
  return {
    id: `simple-tab-${index}`,
    'aria-controls': `simple-tabpanel-${index}`,
  };
}

// tabs option
const tabsOption = [
  {
    label: 'User Profile',
    icon: <PersonOutlineTwoToneIcon />,
    caption: 'Profile Settings',
  },
  {
    label: 'Billing',
    icon: <DescriptionTwoToneIcon />,
    caption: 'Billing Information',
  },
  {
    label: 'Payment',
    icon: <CreditCardTwoToneIcon />,
    caption: 'Add & Update Card',
  },
  {
    label: 'Change Password',
    icon: <VpnKeyTwoToneIcon />,
    caption: 'Update Profile Security',
  },
];

// ==============================|| PROFILE 2 ||============================== //

const Profile2 = () => {
  const theme = useTheme();
  const { borderRadius } = useConfig();
  const [value, setValue] = React.useState<number>(0);

  const handleChange = (_event: React.SyntheticEvent, newValue: number) => {
    setValue(newValue);
  };

  return (
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12 }}>
        <MainCard title="Account Settings" content={false}>
          <Grid container spacing={gridSpacing}>
            <Grid size={{ xs: 12 }}>
              <CardContent>
                <Tabs
                  value={value}
                  onChange={handleChange}
                  orientation="horizontal"
                  variant="scrollable"
                  scrollButtons="auto"
                  sx={{
                    '& .MuiTabs-flexContainer': {
                      borderBottom: '1px solid',
                      borderColor: theme.palette.mode === 'dark' ? 'dark.main' : 'grey.200',
                    },
                    '& button': {
                      color: theme.palette.mode === 'dark' ? 'grey.600' : 'grey.900',
                      minHeight: 'auto',
                      minWidth: 'fit-content',
                      py: 1.5,
                      px: 2,
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      textAlign: 'center',
                      justifyContent: 'center',
                      borderRadius: `${borderRadius}px ${borderRadius}px 0 0`,
                    },
                    '& button.Mui-selected': {
                      color: theme.palette.primary.main,
                      background:
                        theme.palette.mode === 'dark'
                          ? theme.palette.dark.main
                          : theme.palette.grey[50],
                    },
                    '& button > svg': {
                      marginBottom: '8px !important',
                      marginRight: 0,
                      marginTop: 0,
                      height: 24,
                      width: 24,
                    },
                    '& button > div > span': {
                      display: 'block',
                    },
                  }}
                >
                  {tabsOption.map((tab, index) => (
                    <Tab
                      key={index}
                      icon={tab.icon}
                      label={
                        <Grid container direction="column">
                          <Typography variant="subtitle1" color="inherit">
                            {tab.label}
                          </Typography>
                          <Typography
                            component="div"
                            variant="caption"
                            sx={{ textTransform: 'capitalize' }}
                          >
                            {tab.caption}
                          </Typography>
                        </Grid>
                      }
                      {...a11yProps(index)}
                    />
                  ))}
                </Tabs>
              </CardContent>
            </Grid>
            <Grid size={{ xs: 12 }}>
              <CardContent
                sx={{
                  borderTop: '1px solid',
                  borderColor:
                    theme.palette.mode === 'dark'
                      ? theme.palette.background.default
                      : theme.palette.grey[200],
                  pt: 3,
                }}
              >
                <TabPanel value={value} index={0}>
                  <UserProfile />
                </TabPanel>
                <TabPanel value={value} index={1}>
                  <Billing />
                </TabPanel>
                <TabPanel value={value} index={2}>
                  <Payment />
                </TabPanel>
                <TabPanel value={value} index={3}>
                  <ChangePassword />
                </TabPanel>
              </CardContent>
            </Grid>
          </Grid>
          <Divider />
          <CardActions>
            <Grid container justifyContent="space-between" spacing={0}>
              <Grid>
                {value > 0 && (
                  <AnimateButton>
                    <Button
                      variant="outlined"
                      size="large"
                      onClick={e => handleChange(e, value - 1)}
                    >
                      Back
                    </Button>
                  </AnimateButton>
                )}
              </Grid>
              <Grid>
                {value < 3 && (
                  <AnimateButton>
                    <Button
                      variant="contained"
                      size="large"
                      onClick={e => handleChange(e, 1 + value)}
                    >
                      Continue
                    </Button>
                  </AnimateButton>
                )}
              </Grid>
            </Grid>
          </CardActions>
        </MainCard>
      </Grid>
    </Grid>
  );
};

// Enterprise Pattern: Apply error boundary HOC
import { withErrorBoundary } from '@/components/enterprise';
export default withErrorBoundary(Profile2);
