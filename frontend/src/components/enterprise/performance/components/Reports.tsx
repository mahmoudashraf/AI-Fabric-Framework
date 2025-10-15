'use client';

import React from 'react';
import { Box, Typography, Paper } from '@mui/material';

/**
 * Placeholder Reports Component
 * This is a demo component for lazy loading examples
 */
export const Reports: React.FC = () => {
  return (
    <Paper sx={{ p: 3, m: 2 }}>
      <Typography variant="h6" gutterBottom>
        Reports Component
      </Typography>
      <Typography variant="body2" color="text.secondary">
        This is a placeholder reports component that would normally contain
        report generation and data export functionality.
      </Typography>
      <Box sx={{ mt: 2, p: 2, bgcolor: 'grey.100', borderRadius: 1 }}>
        <Typography variant="caption">
          Reports content would appear here
        </Typography>
      </Box>
    </Paper>
  );
};

export default Reports;
