import ShoppingCartTwoToneIcon from '@mui/icons-material/ShoppingCartTwoTone';
import { Fab, Badge, IconButton } from '@mui/material';
import { useTheme, styled } from '@mui/material/styles';
import { sum } from 'lodash';
import Link from 'next/link';

// material-ui

// third-party

// project import
import { useCartState } from 'contexts/CartContext';
import type { CartProduct } from 'contexts/CartContext';

// assets

// types
// import { CartProductStateProps } from 'types/cart';

const StyledBadge = styled(Badge)(({ theme }) => ({
  '& .MuiBadge-badge': {
    right: 0,
    top: 3,
    border: `2px solid ${theme.palette.background.paper}`,
    padding: '0 4px',
  },
}));

// ==============================|| CART ITEMS - FLOATING BUTTON ||============================== //

const FloatingCart = () => {
  const theme = useTheme();
  const cart = useCartState();
  const totalQuantity = sum(
    cart.checkout.products.map((item: CartProduct) => item.quantity)
  );

  return (
    <Fab
      component={Link}
      href="/apps/e-commerce/checkout"
      size="large"
      sx={{
        top: '50%',
        position: 'fixed',
        right: 0,
        zIndex: 1200,
        boxShadow: theme.customShadows.warning,
        bgcolor: 'warning.main',
        color: 'warning.light',
        borderRadius: '8px',
        borderTopRightRadius: 0,
        borderBottomRightRadius: 0,
        '&:hover': {
          bgcolor: 'warning.dark',
          color: 'warning.light',
        },
      }}
    >
      <IconButton
        disableRipple
        aria-label="cart"
        sx={{ '&:hover': { bgcolor: 'transparent' } }}
        size="large"
      >
        <StyledBadge showZero badgeContent={totalQuantity} color="error">
          <ShoppingCartTwoToneIcon
            sx={{ color: theme.palette.mode === 'dark' ? 'grey.600' : 'grey.900' }}
          />
        </StyledBadge>
      </IconButton>
    </Fab>
  );
};

export default FloatingCart;
