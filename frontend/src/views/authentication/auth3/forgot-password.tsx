'use client';

import { Divider, Grid, Typography, useMediaQuery  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import Link from 'next/link';

// material-ui

// project imports
import AuthResetPassword from '@/components/authentication/auth-forms/AuthResetPassword';
import AuthCardWrapper from 'components/authentication/AuthCardWrapper';
import AuthWrapper1 from 'components/authentication/AuthWrapper1';
import useAuth from 'hooks/useAuth';
import AuthFooter from 'ui-component/cards/AuthFooter';
import Logo from 'ui-component/Logo';

// ============================|| AUTH3 - FORGOT PASSWORD ||============================ //

const ForgotPassword = () => {
  const theme = useTheme();
  const { isLoggedIn } = useAuth();
  const matchDownSM = useMediaQuery(theme.breakpoints.down('md'));

  return (
    <AuthWrapper1>
      <Grid container direction="column" justifyContent="flex-end" sx={{ minHeight: '100vh' }}>
        <Grid size={{ xs: 12 }}>
          <Grid
            container
            justifyContent="center"
            alignItems="center"
            sx={{ minHeight: 'calc(100vh - 68px)' }}
          >
            <Grid sx={{ m: { xs: 1, sm: 3 }, mb: 0 }}>
              <AuthCardWrapper>
                <Grid container spacing={2} alignItems="center" justifyContent="center">
                  <Grid sx={{ mb: 3 }}>
                    <Link href="#" aria-label="theme-logo">
                      <Logo />
                    </Link>
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <Grid
                      container
                      alignItems="center"
                      justifyContent="center"
                      textAlign="center"
                      spacing={2}
                    >
                      <Grid size={{ xs: 12 }}>
                        <Typography
                          color={theme.palette.secondary.main}
                          gutterBottom
                          variant={matchDownSM ? 'h3' : 'h2'}
                        >
                          Forgot password?
                        </Typography>
                      </Grid>
                      <Grid size={{ xs: 12 }}>
                        <Typography variant="caption" fontSize="16px" textAlign="center">
                          Enter your email address below and we&apos;ll send you password reset OTP.
                        </Typography>
                      </Grid>
                    </Grid>
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <AuthResetPassword />
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <Divider />
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <Grid container direction="column" alignItems="center" size={{ xs: 12 }}>
                      <Typography
                        component={Link}
                        href={isLoggedIn ? '/pages/authentication/auth3/login' : '/login'}
                        variant="subtitle1"
                        sx={{ textDecoration: 'none' }}
                      >
                        Already have an account?
                      </Typography>
                    </Grid>
                  </Grid>
                </Grid>
              </AuthCardWrapper>
            </Grid>
          </Grid>
        </Grid>
        <Grid size={{ xs: 12 }} sx={{ m: 3, mt: 1 }}>
          <AuthFooter />
        </Grid>
      </Grid>
    </AuthWrapper1>
  );
};

export default ForgotPassword;
