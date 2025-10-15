// material-ui
import KeyboardArrowDownOutlinedIcon from '@mui/icons-material/KeyboardArrowDownOutlined';
import KeyboardArrowUpOutlinedIcon from '@mui/icons-material/KeyboardArrowUpOutlined';
import { Avatar, Divider, Grid, Typography  } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// assets

// ================================|| UI LIST - CUSTOM ||================================ //

export default function CustomList() {
  const theme = useTheme();
  const avatarSuccess = {
    width: 16,
    height: 16,
    borderRadius: '5px',
    backgroundColor: theme.palette.success.light,
    color: theme.palette.success.dark,
    ml: 1.875,
  };
  const avatarError = {
    width: 16,
    height: 16,
    borderRadius: '5px',
    backgroundColor: theme.palette.orange.light,
    color: theme.palette.orange.dark,
    ml: 1.875,
  };
  return (
    <div>
      <Grid container direction="column">
        <Grid>
          <Grid container alignItems="center" justifyContent="space-between">
            <Grid>
              <Typography variant="subtitle1" color="inherit">
                Bajaj Finsery
              </Typography>
            </Grid>
            <Grid>
              <Grid container alignItems="center" justifyContent="space-between">
                <Grid>
                  <Typography variant="subtitle1" color="inherit">
                    $1839.00
                  </Typography>
                </Grid>
                <Grid>
                  <Avatar variant="rounded" sx={avatarSuccess}>
                    <KeyboardArrowUpOutlinedIcon fontSize="small" color="inherit" />
                  </Avatar>
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        </Grid>
        <Grid>
          <Typography variant="subtitle2" sx={{ color: theme.palette.success.dark }}>
            10% Profit
          </Typography>
        </Grid>
      </Grid>
      <Divider sx={{ mt: 1.5, mb: 1.5 }} />
      <Grid container direction="column">
        <Grid>
          <Grid container alignItems="center" justifyContent="space-between">
            <Grid>
              <Typography variant="subtitle1" color="inherit">
                TTML
              </Typography>
            </Grid>
            <Grid>
              <Grid container alignItems="center" justifyContent="space-between">
                <Grid>
                  <Typography variant="subtitle1" color="inherit">
                    $100.00
                  </Typography>
                </Grid>
                <Grid>
                  <Avatar variant="rounded" sx={avatarError}>
                    <KeyboardArrowDownOutlinedIcon fontSize="small" color="inherit" />
                  </Avatar>
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        </Grid>
        <Grid>
          <Typography variant="subtitle2" sx={{ color: theme.palette.orange.dark }}>
            10% loss
          </Typography>
        </Grid>
      </Grid>
      <Divider sx={{ mt: 1.5, mb: 1.5 }} />
      <Grid container direction="column">
        <Grid>
          <Grid container alignItems="center" justifyContent="space-between">
            <Grid>
              <Typography variant="subtitle1" color="inherit">
                Reliance
              </Typography>
            </Grid>
            <Grid>
              <Grid container alignItems="center" justifyContent="space-between">
                <Grid>
                  <Typography variant="subtitle1" color="inherit">
                    $200.00
                  </Typography>
                </Grid>
                <Grid>
                  <Avatar variant="rounded" sx={avatarSuccess}>
                    <KeyboardArrowUpOutlinedIcon fontSize="small" color="inherit" />
                  </Avatar>
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        </Grid>
        <Grid>
          <Typography variant="subtitle2" sx={{ color: theme.palette.success.dark }}>
            10% Profit
          </Typography>
        </Grid>
      </Grid>
      <Divider sx={{ mt: 1.5, mb: 1.5 }} />
      <Grid container direction="column">
        <Grid>
          <Grid container alignItems="center" justifyContent="space-between">
            <Grid>
              <Typography variant="subtitle1" color="inherit">
                TTML
              </Typography>
            </Grid>
            <Grid>
              <Grid container alignItems="center" justifyContent="space-between">
                <Grid>
                  <Typography variant="subtitle1" color="inherit">
                    $189.00
                  </Typography>
                </Grid>
                <Grid>
                  <Avatar variant="rounded" sx={avatarError}>
                    <KeyboardArrowDownOutlinedIcon fontSize="small" color="inherit" />
                  </Avatar>
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        </Grid>
        <Grid>
          <Typography variant="subtitle2" sx={{ color: theme.palette.orange.dark }}>
            10% loss
          </Typography>
        </Grid>
      </Grid>
    </div>
  );
}
