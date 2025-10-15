'use client';

import React from 'react';
import { Box, Typography, Paper } from '@mui/material';

/**
 * Placeholder Heavy Component
 * This is a demo component for code splitting examples
 */
const HeavyComponent: React.FC = () => {
  return (
    <Paper sx={{ p: 3, m: 2 }}>
      <Typography variant="h6" gutterBottom>
        Heavy Component
      </Typography>
      <Typography variant="body2" color="text.secondary">
        This is a placeholder heavy component that would normally contain complex logic,
        large data processing, or heavy rendering operations.
      </Typography>
      <Box sx={{ mt: 2, p: 2, bgcolor: 'grey.100', borderRadius: 1 }}>
        <Typography variant="caption">
          Simulated heavy content...
        </Typography>
      </Box>
    </Paper>
  );
};

export default HeavyComponent;
