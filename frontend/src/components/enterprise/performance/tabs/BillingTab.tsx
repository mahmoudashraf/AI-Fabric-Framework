'use client';

import React from 'react';
import { Box, Typography, Paper } from '@mui/material';

/**
 * Placeholder Billing Tab Component
 * This is a demo component for code splitting examples
 */
const BillingTab: React.FC = () => {
  return (
    <Paper sx={{ p: 3, m: 2 }}>
      <Typography variant="h6" gutterBottom>
        Billing Tab
      </Typography>
      <Typography variant="body2" color="text.secondary">
        This is a placeholder billing tab component that would normally contain
        billing information, payment methods, and subscription details.
      </Typography>
      <Box sx={{ mt: 2, p: 2, bgcolor: 'grey.100', borderRadius: 1 }}>
        <Typography variant="caption">
          Billing information would appear here
        </Typography>
      </Box>
    </Paper>
  );
};

export default BillingTab;
