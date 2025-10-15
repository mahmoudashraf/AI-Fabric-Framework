'use client';

// material-ui
import { Box, useMediaQuery } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useState, ReactElement } from 'react';

// third-party
import Slider from 'react-slick';

// project imports
import { useProduct } from 'contexts/ProductContext';

// types
import { Products } from 'types/e-commerce';
import ProductCard from 'ui-component/cards/ProductCard';

// ==============================|| PRODUCT DETAILS - RELATED PRODUCTS ||============================== //

const RelatedProducts = ({ id }: { id?: string }) => {
  const theme = useTheme();
  const { state, getRelatedProducts } = useProduct();
  const [related, setRelated] = useState<Products[]>([]);
  const [itemsToShow, setItemsToShow] = useState<number>(5);
  const matchDownSM = useMediaQuery(theme.breakpoints.down('sm'));
  const matchDownMD = useMediaQuery(theme.breakpoints.down('md'));
  const matchDownLG = useMediaQuery(theme.breakpoints.down('lg'));
  const matchDownXL = useMediaQuery(theme.breakpoints.down('xl'));
  const { relatedProducts } = state;

  useEffect(() => {
    setRelated(relatedProducts);
  }, [relatedProducts]);

  useEffect(() => {
    getRelatedProducts(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (matchDownSM) {
      setItemsToShow(1);
      return;
    }
    if (matchDownMD) {
      setItemsToShow(2);
      return;
    }
    if (matchDownLG) {
      setItemsToShow(3);
      return;
    }
    if (matchDownXL) {
      setItemsToShow(4);
      return;
    } else {
      setItemsToShow(5);
    }
  }, [matchDownSM, matchDownMD, matchDownLG, matchDownXL, itemsToShow]);

  const settings = {
    dots: false,
    centerMode: true,
    swipeToSlide: true,
    focusOnSelect: true,
    centerPadding: '0px',
    slidesToShow: itemsToShow,
  };

  let productResult: ReactElement | ReactElement[] = <></>;
  if (related) {
    productResult = related.map((product: Products, index) => (
      <Box key={index} sx={{ p: 1.5 }}>
        <ProductCard
          key={index}
          id={product.id ?? ''}
          image={product.image}
          name={product.name}
          offerPrice={product.offerPrice ?? 0}
          salePrice={product.salePrice ?? 0}
          rating={product.rating ?? 0}
        />
      </Box>
    ));
  }

  return <Slider {...settings}>{productResult}</Slider>;
};

export default RelatedProducts;
