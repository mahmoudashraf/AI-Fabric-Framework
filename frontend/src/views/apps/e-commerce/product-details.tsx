'use client';

// material-ui
import { Box, Grid, Stack, Tab, Tabs, Typography  } from '@mui/material';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useState, SyntheticEvent } from 'react';

// project imports
import ProductDescription from 'components/application/e-commerce/ProductDetails/ProductDescription';
import ProductImages from 'components/application/e-commerce/ProductDetails/ProductImages';
import ProductInfo from 'components/application/e-commerce/ProductDetails/ProductInfo';
import ProductReview from 'components/application/e-commerce/ProductDetails/ProductReview';
import RelatedProducts from 'components/application/e-commerce/ProductDetails/RelatedProducts';
import { useProduct } from 'contexts/ProductContext';
import { useNotifications } from 'contexts/NotificationContext';
import { gridSpacing } from 'constants/index';

// types
import { TabsProps } from 'types';
import FloatingCart from 'ui-component/cards/FloatingCart';
import MainCard from 'ui-component/cards/MainCard';
import Chip from 'ui-component/extended/Chip';
import Loader from 'ui-component/Loader';

function TabPanel({ children, value, index, ...other }: TabsProps) {
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`product-details-tabpanel-${index}`}
      aria-labelledby={`product-details-tab-${index}`}
      {...other}
    >
      {value === index && <Box>{children}</Box>}
    </div>
  );
}

function a11yProps(index: number) {
  return {
    id: `product-details-tab-${index}`,
    'aria-controls': `product-details-tabpanel-${index}`,
  };
}

type Props = {
  id?: string;
};

const ProductDetails = ({ id }: Props) => {
  const params = useParams();

  // Pure Context API usage
  const productContext = useProduct();
  const notificationContext = useNotifications();

  // Get data from Context API directly
  const product = productContext.state.product;

  const [loading, setLoading] = useState<boolean>(true);

  // product description tabs
  const [value, setValue] = useState(0);

  const handleChange = (_event: SyntheticEvent, newValue: number) => {
    setValue(newValue);
  };

  useEffect(() => {
    let isMounted = true;
    
    const loadProduct = async () => {
      if (!id) return;
      
      try {
        await productContext.getProduct(id);
        if (isMounted) {
          setLoading(false);
        }
      } catch (error) {
        console.warn('Failed to load product details:', error);
        notificationContext.showNotification({
          open: true,
          message: 'Failed to load product details',
          variant: 'alert',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
        if (isMounted) {
          setLoading(false);
        }
      }
    };
    
    loadProduct();
    
    return () => {
      isMounted = false;
    };
  }, [id]); // Only depend on id, not the entire context objects

  if (loading) return <Loader />;
  return (
    <Grid container alignItems="center" justifyContent="center" spacing={gridSpacing}>
      <Grid size={{ xs: 12, lg: 10 }}>
        <MainCard>
          {product && id && product?.id === Number(id) && (
            <Grid container spacing={gridSpacing}>
              <Grid size={{ xs: 12, md: 6 }}>
                <ProductImages product={product} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <ProductInfo product={product} />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Tabs
                  value={value}
                  indicatorColor="primary"
                  onChange={handleChange}
                  sx={{}}
                  aria-label="product description tabs example"
                  variant="scrollable"
                >
                  <Tab component={Link} href="#" label="Description" {...a11yProps(0)} />
                  <Tab
                    component={Link}
                    href="#"
                    label={
                      <Stack direction="row" alignItems="center">
                        Reviews{' '}
                        <Chip
                          label={String(product.salePrice)}
                          size="small"
                          chipcolor="secondary"
                          sx={{ ml: 1.5 }}
                        />
                      </Stack>
                    }
                    {...a11yProps(1)}
                  />
                </Tabs>
                <TabPanel value={value} index={0}>
                  <ProductDescription />
                </TabPanel>
                <TabPanel value={value} index={1}>
                  <ProductReview product={product} />
                </TabPanel>
              </Grid>
            </Grid>
          )}
        </MainCard>
      </Grid>
      <Grid size={{ xs: 12, lg: 10 }} sx={{ mt: 3 }}>
        <Typography variant="h2">Related Products</Typography>
      </Grid>
      <Grid size={{ xs: 11, lg: 10 }}>
        {params?.id ? <RelatedProducts id={String(params.id)} /> : <RelatedProducts />}
      </Grid>
      <FloatingCart />
    </Grid>
  );
};

// Enterprise Pattern: Apply error boundary HOC
import { withErrorBoundary } from '@/components/enterprise';
export default withErrorBoundary(ProductDetails);
