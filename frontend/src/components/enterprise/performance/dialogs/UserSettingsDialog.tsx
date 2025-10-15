'use client';

import React from 'react';
import { Box, Typography, Paper } from '@mui/material';

/**
 * Placeholder User Settings Dialog Component
 * This is a demo component for code splitting examples
 */
const UserSettingsDialog: React.FC = () => {
  return (
    <Paper sx={{ p: 3, m: 2 }}>
      <Typography variant="h6" gutterBottom>
        User Settings Dialog
      </Typography>
      <Typography variant="body2" color="text.secondary">
        This is a placeholder user settings dialog component that would normally contain
        user preferences, account settings, and configuration options.
      </Typography>
      <Box sx={{ mt: 2, p: 2, bgcolor: 'grey.100', borderRadius: 1 }}>
        <Typography variant="caption">
          User settings form would appear here
        </Typography>
      </Box>
    </Paper>
  );
};

export default UserSettingsDialog;
