'use client';

import { useSnackbar } from 'notistack';
import React, { useEffect } from 'react';

interface IGlobalErrorHandlerProps {
  children: React.ReactNode;
}

const GlobalErrorHandler: React.FC<IGlobalErrorHandlerProps> = ({ children }) => {
  const { enqueueSnackbar } = useSnackbar();

  useEffect(() => {
    // Handle unhandled promise rejections
    const handleUnhandledRejection = (event: PromiseRejectionEvent) => {
      console.error('Unhandled promise rejection:', event.reason);

      const errorMessage = event.reason?.message || 'An unexpected error occurred';

      enqueueSnackbar(errorMessage, {
        variant: 'error',
        autoHideDuration: 5000,
        anchorOrigin: {
          vertical: 'top',
          horizontal: 'right',
        },
      });

      // Prevent the default browser behavior
      event.preventDefault();
    };

    // Handle uncaught errors
    const handleError = (event: ErrorEvent) => {
      console.error('Uncaught error:', event.error);

      const errorMessage = event.error?.message || 'An unexpected error occurred';

      enqueueSnackbar(errorMessage, {
        variant: 'error',
        autoHideDuration: 5000,
        anchorOrigin: {
          vertical: 'top',
          horizontal: 'right',
        },
      });
    };

    // Add event listeners
    window.addEventListener('unhandledrejection', handleUnhandledRejection);
    window.addEventListener('error', handleError);

    // Cleanup
    return () => {
      window.removeEventListener('unhandledrejection', handleUnhandledRejection);
      window.removeEventListener('error', handleError);
    };
  }, [enqueueSnackbar]);

  return <>{children}</>;
};

export default GlobalErrorHandler;
