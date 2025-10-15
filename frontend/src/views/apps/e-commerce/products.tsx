'use client';

import ArrowForwardIosIcon from '@mui/icons-material/ArrowForwardIos';
import FilterAltIcon from '@mui/icons-material/FilterAlt';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import SearchIcon from '@mui/icons-material/Search';
import { Box,
  Button,
  Divider,
  Drawer,
  Grid,
  IconButton,
  InputAdornment,
  Menu,
  MenuItem,
  Stack,
  TextField,
  Typography,
  useMediaQuery,
 } from '@mui/material';
import { styled, useTheme } from '@mui/material/styles';
import { useEffect, useState, ReactElement } from 'react';

// material-ui

// third party
import PerfectScrollbar from 'react-perfect-scrollbar';

// project imports
import ProductEmpty from 'components/application/e-commerce/Products/ProductEmpty';
import ProductFilter from 'components/application/e-commerce/Products/ProductFilter';
import ProductFilterView from 'components/application/e-commerce/Products/ProductFilterView';
import SortOptions from 'components/application/e-commerce/Products/SortOptions';
import useConfig from 'hooks/useConfig';
import { useProduct } from 'contexts/ProductContext';
import { useCart } from 'contexts/CartContext';
import { useNotifications } from 'contexts/NotificationContext';
import { appDrawerWidth, gridSpacing } from 'constants/index';

// assets

// types
import { Products as ProductsTypo, ProductsFilter } from 'types/e-commerce';
import FloatingCart from 'ui-component/cards/FloatingCart';
import ProductCard from 'ui-component/cards/ProductCard';
import SkeletonProductPlaceholder from 'ui-component/cards/Skeleton/ProductPlaceholder';
import Loader from 'ui-component/Loader';

// product list container
const Main = styled('main', { shouldForwardProp: prop => prop !== '$open' })<{ $open?: boolean }>(
  ({ theme, $open }) => ({
    flexGrow: 1,
    transition: theme.transitions.create('margin', {
      easing: theme.transitions.easing.sharp,
      duration: theme.transitions.duration.shorter,
    }),
    marginRight: -appDrawerWidth,
    [theme.breakpoints.down('xl')]: {
      paddingRight: 0,
      marginRight: 0,
    },
    ...($open && {
      transition: theme.transitions.create('margin', {
        easing: theme.transitions.easing.easeOut,
        duration: theme.transitions.duration.shorter,
      }),
      marginRight: 0,
    }),
  })
);

const filterIsEqual = (a1: ProductsFilter, a2: ProductsFilter) =>
  a1 === a2 ||
  (a1.length === a2.length &&
    a1.search === a2.search &&
    a1.sort === a2.sort &&
    a1.price === a2.price &&
    a1.rating === a2.rating &&
    JSON.stringify(a1.gender) === JSON.stringify(a2.gender) &&
    JSON.stringify(a1.categories) === JSON.stringify(a2.categories) &&
    JSON.stringify(a1.colors) === JSON.stringify(a2.colors));

// ==============================|| E-COMMERCE - PRODUCT GRID ||============================== //

const ProductsList = () => {
  const theme = useTheme();
  
  // Using Context API
  const productContext = useProduct();
  const cartContext = useCart();
  const { showNotifications } = useNotifications();
  
  // Using Context API

  const { borderRadius } = useConfig();

  const matchDownSM = useMediaQuery(theme.breakpoints.down('md'));
  const matchDownMD = useMediaQuery(theme.breakpoints.down('lg'));
  const matchDownLG = useMediaQuery(theme.breakpoints.down('xl'));

  const [loading, setLoading] = useState<boolean>(true);
  const [productLoading, setProductLoading] = useState(true);

  useEffect(() => {
    setProductLoading(false);
  }, []);

  // drawer
  const [open, setOpen] = useState(productLoading);
  const handleDrawerOpen = () => {
    setOpen(prevState => !prevState);
  };

  useEffect(() => {
    setOpen(!matchDownLG);
  }, [matchDownLG]);

  // Use Context API directly
  const cart = cartContext.state;
  const products = productContext.state.products;

  useEffect(() => {
    // Use Context API methods directly
    try {
      productContext.getProducts();
      setLoading(false);
    } catch (error) {
      showNotifications({
        message: 'Failed to load products',
        alert: { color: 'error', variant: 'filled' },
        close: true,
      });
      setLoading(false);
    }

    // ensure cart state accessed safely
    if (cart?.checkout?.step && cart.checkout.step > 2 && typeof cartContext.resetCart === 'function') {
      cartContext.resetCart();
    }

    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // filter
  const initialState: ProductsFilter = {
    search: '',
    sort: 'low',
    gender: [],
    categories: ['all'],
    colors: [],
    price: '',
    rating: 0,
  };
  const [filter, setFilter] = useState(initialState);

  // search filter
  const handleSearch = async (
    event: React.ChangeEvent<HTMLTextAreaElement | HTMLInputElement> | undefined
  ) => {
    const newString = event?.target.value;
    setFilter({ ...filter, search: newString! });
  };

  // sort options
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const openSort = Boolean(anchorEl);
  const handleClickListItem = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handelFilter = (type: string, params: string, rating?: number) => {
    setProductLoading(true);
    switch (type) {
      case 'gender':
        if (filter.gender.some(item => item === params)) {
          setFilter({ ...filter, gender: filter.gender.filter(item => item !== params) });
        } else {
          setFilter({ ...filter, gender: [...filter.gender, params] });
        }
        break;
      case 'categories':
        if (filter.categories.some(item => item === params)) {
          setFilter({ ...filter, categories: filter.categories.filter(item => item !== params) });
        } else if (filter.categories.some(item => item === 'all') || params === 'all') {
          setFilter({ ...filter, categories: [params] });
        } else {
          setFilter({ ...filter, categories: [...filter.categories, params] });
        }

        break;
      case 'colors':
        if (filter.colors.some(item => item === params)) {
          setFilter({ ...filter, colors: filter.colors.filter(item => item !== params) });
        } else {
          setFilter({ ...filter, colors: [...filter.colors, params] });
        }
        break;
      case 'price':
        setFilter({ ...filter, price: params });
        break;
      case 'search':
        setFilter({ ...filter, search: params });
        break;
      case 'sort':
        setFilter({ ...filter, sort: params });
        break;
      case 'rating':
        setFilter({ ...filter, rating: rating! });
        break;
      case 'reset':
        setFilter(initialState);
        break;
      default:
      // no options
    }
  };

  const filterData = async () => {
    try {
      await productContext.filterProducts(filter);
      setProductLoading(false);
    } catch (error) {
      setProductLoading(false);
      showNotifications({
        message: 'Failed to filter products',
        alert: { color: 'error', variant: 'filled' },
        close: true,
      });
    }
  };

  useEffect(() => {
    filterData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filter]);

  // sort filter
  const handleMenuItemClick = (_event: React.MouseEvent<HTMLElement>, index: string) => {
    setFilter({ ...filter, sort: index });
    setAnchorEl(null);
  };

  const sortLabel = SortOptions.filter(items => items.value === filter.sort);

  let productResult: ReactElement | ReactElement[] = <></>;

  if (!productLoading) {
    if (products && products.length > 0) {
      productResult = products.map((product: ProductsTypo, index) => (
        <Grid key={index} size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
            <ProductCard
            id={product.id ?? index}
            image={product.image}
            name={product.name}
              description={product.description ?? ''}
              offerPrice={product.offerPrice ?? 0}
              salePrice={product.salePrice ?? 0}
              rating={product.rating ?? 0}
            color={product.colors && product.colors[0] ? product.colors[0]! : ''}
          />
        </Grid>
      ));
    } else {
      productResult = (
        <Grid size={{ xs: 12 }} sx={{ mt: 3 }}>
          <ProductEmpty />
        </Grid>
      );
    }
  }

  const spacingMD = matchDownMD ? 1 : 1.5;

  if (loading) return <Loader />;

  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12 }}>
        <Grid
          container
          alignItems="center"
          justifyContent="space-between"
          spacing={matchDownMD ? 0.5 : 2}
        >
          <Grid>
            <Stack direction="row" alignItems="center" spacing={1}>
              <Typography variant="h4">Shop</Typography>
              <IconButton size="large" aria-label="forward">
                <ArrowForwardIosIcon
                  sx={{ width: '0.875rem', height: '0.875rem', fontWeight: 500, color: 'grey.500' }}
                />
              </IconButton>
            </Stack>
          </Grid>
          <Grid>
            <Stack
              direction="row"
              alignItems="center"
              justifyContent="center"
              spacing={matchDownSM ? 0.5 : spacingMD}
            >
              <TextField
                sx={{ width: { xs: 140, md: 'auto' } }}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchIcon fontSize="small" />
                    </InputAdornment>
                  ),
                }}
                value={filter.search}
                placeholder="Search Product"
                size="small"
                onChange={handleSearch}
              />

              <Typography sx={{ pl: 1.5, fontSize: '1rem', color: 'grey.500', fontWeight: 400 }}>
                |
              </Typography>

              <Button
                disableRipple
                onClick={handleDrawerOpen}
                color="secondary"
                startIcon={<FilterAltIcon sx={{ fontWeight: 500, color: 'secondary.200' }} />}
              >
                Filter
              </Button>

              <Typography
                sx={{
                  display: { xs: 'none', sm: 'flex' },
                  fontSize: '1rem',
                  color: 'grey.500',
                  fontWeight: 400,
                }}
              >
                |
              </Typography>
              <Stack
                direction="row"
                alignItems="center"
                justifyContent="center"
                sx={{ display: { xs: 'none', sm: 'flex' } }}
              >
                <Typography variant="h5">Sort by: </Typography>
                <Button
                  id="demo-positioned-button"
                  aria-controls="demo-positioned-menu"
                  aria-haspopup="true"
                  aria-expanded={openSort ? 'true' : undefined}
                  onClick={handleClickListItem}
                  sx={{ color: 'grey.500', fontWeight: 400 }}
                  endIcon={<KeyboardArrowDownIcon />}
                >
                  {sortLabel.length > 0 && sortLabel[0]?.label}
                </Button>
                <Menu
                  id="demo-positioned-menu"
                  aria-labelledby="demo-positioned-button"
                  anchorEl={anchorEl}
                  open={openSort}
                  onClose={handleClose}
                  anchorOrigin={{
                    vertical: 'bottom',
                    horizontal: 'right',
                  }}
                  transformOrigin={{
                    vertical: 'top',
                    horizontal: 'right',
                  }}
                >
                  {SortOptions.map((option, index) => (
                    <MenuItem
                      sx={{ p: 1.5 }}
                      key={index}
                      selected={option.value === filter.sort}
                      onClick={event => handleMenuItemClick(event, option.value)}
                    >
                      {option.label}
                    </MenuItem>
                  ))}
                </Menu>
              </Stack>
            </Stack>
          </Grid>
        </Grid>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <Divider sx={{ borderColor: 'grey.400' }} />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <Box sx={{ display: 'flex' }}>
          <Main $open={open}>
            <ProductFilterView
              filter={filter}
              filterIsEqual={filterIsEqual}
              handelFilter={handelFilter}
              initialState={initialState}
            />
            <Grid container spacing={gridSpacing}>
              {productLoading
                ? [1, 2, 3, 4, 5, 6, 7, 8].map(item => (
                    <Grid key={item} size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
                      <SkeletonProductPlaceholder />
                    </Grid>
                  ))
                : productResult}
            </Grid>
          </Main>
          <Drawer
            sx={{
              ml: open ? 3 : 0,
              height: matchDownLG ? '100vh' : 'auto',
              flexShrink: 0,
              zIndex: { xs: 1200, lg: open ? 1000 : -1 },
              overflowX: 'hidden',
              width: appDrawerWidth,
              '& .MuiDrawer-paper': {
                height: 'auto',
                width: appDrawerWidth,
                position: matchDownLG ? 'fixed' : 'relative',
                border: 'none',
                borderRadius: matchDownLG ? 0 : `${borderRadius}px`,
              },
            }}
            variant={matchDownLG ? 'temporary' : 'persistent'}
            anchor="right"
            open={open}
            ModalProps={{ keepMounted: true }}
            onClose={handleDrawerOpen}
          >
            {open && (
              <PerfectScrollbar component="div">
                <ProductFilter filter={filter} handelFilter={handelFilter as any} />
              </PerfectScrollbar>
            )}
          </Drawer>
        </Box>
      </Grid>
      <FloatingCart />
    </Grid>
  );
};

// Enterprise Pattern: Apply error boundary HOC
import { withErrorBoundary } from '@/components/enterprise';
export default withErrorBoundary(ProductsList);
