'use client';
import { Box, Divider, Grid, Stack, Typography, useMediaQuery  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import Image from 'next/image';
import Link from 'next/link';

// material-ui

// project imports
import AuthSupabaseLogin from '@/components/authentication/auth-forms/AuthSupabaseLogin';
import AuthCardWrapper from 'components/authentication/AuthCardWrapper';
import AuthWrapper2 from 'components/authentication/AuthWrapper2';
import { AuthSliderProps } from 'types';
import AuthFooter from 'ui-component/cards/AuthFooter';
import AuthSlider from 'ui-component/cards/AuthSlider';
import BackgroundPattern2 from 'ui-component/cards/BackgroundPattern2';
import Logo from 'ui-component/Logo';

// types

// assets
const imgMain = '/assets/images/auth/img-a2-login.svg';

// carousel items
const items: AuthSliderProps[] = [
  {
    title: 'Components Based Design System',
    description: 'Powerful and easy to use multipurpose theme',
  },
  {
    title: 'Ready to use components',
    description: 'Ready made component to apply directly',
  },
  {
    title: 'Multiple dashboard and widgets',
    description: '100+ widgets and customize controls',
  },
];

// ================================|| AUTH2 - LOGIN ||================================ //

const Login = () => {
  const theme = useTheme();
  const matchDownSM = useMediaQuery(theme.breakpoints.down('md'));
  const matchDownMD = useMediaQuery(theme.breakpoints.down('lg'));

  return (
    <AuthWrapper2>
      <Grid container justifyContent={matchDownSM ? 'center' : 'space-between'} alignItems="center">
        <Grid size={{ xs: 12 }} sx={{ minHeight: '100vh' }}>
          <Grid
            sx={{ minHeight: '100vh' }}
            container
            alignItems={matchDownSM ? 'center' : 'flex-start'}
            justifyContent={matchDownSM ? 'center' : 'space-between'}
          >
            <Grid sx={{ display: { xs: 'none', md: 'block' }, m: 3 }}>
              <Link href="#" aria-label="theme-logo">
                <Logo />
              </Link>
            </Grid>
            <Grid size={{ xs: 12 }}
              container
              justifyContent="center"
              alignItems="center"
              sx={{ minHeight: { xs: 'calc(100vh - 68px)', md: 'calc(100vh - 152px)' } }}
            >
              <Stack justifyContent="center" alignItems="center" spacing={5} m={2}>
                <Box component={Link} href="#" sx={{ display: { xs: 'block', md: 'none' } }}>
                  <Logo />
                </Box>
                <AuthCardWrapper border={matchDownMD}>
                  <Grid container spacing={2} justifyContent="center">
                    <Grid>
                      <Stack alignItems="center" justifyContent="center" spacing={1}>
                        <Typography
                          color={theme.palette.secondary.main}
                          gutterBottom
                          variant={matchDownSM ? 'h3' : 'h2'}
                        >
                          Hi, Welcome Back
                        </Typography>
                        <Typography
                          variant="caption"
                          fontSize="16px"
                          textAlign={matchDownSM ? 'center' : 'inherit'}
                        >
                          Enter your credentials to continue
                        </Typography>
                      </Stack>
                    </Grid>
                    <Grid size={{ xs: 12 }}>
                      <AuthSupabaseLogin />
                    </Grid>
                    <Grid size={{ xs: 12 }}>
                      <Divider />
                    </Grid>
                    <Grid size={{ xs: 12 }}>
                      <Grid container direction="column" alignItems="center" size={{ xs: 12 }}>
                        <Typography
                          component={Link}
                          href="/pages/authentication/auth2/register"
                          variant="subtitle1"
                          sx={{ textDecoration: 'none' }}
                        >
                          Don&apos;t have an account?
                        </Typography>
                      </Grid>
                    </Grid>
                  </Grid>
                </AuthCardWrapper>
              </Stack>
            </Grid>
            <Grid size={{ xs: 12 }} sx={{ m: 3 }}>
              <AuthFooter />
            </Grid>
          </Grid>
        </Grid>
        <Grid
          sx={{ position: 'relative', alignSelf: 'stretch', display: { xs: 'none', md: 'block' } }}
        >
          <BackgroundPattern2>
            <Grid container justifyContent="center">
              <Grid size={{ xs: 12 }}>
                <Grid container justifyContent="center" sx={{ pb: 8 }}>
                  <Grid size={{ xs: 10, lg: 8 }} sx={{ '& .slick-list': { pb: 2 } }}>
                    <AuthSlider items={items} />
                  </Grid>
                </Grid>
              </Grid>
              <Grid size={{ xs: 12 }} sx={{ position: 'relative' }}>
                <div
                  style={{
                    maxWidth: '100%',
                    margin: '0 auto',
                    display: 'block',
                    position: 'relative',
                    zIndex: 5,
                    textAlign: 'center',
                  }}
                >
                  <Image
                    alt="Auth method"
                    src={imgMain}
                    width={300}
                    height={300}
                    style={{
                      maxWidth: '100%',
                      height: 'auto',
                    }}
                  />
                </div>
              </Grid>
            </Grid>
          </BackgroundPattern2>
        </Grid>
      </Grid>
    </AuthWrapper2>
  );
};

export default Login;
