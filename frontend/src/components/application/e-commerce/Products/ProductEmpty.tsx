// material-ui
import { Box, CardMedia, Grid, Typography  } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project imports
import { gridSpacing } from 'constants/index';

// assets
const imageEmpty = '/assets/images/e-commerce/empty.svg';
const imageDarkEmpty = '/assets/images/e-commerce/empty-dark.svg';

// ==============================|| NO/EMPTY Product ||============================== //

const ProductEmpty = () => {
  const theme = useTheme();
  return (
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12 }}>
        <Box sx={{ maxWidth: 720, m: '0 auto', textAlign: 'center' }}>
          <Grid container justifyContent="center" spacing={gridSpacing}>
            <Grid size={{ xs: 12 }}>
              <CardMedia
                component="img"
                image={theme.palette.mode === 'dark' ? imageDarkEmpty : imageEmpty}
                title="Slider5 image"
              />
            </Grid>
            <Grid size={{ xs: 12 }}>
              <Grid container spacing={gridSpacing}>
                <Grid size={{ xs: 12 }}>
                  <Typography variant="h1" color="inherit" component="div">
                    There is no Product
                  </Typography>
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <Typography variant="body2">
                    Try checking your spelling or use more general terms
                  </Typography>
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        </Box>
      </Grid>
    </Grid>
  );
};

export default ProductEmpty;
