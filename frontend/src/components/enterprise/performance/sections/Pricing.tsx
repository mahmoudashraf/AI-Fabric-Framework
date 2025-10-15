'use client';

import React from 'react';
import { Box, Typography, Paper } from '@mui/material';

/**
 * Placeholder Pricing Component
 * This is a demo component for code splitting examples
 */
const Pricing: React.FC = () => {
  return (
    <Paper sx={{ p: 3, m: 2 }}>
      <Typography variant="h6" gutterBottom>
        Pricing Section
      </Typography>
      <Typography variant="body2" color="text.secondary">
        This is a placeholder pricing component that would normally contain
        pricing plans, subscription options, and payment information.
      </Typography>
      <Box sx={{ mt: 2, p: 2, bgcolor: 'grey.100', borderRadius: 1 }}>
        <Typography variant="caption">
          Pricing content would appear here
        </Typography>
      </Box>
    </Paper>
  );
};

export default Pricing;
