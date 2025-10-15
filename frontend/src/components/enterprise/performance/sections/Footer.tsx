'use client';

import React from 'react';
import { Box, Typography, Paper } from '@mui/material';

/**
 * Placeholder Footer Component
 * This is a demo component for code splitting examples
 */
const Footer: React.FC = () => {
  return (
    <Paper sx={{ p: 3, m: 2 }}>
      <Typography variant="h6" gutterBottom>
        Footer Section
      </Typography>
      <Typography variant="body2" color="text.secondary">
        This is a placeholder footer component that would normally contain
        site links, copyright information, and contact details.
      </Typography>
      <Box sx={{ mt: 2, p: 2, bgcolor: 'grey.100', borderRadius: 1 }}>
        <Typography variant="caption">
          Footer content would appear here
        </Typography>
      </Box>
    </Paper>
  );
};

export default Footer;
