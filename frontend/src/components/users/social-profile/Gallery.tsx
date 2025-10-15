'use client';

// material-ui
import { Button, Grid, Typography  } from '@mui/material';
import React from 'react';

// project imports
import { useUser } from 'contexts/UserContext';
import { useNotifications } from 'contexts/NotificationContext';
// Using Context API  
import { gridSpacing } from 'constants/index';

// types
import { GenericCardProps } from 'types';
import GalleryCard from 'ui-component/cards/GalleryCard';
import MainCard from 'ui-component/cards/MainCard';

// Using Context API

// ==============================|| SOCIAL PROFILE - GALLERY ||============================== //

const Gallery = () => {
  // Fully migrated to Context system
  const userContext = useUser();
  const notificationContext = useNotifications();
  
  const [gallery, setGallery] = React.useState<GenericCardProps[]>([]);
  
  // Use Context data directly
  const galleryData = userContext.gallery;

  React.useEffect(() => {
    setGallery(galleryData);
  }, [galleryData]);

  React.useEffect(() => {
    try {
      userContext.getGallery();
    } catch (error) {
      notificationContext.showNotification({
        open: true,
        message: 'Failed to load gallery',
        variant: 'alert',
        alert: { color: 'error', variant: 'filled' },
        close: true,
      });
    }
  }, [userContext]);

  let galleryResult: React.ReactElement[] | React.ReactElement = <></>;
  if (gallery) {
    galleryResult = gallery.map((item, index) => (
      <Grid key={index} size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
        <GalleryCard {...item} />
      </Grid>
    ));
  }

  return (
    <MainCard
      title={
        <Grid container alignItems="center" justifyContent="space-between" spacing={gridSpacing}>
          <Grid>
            <Typography variant="h3">Gallery</Typography>
          </Grid>
          <Grid>
            <Button variant="contained" color="secondary">
              Add Photos
            </Button>
          </Grid>
        </Grid>
      }
    >
      <Grid container direction="row" spacing={gridSpacing}>
        {galleryResult}
      </Grid>
    </MainCard>
  );
};

export default Gallery;
