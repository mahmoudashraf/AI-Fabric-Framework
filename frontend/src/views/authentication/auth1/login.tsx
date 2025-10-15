'use client';

import { Divider, Grid, Stack, Typography, useMediaQuery  } from '@mui/material';
import { useTheme, styled } from '@mui/material/styles';
import Link from 'next/link';

// material-ui

// project imports
import AuthSupabaseLogin from '@/components/authentication/auth-forms/AuthSupabaseLogin';
import AuthCardWrapper from 'components/authentication/AuthCardWrapper';
import AuthWrapper1 from 'components/authentication/AuthWrapper1';
import { AuthSliderProps } from 'types';
import AuthSlider from 'ui-component/cards/AuthSlider';
import BackgroundPattern1 from 'ui-component/cards/BackgroundPattern1';
import Logo from 'ui-component/Logo';

// types

// assets
const AuthBlueCard = '/assets/images/auth/auth-blue-card.svg';
const AuthPurpleCard = '/assets/images/auth/auth-purple-card.svg';

// styles
const PurpleWrapper = styled('span')({
  '&:after': {
    content: '""',
    position: 'absolute',
    top: '32%',
    left: '40%',
    width: 313,
    backgroundSize: 380,
    height: 280,
    backgroundImage: `url(${AuthPurpleCard})`,
    backgroundRepeat: 'no-repeat',
    backgroundPosition: 'center',
    animation: '15s wings ease-in-out infinite',
  },
  '&:before': {
    content: '""',
    position: 'absolute',
    top: '23%',
    left: '37%',
    width: 243,
    height: 210,
    backgroundSize: 380,
    backgroundImage: `url(${AuthBlueCard})`,
    backgroundRepeat: 'no-repeat',
    backgroundPosition: 'center',
    animation: '15s wings ease-in-out infinite',
    animationDelay: '1s',
  },
});

// carousel items
const items: AuthSliderProps[] = [
  {
    title: 'Components Based Design System',
    description: 'Powerful and easy to use multipurpose theme',
  },
  {
    title: 'Components Based Design System',
    description: 'Powerful and easy to use multipurpose theme',
  },
  {
    title: 'Components Based Design System',
    description: 'Powerful and easy to use multipurpose theme',
  },
];

// ================================|| AUTH1 - LOGIN ||================================ //

const Login = () => {
  const theme = useTheme();
  const matchDownSM = useMediaQuery(theme.breakpoints.down('md'));

  return (
    <AuthWrapper1>
      <Grid
        container
        justifyContent="space-between"
        alignItems="center"
        sx={{ minHeight: '100vh' }}
      >
        <Grid container justifyContent="center" sx={{ my: 3 }}>
          <AuthCardWrapper>
            <Grid container spacing={2} justifyContent="center">
              <Grid size={{ xs: 12 }}>
                <Grid
                  container
                  direction={matchDownSM ? 'column-reverse' : 'row'}
                  alignItems={matchDownSM ? 'center' : 'inherit'}
                  justifyContent={matchDownSM ? 'center' : 'space-between'}
                >
                  <Grid>
                    <Stack
                      justifyContent={matchDownSM ? 'center' : 'flex-start'}
                      textAlign={matchDownSM ? 'center' : 'inherit'}
                    >
                      <Typography
                        color={theme.palette.secondary.main}
                        gutterBottom
                        variant={matchDownSM ? 'h3' : 'h2'}
                      >
                        Hi, Welcome Back
                      </Typography>
                      <Typography color="textPrimary" gutterBottom variant="h4">
                        Login in to your account
                      </Typography>
                    </Stack>
                  </Grid>
                  <Grid sx={{ mb: { xs: 3, sm: 0 } }}>
                    <Link href="#" aria-label="theme-logo">
                      <Logo />
                    </Link>
                  </Grid>
                </Grid>
              </Grid>
              <Grid size={{ xs: 12 }}>
                <AuthSupabaseLogin />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Divider />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Grid container direction="column" alignItems="flex-end" size={{ xs: 12 }}>
                  <Typography
                    component={Link}
                    href="/pages/authentication/auth1/register"
                    variant="subtitle1"
                    sx={{ textDecoration: 'none' }}
                  >
                    Don&apos;t have an account?
                  </Typography>
                </Grid>
              </Grid>
            </Grid>
          </AuthCardWrapper>
        </Grid>
        <Grid
          sx={{ position: 'relative', alignSelf: 'stretch', display: { xs: 'none', md: 'block' } }}
        >
          <BackgroundPattern1>
            <Grid container alignItems="flex-end" justifyContent="center" spacing={3}>
              <Grid size={{ xs: 12 }}>
                <span />
                <PurpleWrapper />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Grid container justifyContent="center" sx={{ pb: 8 }}>
                  <Grid size={{ xs: 10, lg: 8 }} sx={{ '& .slick-list': { pb: 2 } }}>
                    <AuthSlider items={items} />
                  </Grid>
                </Grid>
              </Grid>
            </Grid>
          </BackgroundPattern1>
        </Grid>
      </Grid>
    </AuthWrapper1>
  );
};

export default Login;
