'use client';

import ShoppingCartTwoToneIcon from '@mui/icons-material/ShoppingCartTwoTone';
import { Button, CardContent, CardMedia, Grid, Rating, Stack, Typography  } from '@mui/material';
import Link from 'next/link';
import { useEffect, useState } from 'react';

// material-ui

// project import
import { useCart, useCartActions } from 'contexts/CartContext';
import { useSnackbar } from 'contexts/NotificationContext';

// assets
const prodImage = '/assets/images/e-commerce';

// types
import { ProductCardProps } from 'types/product';
import SkeletonProductPlaceholder from 'ui-component/cards/Skeleton/ProductPlaceholder';

import MainCard from './MainCard';

// ==============================|| PRODUCT CARD ||============================== //

const ProductCard = ({
  id,
  color,
  name,
  image,
  description,
  offerPrice,
  salePrice,
  rating,
}: ProductCardProps) => {
  const { state: cartState } = useCart();
  const { addProduct } = useCartActions();
  const { showSnackbar } = useSnackbar();

  const prodProfile = image && `${prodImage}/${image}`;
  const [productRating] = useState<number | null>(rating ?? null);
  const cart = cartState;

  const addCart = () => {
    if (id === undefined || offerPrice === undefined) return;
    const newProduct = { id, name, image, salePrice: Number(salePrice ?? 0), offerPrice, color: color ?? '', size: '8' as const, quantity: 1 };
    const updatedProducts = cart.checkout.products.some(p => p.id === id)
      ? cart.checkout.products.map(p => (p.id === id ? { ...p, quantity: p.quantity + 1 } : p))
      : [...cart.checkout.products, newProduct];

    addProduct(updatedProducts);
    showSnackbar('Add To Cart Success', {
      variant: 'success',
      alert: {
        color: 'success',
        variant: 'filled',
      },
      close: false,
    });
  };

  const [isLoading, setLoading] = useState(true);
  useEffect(() => {
    setLoading(false);
  }, []);

  return (
    <>
      {isLoading ? (
        <SkeletonProductPlaceholder />
      ) : (
        <MainCard
          content={false}
          boxShadow
          sx={{
            '&:hover': {
              transform: 'scale3d(1.02, 1.02, 1)',
              transition: 'all .4s ease-in-out',
            },
          }}
        >
          <CardMedia
            sx={{ height: 220 }}
            image={prodProfile}
            title="Contemplative Reptile"
            component={Link}
            href={`/apps/e-commerce/product-details/${id}`}
          />
          <CardContent sx={{ p: 2 }}>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12 }}>
                <Typography
                  component={Link}
                  href={`/apps/e-commerce/product-details/${id}`}
                  variant="subtitle1"
                  sx={{ textDecoration: 'none' }}
                >
                  {name}
                </Typography>
              </Grid>
              {description && (
                <Grid size={{ xs: 12 }}>
                  <Typography
                    variant="body2"
                    sx={{
                      overflow: 'hidden',
                      height: 45,
                    }}
                  >
                    {description}
                  </Typography>
                </Grid>
              )}
              <Grid size={{ xs: 12 }} sx={{ pt: '8px !important' }}>
                <Stack direction="row" alignItems="center" spacing={1}>
                  <Rating
                    precision={0.5}
                    name="size-small"
                    value={productRating}
                    size="small"
                    readOnly
                  />
                  <Typography variant="caption">({offerPrice}+)</Typography>
                </Stack>
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Grid container spacing={1}>
                    <Grid>
                      <Typography variant="h4">${offerPrice}</Typography>
                    </Grid>
                    <Grid>
                      <Typography
                        variant="h6"
                        sx={{ color: 'grey.500', textDecoration: 'line-through' }}
                      >
                        ${salePrice}
                      </Typography>
                    </Grid>
                  </Grid>
                  <Button
                    variant="contained"
                    sx={{ minWidth: 0 }}
                    onClick={addCart}
                    aria-label="Add to Cart product"
                  >
                    <ShoppingCartTwoToneIcon fontSize="small" />
                  </Button>
                </Stack>
              </Grid>
            </Grid>
          </CardContent>
        </MainCard>
      )}
    </>
  );
};

export default ProductCard;
