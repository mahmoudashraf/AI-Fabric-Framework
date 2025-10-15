'use client';

import { Button,
  Box,
  Card,
  CardContent,
  Container,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  OutlinedInput,
  TextField,
  Typography,
 } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import Image from 'next/image';
import Link from 'next/link';
import React from 'react';

// material-ui

// third-party
import { PatternFormat } from 'react-number-format';

// project imports
import { gridSpacing } from 'constants/index';
import AnimateButton from 'ui-component/extended/AnimateButton';

// assets
const mailImg = '/assets/images/landing/widget-mail.svg';

// select options
const currencies = [
  {
    value: '1',
    label: 'Below $1000',
  },
  {
    value: '2',
    label: '$1000 - $5000',
  },
  {
    value: '3',
    label: 'Not specified',
  },
];

const sizes = [
  {
    value: '1',
    label: '1 - 5',
  },
  {
    value: '2',
    label: '5 - 10',
  },
  {
    value: '3',
    label: '10+',
  },
];

// ===========================|| CONTACT CARD - FORMS ||=========================== //

const ContactCard = () => {
  const theme = useTheme();

  const [budget, setBudget] = React.useState(1);
  const handleChange = (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    setBudget(Number(event.target?.value!));
  };

  const [size, setSize] = React.useState(1);
  const handleChange1 = (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    setSize(Number(event.target?.value!));
  };

  return (
    <Container>
      <Grid container justifyContent="center" spacing={gridSpacing}>
        <Grid sx={{ mt: { md: 12.5, xs: 2.5 }, mb: { md: 12.5, xs: 2.5 } }}>
          <Grid container spacing={gridSpacing}>
            <Grid size={{ xs: 12 }}>
              <Typography
                variant="h1"
                color="white"
                component="div"
                sx={{
                  fontSize: '3.5rem',
                  fontWeight: 900,
                  lineHeight: 1.4,
                  [theme.breakpoints.down('md')]: { fontSize: '1.8125rem', marginTop: '80px' },
                }}
              >
                Talk to our account expert
              </Typography>
            </Grid>
            <Grid size={{ xs: 12 }}>
              <Typography
                variant="h4"
                component="div"
                sx={{
                  fontWeight: 400,
                  lineHeight: 1.4,
                  [theme.breakpoints.up('md')]: { my: 0, mx: 12.5 },
                }}
                color="white"
              >
                The starting point for your next project based on easy-to-customize Material-UI ©
                helps you build apps faster and better.
              </Typography>
            </Grid>
          </Grid>
        </Grid>
        <Grid size={{ xs: 12 }} sx={{ position: 'relative', display: { xs: 'none', lg: 'block' } }}>
          <Box
            sx={{
              marginBottom: -0.625,
              position: 'absolute',
              bottom: -90,
              right: '0',
              width: 400,
              maxWidth: '100%',
              animation: '5s wings ease-in-out infinite',
            }}
          >
            <Image
              src={mailImg}
              alt="Kiwi Dashboard"
              width={400}
              height={270}
              style={{
                maxWidth: '100%',
                height: 'auto',
              }}
            />
          </Box>
        </Grid>
        <Grid size={10} sx={{ mb: -37.5 }}>
          <Card sx={{ mb: 6.25 }} elevation={4}>
            <CardContent sx={{ p: 4 }}>
              <Grid container spacing={gridSpacing}>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <FormControl fullWidth>
                    <InputLabel>Name</InputLabel>
                    <OutlinedInput type="text" label="Name" placeholder="Enter Your Name" />
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <FormControl fullWidth>
                    <InputLabel>Company Name</InputLabel>
                    <OutlinedInput
                      type="text"
                      label="Company Name"
                      placeholder="Enter Your Company Name"
                    />
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <FormControl fullWidth>
                    <InputLabel>Email Address</InputLabel>
                    <OutlinedInput
                      type="text"
                      label="Email Address"
                      placeholder="Enter Your Email Address"
                    />
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <FormControl fullWidth>
                    <PatternFormat
                      format="+1 (###) ###-####"
                      mask="_"
                      fullWidth
                      customInput={TextField}
                      label="Phone Number"
                    />
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <FormControl fullWidth sx={{ textAlign: 'left' }}>
                    <TextField
                      id="outlined-select-Size"
                      select
                      fullWidth
                      label="Company Size"
                      value={size}
                      onChange={handleChange1}
                    >
                      {sizes.map((option, index) => (
                        <MenuItem key={index} value={option.value}>
                          {option.label}
                        </MenuItem>
                      ))}
                    </TextField>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <FormControl fullWidth sx={{ textAlign: 'left' }}>
                    <TextField
                      id="outlined-select-budget"
                      select
                      fullWidth
                      label="Project Budget"
                      value={budget}
                      onChange={handleChange}
                    >
                      {currencies.map((option, index) => (
                        <MenuItem key={index} value={option.value}>
                          {option.label}
                        </MenuItem>
                      ))}
                    </TextField>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <FormControl fullWidth>
                    <TextField
                      id="outlined-multiline-static1"
                      placeholder="Message"
                      multiline
                      fullWidth
                      rows={4}
                      defaultValue=""
                    />
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <Grid container spacing={gridSpacing}>
                    <Grid size={{ xs: 12, sm: 9 }} sx={{ minWidth: 0 }}>
                      <Typography align="left" variant="body2">
                        By submitting this, you agree to the
                        <Typography
                          variant="subtitle1"
                          component={Link}
                          href="#"
                          color="primary"
                          sx={{ mx: 0.5 }}
                        >
                          Privacy Policy
                        </Typography>
                        and
                        <Typography
                          variant="subtitle1"
                          component={Link}
                          href="#"
                          color="primary"
                          sx={{ ml: 0.5 }}
                        >
                          Cookie Policy
                        </Typography>
                      </Typography>
                    </Grid>
                    <Grid size={{ xs: 12, sm: 3 }}>
                      <AnimateButton>
                        <Button variant="contained" color="secondary">
                          Get Started
                        </Button>
                      </AnimateButton>
                    </Grid>
                  </Grid>
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Container>
  );
};

export default ContactCard;
