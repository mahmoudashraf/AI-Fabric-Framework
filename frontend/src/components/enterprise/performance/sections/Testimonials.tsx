'use client';

import React from 'react';
import { Box, Typography, Paper } from '@mui/material';

/**
 * Placeholder Testimonials Component
 * This is a demo component for code splitting examples
 */
const Testimonials: React.FC = () => {
  return (
    <Paper sx={{ p: 3, m: 2 }}>
      <Typography variant="h6" gutterBottom>
        Testimonials Section
      </Typography>
      <Typography variant="body2" color="text.secondary">
        This is a placeholder testimonials component that would normally contain
        customer reviews, testimonials, and feedback.
      </Typography>
      <Box sx={{ mt: 2, p: 2, bgcolor: 'grey.100', borderRadius: 1 }}>
        <Typography variant="caption">
          Testimonials content would appear here
        </Typography>
      </Box>
    </Paper>
  );
};

export default Testimonials;
