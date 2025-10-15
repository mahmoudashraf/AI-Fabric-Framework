'use client';

import HomeTwoToneIcon from '@mui/icons-material/HomeTwoTone';
import { Button, Card, CardContent, CardMedia, Grid, Typography  } from '@mui/material';
import { useTheme, styled } from '@mui/material/styles';
import Link from 'next/link';

// material-ui

// project imports
import { DASHBOARD_PATH } from 'config';
import { gridSpacing } from 'constants/index';
import AnimateButton from 'ui-component/extended/AnimateButton';

// assets

const image = '/assets/images/maintenance/img-build.svg';
const imageBackground = '/assets/images/maintenance/img-bg-grid.svg';
const imageDarkBackground = '/assets/images/maintenance/img-bg-grid-dark.svg';
const imageParts = '/assets/images/maintenance/img-bg-parts.svg';

// styles
const CardMediaWrapper = styled('div')({
  maxWidth: 720,
  margin: '0 auto',
  position: 'relative',
});

const PageContentWrapper = styled('div')({
  maxWidth: 350,
  margin: '0 auto',
  textAlign: 'center',
});

const ConstructionCard = styled(Card)({
  minHeight: '100vh',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
});

const CardMediaBuild = styled('img')({
  position: 'absolute',
  top: 0,
  left: 0,
  width: '100%',
  animation: '5s bounce ease-in-out infinite',
});

const CardMediaParts = styled('img')({
  position: 'absolute',
  top: 0,
  left: 0,
  width: '100%',
  animation: '10s blink ease-in-out infinite',
});

// ========================|| UNDER CONSTRUCTION PAGE ||======================== //

const UnderConstruction = () => {
  const theme = useTheme();

  return (
    <ConstructionCard>
      <CardContent>
        <Grid container justifyContent="center" spacing={gridSpacing}>
          <Grid size={{ xs: 12 }}>
            <CardMediaWrapper>
              <CardMedia
                component="img"
                image={theme.palette.mode === 'dark' ? imageDarkBackground : imageBackground}
                title="Slider 3 image"
              />
              <CardMediaParts src={imageParts} title="Slider 1 image" />
              <CardMediaBuild src={image} title="Slider 2 image" />
            </CardMediaWrapper>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <PageContentWrapper>
              <Grid container spacing={gridSpacing}>
                <Grid size={{ xs: 12 }}>
                  <Typography variant="h1" component="div">
                    Under Construction
                  </Typography>
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <Typography variant="body2">
                    This site is on under construction!! Please check after some time
                  </Typography>
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <AnimateButton>
                    <Button variant="contained" size="large" component={Link} href={DASHBOARD_PATH}>
                      <HomeTwoToneIcon sx={{ fontSize: '1.3rem', mr: 0.75 }} /> Home
                    </Button>
                  </AnimateButton>
                </Grid>
              </Grid>
            </PageContentWrapper>
          </Grid>
        </Grid>
      </CardContent>
    </ConstructionCard>
  );
};

export default UnderConstruction;
