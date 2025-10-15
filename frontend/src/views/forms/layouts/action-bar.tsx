'use client';

// material-ui
import { Button,
  CardContent,
  CardActions,
  Divider,
  Grid,
  TextField,
  FormHelperText,
  Typography,
 } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project imports
import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';
import InputLabel from 'ui-component/extended/Form/InputLabel';

// ==============================|| ActionBar ||============================== //
function ActionBar() {
  const theme = useTheme();
  return (
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12 }}>
        <Grid container spacing={gridSpacing}>
          <Grid size={{ xs: 12 }}>
            <MainCard title="Simple Action Bar" content={false}>
              <CardContent>
                <Grid container spacing={2} alignItems="center">
                  <Grid size={{ xs: 12 }}>
                    <InputLabel>Name</InputLabel>
                    <TextField fullWidth placeholder="Enter full name" />
                    <FormHelperText>Please enter your full name</FormHelperText>
                  </Grid>
                </Grid>
              </CardContent>
              <Divider />
              <CardActions>
                <Grid container spacing={1}>
                  <Grid>
                    <Button variant="contained" color="secondary">
                      Submit
                    </Button>
                  </Grid>
                  <Grid>
                    <Button variant="outlined">Clear</Button>
                  </Grid>
                </Grid>
              </CardActions>
            </MainCard>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <MainCard title="Action Button with Link" content={false}>
              <CardContent>
                <Grid container spacing={2} alignItems="center">
                  <Grid size={{ xs: 12 }}>
                    <InputLabel>Name</InputLabel>
                    <TextField fullWidth placeholder="Enter full name" />
                    <FormHelperText>Please enter your full name</FormHelperText>
                  </Grid>
                </Grid>
              </CardContent>
              <Divider />
              <CardActions>
                <Grid container alignItems="center" justifyContent="flex-end" spacing={2}>
                  <Grid>
                    <Button variant="contained" color="secondary">
                      Submit
                    </Button>
                  </Grid>
                  <Grid>
                    <Typography variant="body2" sx={{ m: 0 }}>
                      or
                    </Typography>
                  </Grid>
                  <Grid>
                    <Button variant="text">Clear</Button>
                  </Grid>
                </Grid>
              </CardActions>
            </MainCard>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <MainCard title="With side action button" content={false}>
              <CardContent>
                <Grid container spacing={2} alignItems="center">
                  <Grid size={{ xs: 12 }}>
                    <InputLabel>Name</InputLabel>
                    <TextField fullWidth placeholder="Enter full name" />
                    <FormHelperText>Please enter your full name</FormHelperText>
                  </Grid>
                </Grid>
              </CardContent>
              <Divider />
              <CardActions>
                <Grid container alignItems="center" justifyContent="space-between" spacing={2}>
                  <Grid>
                    <Grid container alignItems="center" justifyContent="flex-end" spacing={2}>
                      <Grid>
                        <Button variant="contained" color="secondary">
                          Submit
                        </Button>
                      </Grid>
                      <Grid>
                        <Button variant="outlined">Clear</Button>
                      </Grid>
                    </Grid>
                  </Grid>
                  <Grid>
                    <Grid>
                      {/**
                       * wrong prop color="error"
                       * color?: "inherit" | "primary" | "secondary" | undefined
                       */}
                      <Button
                        variant="contained"
                        style={{ background: theme.palette.error.main, borderColor: '#EDE7F6' }}
                      >
                        Delete
                      </Button>
                    </Grid>
                  </Grid>
                </Grid>
              </CardActions>
            </MainCard>
          </Grid>
        </Grid>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <Grid container spacing={gridSpacing}>
          <Grid size={{ xs: 12 }}>
            <MainCard title="Right Align Action Bar" content={false}>
              <CardContent>
                <Grid container spacing={2} alignItems="center">
                  <Grid size={{ xs: 12 }}>
                    <InputLabel>Name</InputLabel>
                    <TextField fullWidth placeholder="Enter full name" />
                    <FormHelperText>Please enter your full name</FormHelperText>
                  </Grid>
                </Grid>
              </CardContent>
              <Divider />
              <CardActions>
                <Grid container alignItems="center" justifyContent="flex-end" spacing={2}>
                  <Grid>
                    <Button variant="contained" color="secondary">
                      Submit
                    </Button>
                  </Grid>
                  <Grid>
                    <Button variant="outlined">Clear</Button>
                  </Grid>
                </Grid>
              </CardActions>
            </MainCard>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <MainCard title="Horizontal Form" content={false}>
              <CardContent>
                <Grid container spacing={2} alignItems="center">
                  <Grid size={{ xs: 12, sm: 3, lg: 4 }} sx={{ pt: { xs: 2, sm: '0 !important' } }}>
                    <InputLabel horizontal sx={{ textAlign: { xs: 'left', sm: 'right' } }}>
                      Name :
                    </InputLabel>
                  </Grid>
                  <Grid size={{ xs: 12, sm: 9, lg: 8 }}>
                    <TextField fullWidth placeholder="Enter full name" />
                    <FormHelperText>Please enter your full name</FormHelperText>
                  </Grid>
                </Grid>
              </CardContent>
              <Divider />
              <CardActions>
                <Grid container spacing={2} alignItems="center">
                  <Grid size={{ xs: 12 }} />
                  <Grid size={{ xs: 12 }}>
                    <Grid container alignItems="center" spacing={2}>
                      <Grid>
                        <Button variant="contained" color="secondary">
                          Submit
                        </Button>
                      </Grid>
                      <Grid>
                        <Button variant="outlined">Clear</Button>
                      </Grid>
                    </Grid>
                  </Grid>
                </Grid>
              </CardActions>
            </MainCard>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <MainCard title="Top & Bottom Actions Bars" content={false}>
              <CardActions>
                <Grid container alignItems="center" justifyContent="space-between" spacing={2}>
                  <Grid>
                    <Typography variant="h5" sx={{ m: 0 }}>
                      Top Actions
                    </Typography>
                  </Grid>
                  <Grid>
                    <Grid container alignItems="center" justifyContent="flex-end" spacing={2}>
                      {/**
                       * wrong prop color="error"
                       * color?: "inherit" | "primary" | "secondary" | undefined
                       * placeholder style={{color: 'red', borderColor: '#EDE7F6'}}
                       */}
                      <Grid>
                        <Button variant="outlined" style={{ color: 'red', borderColor: '#EDE7F6' }}>
                          Delete
                        </Button>
                      </Grid>
                    </Grid>
                  </Grid>
                </Grid>
              </CardActions>
              <Divider />
              <CardContent>
                <InputLabel>Name</InputLabel>
                <TextField fullWidth placeholder="Enter full name" />
                <FormHelperText>Please enter your full name</FormHelperText>
              </CardContent>
              <Divider />
              <CardActions>
                <Grid container spacing={2} alignItems="center">
                  <Grid size={{ xs: 12 }}>
                    <Grid container alignItems="center" spacing={2}>
                      <Grid>
                        <Button variant="contained" color="secondary">
                          Submit
                        </Button>
                      </Grid>
                      <Grid>
                        <Button variant="outlined">Clear</Button>
                      </Grid>
                    </Grid>
                  </Grid>
                </Grid>
              </CardActions>
            </MainCard>
          </Grid>
        </Grid>
      </Grid>
    </Grid>
  );
}

export default ActionBar;
