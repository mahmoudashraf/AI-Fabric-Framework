'use client';

import { Button, Grid, Typography, useMediaQuery  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import Link from 'next/link';

// material-ui

// project imports
import AuthCardWrapper from 'components/authentication/AuthCardWrapper';
import AuthWrapper1 from 'components/authentication/AuthWrapper1';
import AuthFooter from 'ui-component/cards/AuthFooter';
import AnimateButton from 'ui-component/extended/AnimateButton';
import Logo from 'ui-component/Logo';

// ==============================|| AUTH3 - CHECK MAIL ||============================== //

const CheckMail = () => {
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
                          Hi, Check Your Mail
                        </Typography>
                      </Grid>
                      <Grid size={{ xs: 12 }}>
                        <Typography
                          variant="caption"
                          fontSize="16px"
                          textAlign={matchDownSM ? 'center' : 'inherit'}
                        >
                          We have sent a password recover instructions to your email.
                        </Typography>
                      </Grid>
                    </Grid>
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <AnimateButton>
                      <Button
                        disableElevation
                        fullWidth
                        size="large"
                        type="submit"
                        variant="contained"
                        color="secondary"
                      >
                        Open Mail
                      </Button>
                    </AnimateButton>
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

export default CheckMail;
