'use client';

import React from 'react';
import { Box, Typography, Paper, Stack } from '@mui/material';
import SocialLoginButtons from '@/components/authentication/auth-forms/SocialLoginButtons';

/**
 * Social Login Test Component
 * This component can be used to test social login functionality
 * Add this to any page temporarily to test the OAuth buttons
 */
const SocialLoginTest: React.FC = () => {
  const handleError = (error: string) => {
    console.error('Social login error:', error);
    alert(`Social login error: ${error}`);
  };

  const handleSuccess = () => {
    console.log('Social login success');
    alert('Social login initiated successfully!');
  };

  return (
    <Box sx={{ p: 3, maxWidth: 400, mx: 'auto', mt: 4 }}>
      <Paper sx={{ p: 3 }}>
        <Typography variant="h5" gutterBottom align="center">
          Social Login Test
        </Typography>
        <Typography variant="body2" color="textSecondary" align="center" sx={{ mb: 3 }}>
          Test the social login buttons below. Make sure Supabase OAuth providers are configured.
        </Typography>
        
        <Stack spacing={2}>
          <SocialLoginButtons
            onError={handleError}
            onSuccess={handleSuccess}
          />
        </Stack>

        <Box sx={{ mt: 3, p: 2, bgcolor: 'grey.100', borderRadius: 1 }}>
          <Typography variant="caption" color="textSecondary">
            <strong>Note:</strong> This is a test component. In production, these buttons 
            would be integrated into the main login form.
          </Typography>
        </Box>
      </Paper>
    </Box>
  );
};

export default SocialLoginTest;
