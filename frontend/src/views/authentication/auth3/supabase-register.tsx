'use client';

import { Divider, Grid, Stack, Typography, useMediaQuery } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import Link from 'next/link';

import AuthSupabaseRegister from '@/components/authentication/auth-forms/AuthSupabaseRegister';
import AuthCardWrapper from '@/components/authentication/AuthCardWrapper';
import AuthWrapper1 from '@/components/authentication/AuthWrapper1';
import AuthFooter from '@/components/ui-component/cards/AuthFooter';
import Logo from '@/components/ui-component/Logo';

const SupabaseRegister = () => {
  const theme = useTheme();
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
                    <Link href="/" aria-label="theme-logo">
                      <Logo />
                    </Link>
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <Grid
                      container
                      direction={matchDownSM ? 'column-reverse' : 'row'}
                      alignItems="center"
                      justifyContent="center"
                    >
                      <Grid>
                        <Stack alignItems="center" justifyContent="center" spacing={1}>
                          <Typography
                            color={theme.palette.secondary.main}
                            gutterBottom
                            variant={matchDownSM ? 'h3' : 'h2'}
                          >
                            Join EasyLuxury
                          </Typography>
                          <Typography
                            variant="caption"
                            fontSize="16px"
                            textAlign={matchDownSM ? 'center' : 'inherit'}
                          >
                            Create your account to get started
                          </Typography>
                        </Stack>
                      </Grid>
                    </Grid>
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <AuthSupabaseRegister />
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <Divider />
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <Grid container direction="column" alignItems="center" size={{ xs: 12 }}>
                      <Typography
                        component={Link}
                        href="/login"
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

export default SupabaseRegister;
