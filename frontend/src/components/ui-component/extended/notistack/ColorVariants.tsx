// material-ul
import { Button, Grid  } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// third-party
import { enqueueSnackbar } from 'notistack';

// project import
import SubCard from 'ui-component/cards/SubCard';

// ==============================|| NOTISTACK - COLOR VARIANTS ||============================== //

export default function ColorVariants() {
  const theme = useTheme();
  return (
    <SubCard title="Color Variants">
      <Grid container spacing={2}>
        <Grid>
          <Button
            variant="contained"
            sx={{
              backgroundColor: theme.palette.grey[900],
              '&:hover': {
                backgroundColor: theme.palette.grey[700],
              },
            }}
            onClick={() => enqueueSnackbar('This is default message.')}
          >
            Default
          </Button>
        </Grid>
        <Grid>
          <Button
            variant="contained"
            color="success"
            onClick={() => enqueueSnackbar('his is success message', { variant: 'success' })}
          >
            Success
          </Button>
        </Grid>
        <Grid>
          <Button
            variant="contained"
            color="warning"
            onClick={() => enqueueSnackbar('This is warning message', { variant: 'warning' })}
          >
            Warning
          </Button>
        </Grid>
        <Grid>
          <Button
            variant="contained"
            color="info"
            onClick={() => enqueueSnackbar('This is info message', { variant: 'info' })}
          >
            Info
          </Button>
        </Grid>
        <Grid>
          <Button
            variant="contained"
            color="error"
            onClick={() => enqueueSnackbar('This is error message', { variant: 'error' })}
          >
            Error
          </Button>
        </Grid>
      </Grid>
    </SubCard>
  );
}
