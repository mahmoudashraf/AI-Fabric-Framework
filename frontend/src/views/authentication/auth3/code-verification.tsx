'use client';

// material-ui
import { Button, Divider, Grid, Stack, Typography, useMediaQuery  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import Link from 'next/link';

// project imports
import AuthCodeVerification from '@/components/authentication/auth-forms/AuthCodeVerification';
import AuthCardWrapper from 'components/authentication/AuthCardWrapper';
import AuthWrapper1 from 'components/authentication/AuthWrapper1';
import AuthFooter from 'ui-component/cards/AuthFooter';
import AnimateButton from 'ui-component/extended/AnimateButton';
import Logo from 'ui-component/Logo';

// ===========================|| AUTH3 - CODE VERIFICATION ||=========================== //

const CodeVerification = () => {
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
                            Enter Verification Code
                          </Typography>
                          <Typography variant="subtitle1" fontSize="1rem">
                            We send you on mail.
                          </Typography>
                          <Typography
                            variant="caption"
                            fontSize="0.875rem"
                            textAlign={matchDownSM ? 'center' : 'inherit'}
                          >
                            We’ve send you code on jone.****@company.com
                          </Typography>
                        </Stack>
                      </Grid>
                    </Grid>
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <AuthCodeVerification />
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <Divider />
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <Grid container direction="column" alignItems="center" size={{ xs: 12 }}>
                      <Typography
                        component={Link}
                        href="#"
                        variant="subtitle1"
                        sx={{ textDecoration: 'none' }}
                        textAlign={matchDownSM ? 'center' : 'inherit'}
                      >
                        Did not receive the email? Check your spam filter, or
                      </Typography>
                    </Grid>
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <AnimateButton>
                      <Button
                        disableElevation
                        fullWidth
                        size="large"
                        type="submit"
                        variant="outlined"
                        color="secondary"
                      >
                        Resend Code
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

export default CodeVerification;
