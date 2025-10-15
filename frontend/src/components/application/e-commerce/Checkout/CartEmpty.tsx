// material-ui
import { CardMedia, Grid, Typography, useMediaQuery  } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project imports
import { gridSpacing } from 'constants/index';

// assets
const imageEmpty = '/assets/images/e-commerce/empty.svg';
const imageDarkEmpty = '/assets/images/e-commerce/empty-dark.svg';

// ==============================|| CHECKOUT CART - NO/EMPTY CART ITEMS ||============================== //

const CartEmpty = () => {
  const theme = useTheme();
  const matchDownMD = useMediaQuery(theme.breakpoints.down('lg'));

  return (
    <Grid container justifyContent="center" spacing={gridSpacing} sx={{ my: 3 }}>
      <Grid size={{ xs: 12, sm: 8 }}>
        <CardMedia
          component="img"
          image={theme.palette.mode === 'dark' ? imageDarkEmpty : imageEmpty}
          title="Slider5 image"
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 8 }}>
        <Grid container direction="column" alignItems="center" spacing={1}>
          <Grid>
            <Typography variant={matchDownMD ? 'h3' : 'h1'} color="inherit">
              Cart is Empty
            </Typography>
          </Grid>
          <Grid>
            <Typography variant="body2" align="center">
              Look like you have no items in your shopping cart.
            </Typography>
          </Grid>
        </Grid>
      </Grid>
    </Grid>
  );
};

export default CartEmpty;
