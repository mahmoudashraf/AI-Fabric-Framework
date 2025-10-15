'use client';

import React from 'react';
import { Box, Typography, Paper } from '@mui/material';

/**
 * Placeholder Security Tab Component
 * This is a demo component for code splitting examples
 */
const SecurityTab: React.FC = () => {
  return (
    <Paper sx={{ p: 3, m: 2 }}>
      <Typography variant="h6" gutterBottom>
        Security Tab
      </Typography>
      <Typography variant="body2" color="text.secondary">
        This is a placeholder security tab component that would normally contain
        security settings, password management, and authentication options.
      </Typography>
      <Box sx={{ mt: 2, p: 2, bgcolor: 'grey.100', borderRadius: 1 }}>
        <Typography variant="caption">
          Security settings would appear here
        </Typography>
      </Box>
    </Paper>
  );
};

export default SecurityTab;
