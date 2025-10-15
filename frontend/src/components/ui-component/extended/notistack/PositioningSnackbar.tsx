// material-ul
import { Button, Grid  } from '@mui/material';

// third-party
import { enqueueSnackbar } from 'notistack';

// project import
import SubCard from 'ui-component/cards/SubCard';

// ==============================|| NOTISTACK - POSTIONING ||============================== //

export default function PositioningSnackbar() {
  return (
    <SubCard title="Positioning">
      <Grid container spacing={2}>
        <Grid>
          <Button
            variant="contained"
            onClick={() =>
              enqueueSnackbar('This is default message.', {
                anchorOrigin: {
                  vertical: 'top',
                  horizontal: 'left',
                },
                variant: 'info',
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
              enqueueSnackbar('his is success message', {
                anchorOrigin: {
                  vertical: 'top',
                  horizontal: 'center',
                },
                variant: 'info',
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
              enqueueSnackbar('This is warning message', {
                anchorOrigin: {
                  vertical: 'top',
                  horizontal: 'right',
                },
                variant: 'info',
              })
            }
          >
            Top-right
          </Button>
        </Grid>
        <Grid>
          <Button
            variant="contained"
            onClick={() =>
              enqueueSnackbar('This is info message', {
                anchorOrigin: {
                  vertical: 'bottom',
                  horizontal: 'left',
                },
                variant: 'info',
              })
            }
          >
            Bottom-left
          </Button>
        </Grid>
        <Grid>
          <Button
            variant="contained"
            onClick={() =>
              enqueueSnackbar('This is info message', {
                anchorOrigin: {
                  vertical: 'bottom',
                  horizontal: 'center',
                },
                variant: 'info',
              })
            }
          >
            Bottom-center
          </Button>
        </Grid>
        <Grid>
          <Button
            variant="contained"
            onClick={() =>
              enqueueSnackbar('This is info message', {
                anchorOrigin: {
                  vertical: 'bottom',
                  horizontal: 'right',
                },
                variant: 'info',
              })
            }
          >
            Bottom-Right
          </Button>
        </Grid>
      </Grid>
    </SubCard>
  );
}
