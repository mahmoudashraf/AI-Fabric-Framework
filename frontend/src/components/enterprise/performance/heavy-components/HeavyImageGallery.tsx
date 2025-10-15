'use client';

import React from 'react';
import { Box, Typography, Paper } from '@mui/material';

/**
 * Placeholder Heavy Image Gallery Component
 * This is a demo component for lazy loading examples
 */
const HeavyImageGallery: React.FC = () => {
  return (
    <Paper sx={{ p: 3, m: 2 }}>
      <Typography variant="h6" gutterBottom>
        Heavy Image Gallery Component
      </Typography>
      <Typography variant="body2" color="text.secondary">
        This is a placeholder heavy image gallery component that would normally contain
        large image collections with lazy loading and optimization.
      </Typography>
      <Box sx={{ mt: 2, height: 300, bgcolor: 'grey.100', borderRadius: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Typography variant="caption">
          Heavy image gallery would appear here
        </Typography>
      </Box>
    </Paper>
  );
};

export default HeavyImageGallery;
