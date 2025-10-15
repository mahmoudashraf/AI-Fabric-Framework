'use client';

import CloseIcon from '@mui/icons-material/Close';
import {
  AppBar,
  Button,
  Dialog,
  Divider,
  IconButton,
  ListItemText,
  ListItemButton,
  List,
  Slide,
  SlideProps,
  Toolbar,
  Typography,
} from '@mui/material';
import React from 'react';

// material-ui

// assets

// slide animation
const Transition = React.forwardRef<unknown, SlideProps>(function Transition(props, ref) {
  return <Slide direction="up" ref={ref} {...props} />;
});

// ===============================|| UI DIALOG - FULL SCREEN ||=============================== //

export default function FullScreenDialog() {
  const [open, setOpen] = React.useState(false);
  const handleClickOpen = () => {
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
  };

  return (
    <div>
      <Button variant="outlined" onClick={handleClickOpen}>
        Open full-screen dialog
      </Button>
      <Dialog fullScreen open={open} onClose={handleClose} TransitionComponent={Transition as any} TransitionProps={{ appear: true } as any}>
        {open && (
          <>
            <AppBar sx={{ position: 'relative' }}>
              <Toolbar>
                <IconButton
                  edge="start"
                  color="inherit"
                  onClick={handleClose}
                  aria-label="close"
                  size="large"
                >
                  <CloseIcon />
                </IconButton>
                <Typography variant="h3" color="inherit" sx={{ ml: 2, flex: 1 }}>
                  Sound
                </Typography>
                <Button autoFocus color="inherit" onClick={handleClose}>
                  SAVE
                </Button>
              </Toolbar>
            </AppBar>
            <List>
              <ListItemButton>
                <ListItemText
                  primary={<Typography variant="subtitle1">Phone Ringtone</Typography>}
                  secondary={<Typography variant="caption">Titania</Typography>}
                />
              </ListItemButton>
              <Divider />
              <ListItemButton>
                <ListItemText
                  primary={
                    <Typography variant="subtitle1">Default Notification Ringtone</Typography>
                  }
                  secondary={<Typography variant="caption">Tethys</Typography>}
                />
              </ListItemButton>
              <ListItemButton>
                <ListItemText
                  primary={<Typography variant="subtitle1">Phone Ringtone</Typography>}
                  secondary={<Typography variant="caption">Titania</Typography>}
                />
              </ListItemButton>
              <Divider />
              <ListItemButton>
                <ListItemText
                  primary={
                    <Typography variant="subtitle1">Default Notification Ringtone</Typography>
                  }
                  secondary={<Typography variant="caption">Tethys</Typography>}
                />
              </ListItemButton>
            </List>
          </>
        )}
      </Dialog>
    </div>
  );
}
