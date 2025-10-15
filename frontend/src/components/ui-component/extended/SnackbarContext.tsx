'use client';

import { Button, IconButton, Snackbar as MuiSnackbar, Alert, Slide, SlideProps, Grow, Fade } from '@mui/material';
import { SyntheticEvent } from 'react';
import CloseIcon from '@mui/icons-material/Close';

// project imports
import { useNotifications } from 'contexts/NotificationContext';
// Using Context API

// animation components
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
const animation: Record<string, any> = {
  SlideLeft: TransitionSlideLeft,
  SlideUp: TransitionSlideUp,
  SlideRight: TransitionSlideRight,
  SlideDown: TransitionSlideDown,
  Grow: GrowTransition,
  Fade,
};

// ==============================|| CONTEXT SNACKBAR ||============================== //

const SnackbarContext = () => {
  const { removeNotification, state } = useNotifications();

  // Handle closing notification
  const handleClose = (id: string, _event: SyntheticEvent | Event, reason?: string) => {
    if (reason === 'clickaway') {
      return;
    }
    removeNotification(id);
  };

  // Render individual notification
  const renderNotification = (notification: any) => {
    const {
      id,
      message,
      variant = 'default',
      alert = { color: 'primary', variant: 'filled' },
      anchorOrigin = { vertical: 'bottom', horizontal: 'right' },
      transition = 'Fade',
      close = true,
      actionButton = false,
      maxStack: _maxStack = 3,
      dense: _dense = false,
    } = notification;

    const TransitionComponent = animation[transition];

    return (
      <div key={id}>
        {/* default snackbar */}
        {variant === 'default' && (
          <MuiSnackbar
            anchorOrigin={anchorOrigin}
            open={true}
            autoHideDuration={1500}
            onClose={(event, reason) => handleClose(id, event, reason)}
            message={message}
            TransitionComponent={TransitionComponent}
            action={
              <>
                {actionButton && (
              <Button color="secondary" size="small" onClick={(event) => handleClose(id, event)}>
                    UNDO
                  </Button>
                )}
                {close && (
                  <IconButton
                    size="small"
                    aria-label="close"
                    color="inherit"
                    onClick={(event) => handleClose(id, event)}
                    sx={{ mt: 0.25 }}
                  >
                    <CloseIcon fontSize="small" />
                  </IconButton>
                )}
              </>
            }
          />
        )}

        {/* alert snackbar */}
        {variant === 'alert' && (
          <MuiSnackbar
            anchorOrigin={anchorOrigin}
            open={true}
            onClose={(event, reason) => handleClose(id, event, reason)}
            TransitionComponent={TransitionComponent}
          >
            <Alert
              variant={alert.variant}
              severity={alert.color as any}
              onClose={(event) => handleClose(id, event)}
              sx={{ width: '100%' }}
            >
              {message}
            </Alert>
          </MuiSnackbar>
        )}
      </div>
    );
  };

  // Render all notifications (limited by maxStack)
  return (
    <div>
      {state.notifications.slice(-(state.notifications.length >= 3 ? 3 : state.notifications.length)).map(renderNotification)}
    </div>
  );
};

export default SnackbarContext;
