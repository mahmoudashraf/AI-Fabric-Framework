import CloseIcon from '@mui/icons-material/Close';
import { Alert, Button, Fade, Grow, IconButton, Slide, SlideProps } from '@mui/material';
import MuiSnackbar from '@mui/material/Snackbar';
import { SyntheticEvent } from 'react';

// material-ui

// project import
import { useNotifications } from 'contexts/NotificationContext';

// types
import { KeyedObject } from 'types';

// assets

// animation function
function TransitionSlideLeft(props: SlideProps) {
  return <Slide {...props} direction="left" />;
}

function TransitionSlideUp(props: SlideProps) {
  return <Slide {...props} direction="up" />;
}

function TransitionSlideRight(props: SlideProps) {
  return <Slide {...props} direction="right" />;
}

function TransitionSlideDown(props: SlideProps) {
  return <Slide {...props} direction="down" />;
}

function GrowTransition(props: SlideProps) {
  return <Grow appear={props.appear || true} timeout={props.timeout}>{props.children}</Grow>;
}

// animation options
const animation: KeyedObject = {
  SlideLeft: TransitionSlideLeft,
  SlideUp: TransitionSlideUp,
  SlideRight: TransitionSlideRight,
  SlideDown: TransitionSlideDown,
  Grow: GrowTransition,
  Fade,
};

// ==============================|| SNACKBAR ||============================== //

const Snackbar = () => {
  const { state, removeNotification } = useNotifications();

  const first = state.notifications[0];
  if (!first) return null;

  const handleClose = (_event: SyntheticEvent | Event, reason?: string) => {
    if (reason === 'clickaway') {
      return;
    }
    removeNotification(first.id);
  };

  const transitionKey = first.transition ?? 'Fade';
  const TransitionComponent = animation[transitionKey];

  if (first.variant === 'alert') {
    return (
      <MuiSnackbar
        TransitionComponent={TransitionComponent}
        anchorOrigin={first.anchorOrigin ?? { vertical: 'bottom', horizontal: 'right' }}
        open={true}
        autoHideDuration={1500}
        onClose={handleClose}
      >
        <Alert
          variant={(first.alert?.variant ?? 'filled') as 'filled' | 'standard' | 'outlined'}
          color={first.alert?.color ?? 'primary'}
          action={
            <>
              {first.actionButton && (
                <Button size="small" onClick={handleClose} sx={{ color: 'background.paper' }}>
                  UNDO
                </Button>
              )}
              {first.close !== false && (
                <IconButton
                  sx={{ color: 'background.paper' }}
                  size="small"
                  aria-label="close"
                  onClick={handleClose}
                >
                  <CloseIcon fontSize="small" />
                </IconButton>
              )}
            </>
          }
          sx={{
            ...((first.alert?.variant ?? 'filled') === 'outlined' && {
              bgcolor: 'background.paper',
            }),
          }}
        >
          {first.message}
        </Alert>
      </MuiSnackbar>
    );
  }

  return (
    <MuiSnackbar
      anchorOrigin={first.anchorOrigin ?? { vertical: 'bottom', horizontal: 'right' }}
      open={true}
      autoHideDuration={1500}
      onClose={handleClose}
      message={first.message}
      TransitionComponent={TransitionComponent}
      action={
        <>
          {first.actionButton && (
            <Button color="secondary" size="small" onClick={handleClose}>
              UNDO
            </Button>
          )}
          {first.close !== false && (
            <IconButton
              size="small"
              aria-label="close"
              color="inherit"
              onClick={handleClose}
              sx={{ mt: 0.25 }}
            >
              <CloseIcon fontSize="small" />
            </IconButton>
          )}
        </>
      }
    />
  );
};

export default Snackbar;
