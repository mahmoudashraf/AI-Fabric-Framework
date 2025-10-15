'use client';

import React from 'react';
import { Box, Typography, Paper } from '@mui/material';

/**
 * Placeholder Heavy Chart Component
 * This is a demo component for lazy loading examples
 */
const HeavyChart: React.FC = () => {
  return (
    <Paper sx={{ p: 3, m: 2 }}>
      <Typography variant="h6" gutterBottom>
        Heavy Chart Component
      </Typography>
      <Typography variant="body2" color="text.secondary">
        This is a placeholder heavy chart component that would normally contain
        complex charting libraries and large datasets.
      </Typography>
      <Box sx={{ mt: 2, height: 300, bgcolor: 'grey.100', borderRadius: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Typography variant="caption">
          Heavy chart visualization would appear here
        </Typography>
      </Box>
    </Paper>
  );
};

export default HeavyChart;
