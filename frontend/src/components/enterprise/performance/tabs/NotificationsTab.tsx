'use client';

import React from 'react';
import { Box, Typography, Paper } from '@mui/material';

/**
 * Placeholder Notifications Tab Component
 * This is a demo component for code splitting examples
 */
const NotificationsTab: React.FC = () => {
  return (
    <Paper sx={{ p: 3, m: 2 }}>
      <Typography variant="h6" gutterBottom>
        Notifications Tab
      </Typography>
      <Typography variant="body2" color="text.secondary">
        This is a placeholder notifications tab component that would normally contain
        notification preferences and settings.
      </Typography>
      <Box sx={{ mt: 2, p: 2, bgcolor: 'grey.100', borderRadius: 1 }}>
        <Typography variant="caption">
          Notification settings would appear here
        </Typography>
      </Box>
    </Paper>
  );
};

export default NotificationsTab;
