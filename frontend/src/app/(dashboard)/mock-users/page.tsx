'use client';

import React from 'react';
import { Box, Typography, Alert } from '@mui/material';
import { authConfig } from '@/config/authConfig';

// Lazy load MockUserTester only in dev mode
const MockUserTester = React.lazy(() => import('@/components/testing/MockUserTester'));

/**
 * Mock Users Testing Page
 * This page is ONLY accessible in development mode with mock auth enabled
 */
export default function MockUsersPage() {
  // Check if mock auth is enabled
  if (!authConfig.shouldUseMockAuth()) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="warning">
          <Typography variant="h6" gutterBottom>
            Mock User Testing Not Available
          </Typography>
          <Typography variant="body2">
            This page is only available in development mode with mock authentication enabled.
            <br />
            Set NEXT_PUBLIC_MOCK_AUTH_ENABLED=true in your .env.development file to enable this feature.
          </Typography>
        </Alert>
      </Box>
    );
  }

  return (
    <React.Suspense fallback={
      <Box sx={{ p: 3 }}>
        <Typography>Loading Mock User Tester...</Typography>
      </Box>
    }>
      <MockUserTester />
    </React.Suspense>
  );
}
