'use client';

import RateReviewTwoToneIcon from '@mui/icons-material/RateReviewTwoTone';
import StarBorderTwoToneIcon from '@mui/icons-material/StarBorderTwoTone';
import StarTwoToneIcon from '@mui/icons-material/StarTwoTone';
import { Button,
  Box,
  Grid,
  CardContent,
  LinearProgress,
  Rating,
  Stack,
  Typography,
 } from '@mui/material';
import { useEffect, useState } from 'react';

// material-ui

// project imports
import { useProduct } from 'contexts/ProductContext';
import { Products, Reviews } from 'types/e-commerce';
import MainCard from 'ui-component/cards/MainCard';
import ProductReview from 'ui-component/cards/ProductReview';

// Using Context API

// types

// assets

interface ProgressProps {
  like: number;
  star: number;
  value: number;
  color?:
    | 'inherit'
    | 'primary'
    | 'secondary'
    | 'error'
    | 'info'
    | 'success'
    | 'warning'
    | undefined;
}

// progress
function LinearProgressWithLabel({ like, star, color, value, ...others }: ProgressProps) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center' }}>
      <Box sx={{ minWidth: 50 }}>
        <Typography variant="body2" color="textSecondary">{`${Math.round(star)} Stars`}</Typography>
      </Box>
      <Box sx={{ width: '100%', mr: 1 }}>
        <LinearProgress value={value} variant="determinate" color={color || 'primary'} {...others} />
      </Box>
      <Box sx={{ minWidth: 35 }}>
        <Typography variant="subtitle1">{`(${Math.round(like)})`}</Typography>
      </Box>
    </Box>
  );
}

// ==============================|| PRODUCT DETAILS - REVIEWS ||============================== //

const ProductReviews = ({ product }: { product: Products }) => {
  // Using Context API
  const productContext = useProduct();
  
  const [reviews, setReviews] = useState<Reviews[]>([]);
  
  // Get reviews data from Context - using empty array as fallback
  const productReviews = productContext.state?.reviews || [];

  useEffect(() => {
    setReviews(productReviews);
  }, [productReviews]);

  return (
    <Grid container spacing={3}>
      <Grid size={{ xs: 12 }}>
        <MainCard content={false} sx={{ height: '100%' }}>
          <CardContent sx={{ height: '100%' }}>
            {product && (
              <Stack
                alignItems="center"
                justifyContent="center"
                spacing={2}
                sx={{ height: '100%' }}
              >
                <Typography variant="subtitle1">Average Rating</Typography>
                <Typography variant="h1" color="primary">
                  {Number(product.rating)}/5
                </Typography>
                <Stack direction="row" alignItems="center" spacing={1}>
                  <Rating
                    name="simple-controlled"
                    value={product.rating ?? 0}
                    icon={<StarTwoToneIcon fontSize="inherit" />}
                    emptyIcon={<StarBorderTwoToneIcon fontSize="inherit" />}
                    readOnly
                    precision={0.1}
                  />
                  <Typography variant="caption">
                    ({(product.salePrice ?? 0) + (product.offerPrice ?? 0)})
                  </Typography>
                </Stack>
              </Stack>
            )}
          </CardContent>
        </MainCard>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <MainCard content={false} sx={{ height: '100%' }}>
          <CardContent>
            <Grid container alignItems="center" justifyContent="space-between" spacing={1}>
              <Grid size={{ xs: 12 }}>
                <LinearProgressWithLabel color="secondary" star={1} value={15} like={125} />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <LinearProgressWithLabel color="secondary" star={2} value={15} like={125} />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <LinearProgressWithLabel color="secondary" star={3} value={20} like={160} />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <LinearProgressWithLabel color="secondary" star={4} value={40} like={320} />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <LinearProgressWithLabel color="secondary" star={5} value={10} like={80} />
              </Grid>
            </Grid>
          </CardContent>
        </MainCard>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <MainCard content={false} sx={{ height: '100%' }}>
          <CardContent sx={{ height: '100%' }}>
            <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ height: '100%' }}>
              <Button
                variant="outlined"
                size="large"
                startIcon={<RateReviewTwoToneIcon fontSize="inherit" />}
              >
                Write an Review
              </Button>
            </Stack>
          </CardContent>
        </MainCard>
      </Grid>

      {reviews &&
        reviews.map((review, index) => (
          <Grid size={{ xs: 12 }} key={index}>
            <ProductReview
              avatar={review.profile.avatar}
              date={review.date}
              name={review.profile.name}
              status={review.profile.status}
              rating={review.rating}
              review={review.review}
            />
          </Grid>
        ))}
      <Grid size={{ xs: 12 }}>
        <Stack direction="row" justifyContent="center">
          <Button variant="text"> Load more Comments </Button>
        </Stack>
      </Grid>
    </Grid>
  );
};

export default ProductReviews;
