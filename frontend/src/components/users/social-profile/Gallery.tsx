'use client';

// material-ui
import { Button, Grid, Typography, Alert, Box, CircularProgress, Chip  } from '@mui/material';
import React from 'react';

// project imports
import { useUser } from 'contexts/UserContext';
import { useNotifications } from 'contexts/NotificationContext';
import { useQuery } from '@tanstack/react-query';
import { aiProfileApi } from '@/services/ai-profile-api';
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

  // Fetch AI profile photos
  const { data: aiProfile, isLoading: aiProfileLoading } = useQuery({
    queryKey: ['aiProfile', 'latest', 'gallery'],
    queryFn: async () => {
      try {
        const profile = await aiProfileApi.getLatestProfile();
        if (profile && profile.status === 'COMPLETE') {
          return aiProfileApi.parseAiAttributes(profile.aiAttributes);
        }
        return null;
      } catch (error) {
        console.log('No published AI profile found:', error);
        return null;
      }
    },
    retry: false,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });

  React.useEffect(() => {
    // Combine gallery data with AI profile photos
    const combinedGallery: GenericCardProps[] = [...galleryData];
    
    if (aiProfile?.photos) {
      const now = new Date().toISOString().split('T')[0] || new Date().toLocaleDateString();
      
      // Add profile photo
      if (aiProfile.photos.profilePhoto) {
        combinedGallery.push({
          title: 'Profile Photo (AI Generated)',
          image: aiProfile.photos.profilePhoto,
          dateTime: now as string,
        } as GenericCardProps);
      }
      
      // Add cover photo
      if (aiProfile.photos.coverPhoto) {
        combinedGallery.push({
          title: 'Cover Photo (AI Generated)',
          image: aiProfile.photos.coverPhoto,
          dateTime: now as string,
        } as GenericCardProps);
      }
      
      // Add professional photos
      aiProfile.photos.professional?.forEach((url, index) => {
        combinedGallery.push({
          title: `Professional Photo ${index + 1}`,
          image: url,
          dateTime: now as string,
        } as GenericCardProps);
      });
      
      // Add team photos
      aiProfile.photos.team?.forEach((url, index) => {
        combinedGallery.push({
          title: `Team Photo ${index + 1}`,
          image: url,
          dateTime: now as string,
        } as GenericCardProps);
      });
      
      // Add project photos
      aiProfile.photos.project?.forEach((url, index) => {
        combinedGallery.push({
          title: `Project Photo ${index + 1}`,
          image: url,
          dateTime: now as string,
        } as GenericCardProps);
      });
    }
    
    setGallery(combinedGallery);
  }, [galleryData, aiProfile]);

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

  const aiPhotoCount = React.useMemo(() => {
    if (!aiProfile?.photos) return 0;
    let count = 0;
    if (aiProfile.photos.profilePhoto) count++;
    if (aiProfile.photos.coverPhoto) count++;
    count += aiProfile.photos.professional?.length || 0;
    count += aiProfile.photos.team?.length || 0;
    count += aiProfile.photos.project?.length || 0;
    return count;
  }, [aiProfile]);

  let galleryResult: React.ReactElement[] | React.ReactElement = <></>;
  if (gallery && gallery.length > 0) {
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
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Typography variant="h3">Gallery</Typography>
              {aiPhotoCount > 0 && (
                <Chip 
                  label={`${aiPhotoCount} AI`} 
                  size="small" 
                  color="primary" 
                  variant="outlined"
                />
              )}
            </Box>
          </Grid>
          <Grid>
            <Button variant="contained" color="secondary">
              Add Photos
            </Button>
          </Grid>
        </Grid>
      }
    >
      {aiProfileLoading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      ) : (
        <>
          {aiProfile?.photos && aiPhotoCount > 0 && (
            <Alert severity="info" sx={{ mb: 3 }}>
              Showing {gallery.length} photos ({aiPhotoCount} from AI Profile, {gallery.length - aiPhotoCount} from other sources)
            </Alert>
          )}
          
          {gallery.length === 0 ? (
            <Alert severity="info">
              No photos in your gallery yet. {!aiProfile?.photos && 'Upload photos via the "Generate with AI" tab to get started.'}
            </Alert>
          ) : (
            <Grid container direction="row" spacing={gridSpacing}>
              {galleryResult}
            </Grid>
          )}
        </>
      )}
    </MainCard>
  );
};

// Enterprise Pattern: Apply error boundary HOC
import { withErrorBoundary } from '@/components/enterprise';
export default withErrorBoundary(Gallery);
