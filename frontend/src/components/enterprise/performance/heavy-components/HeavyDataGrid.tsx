'use client';

import React from 'react';
import { Box, Typography, Paper } from '@mui/material';

/**
 * Placeholder Heavy Data Grid Component
 * This is a demo component for lazy loading examples
 */
const HeavyDataGrid: React.FC = () => {
  return (
    <Paper sx={{ p: 3, m: 2 }}>
      <Typography variant="h6" gutterBottom>
        Heavy Data Grid Component
      </Typography>
      <Typography variant="body2" color="text.secondary">
        This is a placeholder heavy data grid component that would normally contain
        complex data tables with sorting, filtering, and pagination.
      </Typography>
      <Box sx={{ mt: 2, height: 400, bgcolor: 'grey.100', borderRadius: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Typography variant="caption">
          Heavy data grid would appear here
        </Typography>
      </Box>
    </Paper>
  );
};

export default HeavyDataGrid;
