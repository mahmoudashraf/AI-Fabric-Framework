'use client';

import React from 'react';
import { Box, Typography, Paper } from '@mui/material';

/**
 * Placeholder Chart Component
 * This is a demo component for code splitting examples
 */
const Chart: React.FC = () => {
  return (
    <Paper sx={{ p: 3, m: 2 }}>
      <Typography variant="h6" gutterBottom>
        Chart Component
      </Typography>
      <Typography variant="body2" color="text.secondary">
        This is a placeholder chart component that would normally contain
        complex charting libraries like Chart.js, D3.js, or ApexCharts.
      </Typography>
      <Box sx={{ mt: 2, height: 200, bgcolor: 'grey.100', borderRadius: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Typography variant="caption">
          Chart visualization would appear here
        </Typography>
      </Box>
    </Paper>
  );
};

export default Chart;
