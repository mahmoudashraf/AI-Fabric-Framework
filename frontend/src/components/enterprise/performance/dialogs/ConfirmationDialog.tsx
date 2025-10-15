'use client';

import React from 'react';
import { Box, Typography, Paper } from '@mui/material';

/**
 * Placeholder Confirmation Dialog Component
 * This is a demo component for code splitting examples
 */
const ConfirmationDialog: React.FC = () => {
  return (
    <Paper sx={{ p: 3, m: 2 }}>
      <Typography variant="h6" gutterBottom>
        Confirmation Dialog
      </Typography>
      <Typography variant="body2" color="text.secondary">
        This is a placeholder confirmation dialog component that would normally contain
        confirmation prompts and validation dialogs.
      </Typography>
      <Box sx={{ mt: 2, p: 2, bgcolor: 'grey.100', borderRadius: 1 }}>
        <Typography variant="caption">
          Confirmation dialog content would appear here
        </Typography>
      </Box>
    </Paper>
  );
};

export default ConfirmationDialog;
