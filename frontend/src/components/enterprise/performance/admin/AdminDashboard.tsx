'use client';

import React from 'react';
import { Box, Typography, Paper } from '@mui/material';

/**
 * Placeholder Admin Dashboard Component
 * This is a demo component for code splitting examples
 */
const AdminDashboard: React.FC = () => {
  return (
    <Paper sx={{ p: 3, m: 2 }}>
      <Typography variant="h6" gutterBottom>
        Admin Dashboard
      </Typography>
      <Typography variant="body2" color="text.secondary">
        This is a placeholder admin dashboard component that would normally contain
        administrative controls, user management, and system settings.
      </Typography>
      <Box sx={{ mt: 2, p: 2, bgcolor: 'grey.100', borderRadius: 1 }}>
        <Typography variant="caption">
          Admin dashboard content would appear here
        </Typography>
      </Box>
    </Paper>
  );
};

export default AdminDashboard;
