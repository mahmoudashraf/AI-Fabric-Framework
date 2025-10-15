import React from 'react';
import { Alert, Box, Button, Typography } from '@mui/material';
import { ErrorFallbackProps } from './withErrorBoundary';

export const ErrorFallback: React.FC<ErrorFallbackProps> = ({ error, resetError }) => {
  return (
    <Box
      sx={{
        p: 3,
        textAlign: 'center',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '400px'
      }}
    >
      <Alert role="alert" aria-label="Error" severity="error" sx={{ mb: 2, maxWidth: '600px' }}>
        <Typography variant="h6" gutterBottom>
          Something went wrong
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {error.message || 'An unexpected error occurred'}
        </Typography>
      </Alert>
      <Button variant="contained" onClick={resetError}>
        Try Again
      </Button>
    </Box>
  );
};
