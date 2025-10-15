'use client';

import { Button, Grid, Stack, Typography, useMediaQuery  } from '@mui/material';
import { useTheme, styled } from '@mui/material/styles';
import Link from 'next/link';

// material-ui

// project imports
import AuthCardWrapper from 'components/authentication/AuthCardWrapper';
import AuthWrapper1 from 'components/authentication/AuthWrapper1';
import { AuthSliderProps } from 'types';
import AuthSlider from 'ui-component/cards/AuthSlider';
import BackgroundPattern1 from 'ui-component/cards/BackgroundPattern1';
import AnimateButton from 'ui-component/extended/AnimateButton';
import Logo from 'ui-component/Logo';

// types

// assets
const AuthBlueCard = '/assets/images/auth/auth-mail-blue-card.svg';

// styles
const BlueWrapper = styled('span')(({ theme }) => ({
  '&:before': {
    content: '""',
    position: 'absolute',
    top: '25%',
    left: '18%',
    width: 455,
    height: 430,
    backgroundImage: `url(${AuthBlueCard})`,
    backgroundRepeat: 'no-repeat',
    backgroundPosition: 'center',
    animation: '15s wings ease-in-out infinite',
    animationDelay: '1s',
    [theme.breakpoints.down('xl')]: {
      top: '20%',
      left: '6%',
      backgroundSize: 450,
    },
  },
}));

// carousel items
const items: AuthSliderProps[] = [
  {
    title: 'Powerful and easy to use multipurpose theme.',
    description: 'Powerful and easy to use multipurpose theme',
  },
  {
    title: 'Power of React with Material UI',
    description: 'Powerful and easy to use multipurpose theme',
  },
  {
    title: 'Power of React with Material UI',
    description: 'Powerful and easy to use multipurpose theme',
  },
];

// ==============================|| AUTH1 - CHECK MAIL ||============================== //

const CheckMail = () => {
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
                        Check Mail
                      </Typography>
                      <Typography color="textPrimary" gutterBottom variant="h4">
                        Avoid getting locked out.
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
                <Stack direction="row" justifyContent={matchDownSM ? 'center' : 'flex-start'}>
                  <Typography
                    variant="caption"
                    fontSize="16px"
                    textAlign={matchDownSM ? 'center' : 'inherit'}
                  >
                    We have sent a password recover instructions to your email.
                  </Typography>
                </Stack>
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
        <Grid
          sx={{ position: 'relative', alignSelf: 'stretch', display: { xs: 'none', md: 'block' } }}
        >
          <BackgroundPattern1>
            <Grid container alignItems="flex-end" justifyContent="center" spacing={3}>
              <Grid size={{ xs: 12 }}>
                <span />
                <BlueWrapper />
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

export default CheckMail;
