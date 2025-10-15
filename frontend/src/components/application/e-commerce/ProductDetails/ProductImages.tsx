'use client';

import { Box, CardMedia, Grid, useMediaQuery  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useState } from 'react';

// material-ui

// project import
import Lightbox from 'react-18-image-lightbox';
import Slider from 'react-slick';

import useConfig from 'hooks/useConfig';
const gridSpacing = 3;
import { Products } from 'types/e-commerce';
import MainCard from 'ui-component/cards/MainCard';
import Avatar from 'ui-component/extended/Avatar';

// third-party

// types

// assets
const prod1 = '/assets/images/e-commerce/prod-1.png';
const prod2 = '/assets/images/e-commerce/prod-2.png';
const prod3 = '/assets/images/e-commerce/prod-3.png';
const prod4 = '/assets/images/e-commerce/prod-4.png';
const prod5 = '/assets/images/e-commerce/prod-5.png';
const prod6 = '/assets/images/e-commerce/prod-6.png';
const prod7 = '/assets/images/e-commerce/prod-7.png';
const prod8 = '/assets/images/e-commerce/prod-8.png';

const prodImage = '/assets/images/e-commerce';

// ==============================|| PRODUCT DETAILS - IMAGES ||============================== //

const ProductImages = ({ product }: { product: Products }) => {
  const theme = useTheme();
  const { borderRadius } = useConfig();
  const products = [prod1, prod2, prod3, prod4, prod5, prod6, prod7, prod8];

  const matchDownLG = useMediaQuery(theme.breakpoints.up('lg'));
  const initialImage = product.image ? `${prodImage}/${product.image}` : prod1;

  const [selected, setSelected] = useState(initialImage);
  const [modal, setModal] = useState(false);
  const [isOpen, setIsOpen] = useState(false);
  const [photoIndex, setPhotoIndex] = useState(0);

  const images = [prod1, prod2, prod3, prod4, prod5, prod6, prod7, prod8];

  const lgNo = matchDownLG ? 4 : 3;

  const settings = {
    dots: false,
    centerMode: true,
    swipeToSlide: true,
    focusOnSelect: true,
    centerPadding: '0px',
    slidesToShow: products.length > 3 ? lgNo : products.length,
  };

  return (
    <>
      <Grid container alignItems="center" justifyContent="center" spacing={gridSpacing}>
        <Grid size={{ xs: 12 }}>
          <MainCard content={false} sx={{ m: '0 auto' }}>
            <CardMedia
              onClick={() => {
                setModal(!modal);
                setIsOpen(true);
              }}
              component="img"
              image={selected}
              sx={{ borderRadius: `${borderRadius}px`, overflow: 'hidden', cursor: 'zoom-in' }}
              alt="product images"
            />
          </MainCard>
        </Grid>
        <Grid size={{ xs: 11, sm: 7 }}>
          <Slider {...settings}>
            {products.map((item, index) => (
              <Box
                key={index}
                onClick={() => {
                  setSelected(item);
                  setPhotoIndex(index);
                }}
                sx={{ p: 1 }}
              >
                <Avatar
                  outline={selected === item}
                  size={matchDownLG ? 'lg' : 'md'}
                  color="primary"
                  src={item}
                  variant="rounded"
                  sx={{ m: '0 auto', cursor: 'pointer' }}
                  alt="product images"
                />
              </Box>
            ))}
          </Slider>
        </Grid>
      </Grid>
      {isOpen && (
        <Lightbox
          mainSrc={images[photoIndex] || prod1}
          nextSrc={images[(photoIndex + 1) % images.length] || prod1}
          prevSrc={images[(photoIndex + images.length - 1) % images.length] || prod1}
          onCloseRequest={() => setIsOpen(false)}
          onMovePrevRequest={() => setPhotoIndex((photoIndex + images.length - 1) % images.length)}
          onMoveNextRequest={() => setPhotoIndex((photoIndex + 1) % images.length)}
        />
      )}
    </>
  );
};

export default ProductImages;
