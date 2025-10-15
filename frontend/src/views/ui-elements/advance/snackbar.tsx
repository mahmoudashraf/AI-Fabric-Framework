'use client';

// material-ui
import { Button, Grid, Typography  } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project imports
import { useNotifications } from 'contexts/NotificationContext';

const gridSpacing = 3;

// Pure Context API usage
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';
import {
  ColorVariants,
  CustomComponent,
  Dense,
  DismissSnackBar,
  HideDuration,
  IconVariants,
  MaxSnackbar,
  PositioningSnackbar,
  PreventDuplicate,
  SnackBarAction,
  TransitionBar,
} from 'ui-component/extended/notistack';

// ==============================|| UI SNACKBAR ||============================== //

const UISnackbar = () => {
  const theme = useTheme();
  
  const notificationContext = useNotifications();
  
  // Use Context API directly

  return (
    <MainCard>
      <MainCard.Header title="Snackbar" action={<SecondaryAction link="https://next.material-ui.com/components/snackbars/" />} />
      <Grid container spacing={gridSpacing}>
        <Grid size={{ xs: 12, sm: 6, md: 4 }}>
          <SubCard title="Basic Snackbar">
            <Grid container spacing={3} justifyContent="center" alignItems="center">
              <Grid>
                <Button
                  variant="outlined"
                  onClick={() => {
                    notificationContext.showNotification({
                      message: 'This is default message',
                      variant: 'success',
                      alert: { color: 'success', variant: 'filled' },
                      close: false,
                    });
                  }}
                >
                  Default
                </Button>
              </Grid>
              <Grid>
                <Button
                  sx={{
                    color: theme.palette.error.dark,
                    borderColor: theme.palette.error.dark,
                    '&:hover': {
                      background: theme.palette.error.main + 20,
                      borderColor: theme.palette.error.dark,
                    },
                  }}
                  variant="outlined"
                  onClick={() =>
                    notificationContext.showNotification({
                      message: 'This is error message',
                      variant: 'error',
                      alert: {
                        color: 'error',
                        variant: 'filled',
                      },
                      close: false,
                    })
                  }
                >
                  Error
                </Button>
              </Grid>
              <Grid>
                <Button
                  variant="outlined"
                  sx={{
                    color: theme.palette.success.dark,
                    borderColor: theme.palette.success.dark,
                    '&:hover': {
                      background: theme.palette.success.main + 20,
                      borderColor: theme.palette.success.dark,
                    },
                  }}
                  onClick={() =>
                    notificationContext.showNotification({
                        message: 'This is success message',
                        variant: 'success',
                        alert: {
                          color: 'success',
                          variant: 'filled',
                        },
                        close: false,
                      })
                  }
                >
                  Success
                </Button>
              </Grid>
              <Grid>
                <Button
                  variant="outlined"
                  sx={{
                    color: theme.palette.warning.dark,
                    borderColor: theme.palette.warning.dark,
                    '&:hover': {
                      background: theme.palette.warning.main + 20,
                      borderColor: theme.palette.warning.dark,
                    },
                  }}
                  onClick={() =>
                    notificationContext.showNotification({
                        message: 'This is warning message',
                        variant: 'warning',
                        alert: {
                          color: 'warning',
                          variant: 'filled',
                        },
                        close: false,
                      })
                  }
                >
                  Warning
                </Button>
              </Grid>
            </Grid>
          </SubCard>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 4 }}>
          <SubCard title="With Close">
            <Grid container spacing={3} justifyContent="center" alignItems="center">
              <Grid>
                <Button
                  variant="outlined"
                  onClick={() =>
                    notificationContext.showNotification({
                        message: 'This is default message',
                        variant: 'success',
                        alert: { color: 'success', variant: 'filled' },
                        close: false,
                      })
                  }
                >
                  Default
                </Button>
              </Grid>
              <Grid>
                <Button
                  sx={{
                    color: theme.palette.error.dark,
                    borderColor: theme.palette.error.dark,
                    '&:hover': {
                      background: theme.palette.error.main + 20,
                      borderColor: theme.palette.error.dark,
                    },
                  }}
                  variant="outlined"
                  onClick={() =>
                    notificationContext.showNotification({
                        message: 'This is error message',
                        variant: 'success',
                        alert: {
                          color: 'error',
                          variant: 'filled',
                        },
                      })
                  }
                >
                  Error
                </Button>
              </Grid>
              <Grid>
                <Button
                  variant="outlined"
                  sx={{
                    color: theme.palette.success.dark,
                    borderColor: theme.palette.success.dark,
                    '&:hover': {
                      background: theme.palette.success.main + 20,
                      borderColor: theme.palette.success.dark,
                    },
                  }}
                  onClick={() =>
                    notificationContext.showNotification({
                        message: 'This is success message',
                        variant: 'success',
                        alert: {
                          color: 'success',
                          variant: 'filled',
                        },
                      })
                  }
                >
                  Success
                </Button>
              </Grid>
              <Grid>
                <Button
                  variant="outlined"
                  sx={{
                    color: theme.palette.warning.dark,
                    borderColor: theme.palette.warning.dark,
                    '&:hover': {
                      background: theme.palette.warning.main + 20,
                      borderColor: theme.palette.warning.dark,
                    },
                  }}
                  onClick={() =>
                    notificationContext.showNotification({
                        message: 'This is warning message',
                        variant: 'success',
                        alert: {
                          color: 'warning',
                          variant: 'filled',
                        },
                      })
                  }
                >
                  Warning
                </Button>
              </Grid>
            </Grid>
          </SubCard>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 4 }}>
          <SubCard title="With Close + Action">
            <Grid container spacing={3} justifyContent="center" alignItems="center">
              <Grid>
                <Button
                  variant="outlined"
                  onClick={() =>
                    notificationContext.showNotification({
                        message: 'This is default message',
                        variant: 'success',
                        actionButton: true,
                      })
                  }
                >
                  Default
                </Button>
              </Grid>
              <Grid>
                <Button
                  sx={{
                    color: theme.palette.error.dark,
                    borderColor: theme.palette.error.dark,
                    '&:hover': {
                      background: theme.palette.error.main + 20,
                      borderColor: theme.palette.error.dark,
                    },
                  }}
                  variant="outlined"
                  onClick={() =>
                    notificationContext.showNotification({
                        message: 'This is error message',
                        variant: 'success',
                        alert: {
                          color: 'error',
                          variant: 'filled',
                        },
                        actionButton: true,
                      })
                  }
                >
                  Error
                </Button>
              </Grid>
              <Grid>
                <Button
                  variant="outlined"
                  sx={{
                    color: theme.palette.success.dark,
                    borderColor: theme.palette.success.dark,
                    '&:hover': {
                      background: theme.palette.success.main + 20,
                      borderColor: theme.palette.success.dark,
                    },
                  }}
                  onClick={() =>
                    notificationContext.showNotification({
                        message: 'This is success message',
                        variant: 'success',
                        alert: {
                          color: 'success',
                          variant: 'filled',
                        },
                        actionButton: true,
                      })
                  }
                >
                  Success
                </Button>
              </Grid>
              <Grid>
                <Button
                  variant="outlined"
                  sx={{
                    color: theme.palette.warning.dark,
                    borderColor: theme.palette.warning.dark,
                    '&:hover': {
                      background: theme.palette.warning.main + 20,
                      borderColor: theme.palette.warning.dark,
                    },
                  }}
                  onClick={() =>
                    notificationContext.showNotification({
                        message: 'This is warning message',
                        variant: 'success',
                        alert: {
                          color: 'warning',
                          variant: 'filled',
                        },
                        actionButton: true,
                      })
                  }
                >
                  Warning
                </Button>
              </Grid>
            </Grid>
          </SubCard>
        </Grid>

        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Snackbar Position">
            <Grid container spacing={2} justifyContent="center">
              <Grid>
                <Button
                  variant="contained"
                  onClick={() =>
                    notificationContext.showNotification({
                        anchorOrigin: { vertical: 'top', horizontal: 'left' },
                        message: 'This is an top-left message!',
                      })
                  }
                >
                  Top-Left
                </Button>
              </Grid>
              <Grid>
                <Button
                  variant="contained"
                  onClick={() =>
                    notificationContext.showNotification({
                        anchorOrigin: { vertical: 'top', horizontal: 'center' },
                        message: 'This is an top-center message!',
                      })
                  }
                >
                  Top-Center
                </Button>
              </Grid>
              <Grid>
                <Button
                  variant="contained"
                  onClick={() =>
                    notificationContext.showNotification({
                        anchorOrigin: { vertical: 'top', horizontal: 'right' },
                        message: 'This is an top-right message!',
                      })
                  }
                >
                  Top-Right
                </Button>
              </Grid>
              <Grid>
                <Button
                  variant="contained"
                  onClick={() =>
                    notificationContext.showNotification({
                        anchorOrigin: { vertical: 'bottom', horizontal: 'right' },
                        message: 'This is an bottom-right message!',
                      })
                  }
                >
                  Bottom-Right
                </Button>
              </Grid>
              <Grid>
                <Button
                  variant="contained"
                  onClick={() =>
                    notificationContext.showNotification({
                        anchorOrigin: { vertical: 'bottom', horizontal: 'center' },
                        message: 'This is an bottom-center message!',
                      })
                  }
                >
                  Bottom-Center
                </Button>
              </Grid>
              <Grid>
                <Button
                  variant="contained"
                  onClick={() =>
                    notificationContext.showNotification({
                        anchorOrigin: { vertical: 'bottom', horizontal: 'left' },
                        message: 'This is an bottom-left message!',
                      })
                  }
                >
                  Bottom-Left
                </Button>
              </Grid>
            </Grid>
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Snackbar Trasition">
            <Grid container spacing={2} justifyContent="center">
              <Grid>
                <Button
                  variant="contained"
                  onClick={() =>
                    notificationContext.showNotification({
                        message: 'This is an fade message!',
                        transition: 'Fade',
                      })
                  }
                >
                  Default/Fade
                </Button>
              </Grid>
              <Grid>
                <Button
                  variant="contained"
                  onClick={() =>
                    notificationContext.showNotification({
                        message: 'This is an slide-left message!',
                        transition: 'Slide',
                      })
                  }
                >
                  Slide Left
                </Button>
              </Grid>
              <Grid>
                <Button
                  variant="contained"
                  onClick={() =>
                    notificationContext.showNotification({
                        message: 'This is an slide-up message!',
                        transition: 'Slide',
                      })
                  }
                >
                  Slide Up
                </Button>
              </Grid>
              <Grid>
                <Button
                  variant="contained"
                  onClick={() =>
                    notificationContext.showNotification({
                        message: 'This is an slide-right message!',
                        transition: 'Slide',
                      })
                  }
                >
                  Slide Right
                </Button>
              </Grid>
              <Grid>
                <Button
                  variant="contained"
                  onClick={() =>
                    notificationContext.showNotification({
                        message: 'This is an slide-down message!',
                        transition: 'Slide',
                      })
                  }
                >
                  Slide Down
                </Button>
              </Grid>
              <Grid>
                <Button
                  variant="contained"
                  onClick={() =>
                    notificationContext.showNotification({
                        message: 'This is an grow message!',
                        transition: 'Grow',
                      })
                  }
                >
                  Grow
                </Button>
              </Grid>
            </Grid>
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12 }}>
          <Typography variant="h5" sx={{ mt: 2 }}>
            Extended - Notistack
          </Typography>
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <ColorVariants />
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <MaxSnackbar />
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <IconVariants />
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <HideDuration />
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <SnackBarAction />
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <DismissSnackBar />
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <PreventDuplicate />
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <TransitionBar />
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <Dense />
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <CustomComponent />
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <PositioningSnackbar />
        </Grid>
      </Grid>
    </MainCard>
  );
};

export default UISnackbar;
