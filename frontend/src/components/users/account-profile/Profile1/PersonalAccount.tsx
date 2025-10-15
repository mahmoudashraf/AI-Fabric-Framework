'use client';
import FacebookIcon from '@mui/icons-material/Facebook';
import LinkedInIcon from '@mui/icons-material/LinkedIn';
import TwitterIcon from '@mui/icons-material/Twitter';
import { Button, Grid, MenuItem, TextField  } from '@mui/material';
import { useState } from 'react';

// material-ui

// project imports
import useAuth from 'hooks/useAuth';
import { gridSpacing } from 'constants/index';
import SubCard from 'ui-component/cards/SubCard';
import AnimateButton from 'ui-component/extended/AnimateButton';

// assets

// select options
const currencies = [
  {
    value: 'Washington',
    label: 'Washington',
  },
  {
    value: 'India',
    label: 'India',
  },
  {
    value: 'Africa',
    label: 'Africa',
  },
  {
    value: 'New-York',
    label: 'New York',
  },
  {
    value: 'Malaysia',
    label: 'Malaysia',
  },
];

const experiences = [
  {
    value: 'Startup',
    label: 'Startup',
  },
  {
    value: '2-year',
    label: '2 year',
  },
  {
    value: '3-year',
    label: '3 year',
  },
  {
    value: '4-year',
    label: '4 year',
  },
  {
    value: '5-year',
    label: '5 year',
  },
];

// ==============================|| PROFILE 1 - PROFILE ACCOUNT ||============================== //

const PersonalAccount = () => {
  const { user } = useAuth();

  const [currency, setCurrency] = useState('Washington');
  const handleChange1 = (event: React.ChangeEvent<HTMLInputElement>) => {
    setCurrency(event.target.value);
  };

  const [experience, setExperience] = useState('Startup');
  const handleChange2 = (event: React.ChangeEvent<HTMLInputElement>) => {
    setExperience(event.target.value);
  };

  return (
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Personal Information">
          <form noValidate autoComplete="off">
            <Grid container spacing={gridSpacing}>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField id="outlined-basic1" fullWidth label="Name" defaultValue={user?.firstName ? `${user.firstName} ${user.lastName}` : user?.email} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  id="outlined-select-currency"
                  select
                  fullWidth
                  label="Location"
                  value={currency}
                  onChange={handleChange1}
                >
                  {currencies.map(option => (
                    <MenuItem key={option.value} value={option.value}>
                      {option.label}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid size={{ xs: 12 }}>
                <TextField
                  id="outlined-multiline-static1"
                  label="Bio"
                  multiline
                  fullWidth
                  rows={3}
                  defaultValue="I consider myself as a creative, professional and a flexible person. I can adapt with any kind of brief and design style"
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  id="outlined-select-experience"
                  select
                  fullWidth
                  label="Experience"
                  value={experience}
                  onChange={handleChange2}
                >
                  {experiences.map(option => (
                    <MenuItem key={option.value} value={option.value}>
                      {option.label}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
            </Grid>
          </form>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Contact Information">
          <form noValidate autoComplete="off">
            <Grid container spacing={gridSpacing}>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  id="outlined-basic2"
                  fullWidth
                  label="Contact Phone"
                  defaultValue="(+99) 9999 999 999"
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  id="outlined-basic3"
                  fullWidth
                  label="Email"
                  defaultValue="demo@sample.com"
                />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <TextField
                  id="outlined-basic4"
                  fullWidth
                  label="Portfolio Url"
                  defaultValue="https://demo.com"
                />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <TextField
                  id="outlined-multiline-static2"
                  label="Address"
                  multiline
                  fullWidth
                  rows={3}
                  defaultValue="3379  Monroe Avenue, Fort Myers, Florida(33912)"
                />
              </Grid>
            </Grid>
          </form>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <SubCard title="Social Information">
          <form noValidate autoComplete="off">
            <Grid container alignItems="center" spacing={gridSpacing} sx={{ mb: 1.25 }}>
              <Grid>
                <FacebookIcon />
              </Grid>
              <Grid size="grow" sx={{ minWidth: 0 }}>
                <TextField fullWidth label="Facebook Profile Url" />
              </Grid>
              <Grid>
                <AnimateButton>
                  <Button variant="contained" size="small" color="secondary">
                    Connect
                  </Button>
                </AnimateButton>
              </Grid>
            </Grid>
            <Grid container alignItems="center" spacing={gridSpacing} sx={{ mb: 1.25 }}>
              <Grid>
                <TwitterIcon />
              </Grid>
              <Grid size="grow" sx={{ minWidth: 0 }}>
                <TextField fullWidth label="Twitter Profile Url" />
              </Grid>
              <Grid>
                <AnimateButton>
                  <Button variant="contained" size="small" color="secondary">
                    Connect
                  </Button>
                </AnimateButton>
              </Grid>
            </Grid>
            <Grid container alignItems="center" spacing={gridSpacing}>
              <Grid>
                <LinkedInIcon />
              </Grid>
              <Grid size="grow" sx={{ minWidth: 0 }}>
                <TextField fullWidth label="LinkedIn Profile Url" />
              </Grid>
              <Grid>
                <AnimateButton>
                  <Button variant="contained" size="small" color="secondary">
                    Connect
                  </Button>
                </AnimateButton>
              </Grid>
            </Grid>
          </form>
        </SubCard>
      </Grid>
    </Grid>
  );
};

export default PersonalAccount;
