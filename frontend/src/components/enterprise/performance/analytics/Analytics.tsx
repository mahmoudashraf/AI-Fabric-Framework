'use client';

import React from 'react';
import { Box, Typography, Paper } from '@mui/material';

/**
 * Placeholder Analytics Component
 * This is a demo component for code splitting examples
 */
const Analytics: React.FC = () => {
  return (
    <Paper sx={{ p: 3, m: 2 }}>
      <Typography variant="h6" gutterBottom>
        Analytics Dashboard
      </Typography>
      <Typography variant="body2" color="text.secondary">
        This is a placeholder analytics component that would normally contain
        data visualization, metrics, and reporting functionality.
      </Typography>
      <Box sx={{ mt: 2, p: 2, bgcolor: 'grey.100', borderRadius: 1 }}>
        <Typography variant="caption">
          Analytics content would appear here
        </Typography>
      </Box>
    </Paper>
  );
};

export default Analytics;
