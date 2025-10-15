// material-ui

// project imports

// assets
import FilterVintageTwoToneIcon from '@mui/icons-material/FilterVintageTwoTone';
import LocalMallTwoToneIcon from '@mui/icons-material/LocalMallTwoTone';
import RouterTwoToneIcon from '@mui/icons-material/RouterTwoTone';
import ShareTwoToneIcon from '@mui/icons-material/ShareTwoTone';
import { Grid, Typography, useMediaQuery  } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';

// ===========================|| WIDGET STATISTICS - ICON GRID CARD ||=========================== //

const IconGridCard = () => {
  const theme = useTheme();
  const matchDownXs = useMediaQuery(theme.breakpoints.down('sm'));

  const blockSX = {
    p: 2.5,
    borderLeft: '1px solid ',
    borderBottom: '1px solid ',
    borderLeftColor:
      theme.palette.mode === 'dark' ? theme.palette.dark.main : theme.palette.grey[200],
    borderBottomColor:
      theme.palette.mode === 'dark' ? theme.palette.dark.main : theme.palette.grey[200],
  };

  return (
    <Grid container alignItems="center" spacing={gridSpacing}>
      <Grid size={{ xs: 12, sm: 6, lg: 12 }}>
        <MainCard
          sx={{
            '& >div': {
              padding: '0px !important',
            },
            '& svg': {
              width: 45,
              height: 45,
              color: theme.palette.secondary.main,
              borderRadius: '14px',
              p: 1.25,
              backgroundColor:
                theme.palette.mode === 'dark'
                  ? theme.palette.background.default
                  : theme.palette.primary.light,
            },
          }}
        >
          <Grid container alignItems="center" spacing={0}>
            <Grid size={{ xs: 12, sm: 6, lg: 6 }} sx={blockSX}>
              <Grid
                container
                alignItems="center"
                spacing={1}
                justifyContent={matchDownXs ? 'space-between' : 'center'}
              >
                <Grid>
                  <ShareTwoToneIcon />
                </Grid>
                <Grid size="grow" sx={{ minWidth: 0 }}>
                  <Typography variant="h5" align="center">
                    1000
                  </Typography>
                  <Typography variant="subtitle2" align="center">
                    SHARES
                  </Typography>
                </Grid>
              </Grid>
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 6 }} sx={blockSX}>
              <Grid
                container
                alignItems="center"
                spacing={1}
                justifyContent={matchDownXs ? 'space-between' : 'center'}
              >
                <Grid>
                  <RouterTwoToneIcon />
                </Grid>
                <Grid size="grow" sx={{ minWidth: 0 }}>
                  <Typography variant="h5" align="center">
                    600
                  </Typography>
                  <Typography variant="subtitle2" align="center">
                    NETWORK
                  </Typography>
                </Grid>
              </Grid>
            </Grid>
          </Grid>
          <Grid container alignItems="center" spacing={0}>
            <Grid size={{ xs: 12, sm: 6, lg: 6 }} sx={blockSX}>
              <Grid
                container
                alignItems="center"
                spacing={1}
                justifyContent={matchDownXs ? 'space-between' : 'center'}
              >
                <Grid>
                  <FilterVintageTwoToneIcon />
                </Grid>
                <Grid size="grow" sx={{ minWidth: 0 }}>
                  <Typography variant="h5" align="center">
                    3550
                  </Typography>
                  <Typography variant="subtitle2" align="center">
                    RETURNS
                  </Typography>
                </Grid>
              </Grid>
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 6 }} sx={blockSX}>
              <Grid
                container
                alignItems="center"
                spacing={1}
                justifyContent={matchDownXs ? 'space-between' : 'center'}
              >
                <Grid>
                  <LocalMallTwoToneIcon />
                </Grid>
                <Grid size="grow" sx={{ minWidth: 0 }}>
                  <Typography variant="h5" align="center">
                    100%
                  </Typography>
                  <Typography variant="subtitle2" align="center">
                    ORDER
                  </Typography>
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        </MainCard>
      </Grid>
    </Grid>
  );
};

export default IconGridCard;
