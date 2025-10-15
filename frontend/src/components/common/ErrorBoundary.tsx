'use client';

import { ErrorOutline, Refresh } from '@mui/icons-material';
import { Box, Button, Typography, Paper, Alert } from '@mui/material';
import React, { Component, ErrorInfo, ReactNode } from 'react';

interface IErrorBoundaryProps {
  children: ReactNode;
  fallback?: React.ComponentType<IErrorFallbackProps>;
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
  resetOnPropsChange?: boolean;
  resetKeys?: Array<string | number>;
}

interface IErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
}

interface IErrorFallbackProps {
  error: Error;
  resetError: () => void;
}

class ErrorBoundary extends Component<IErrorBoundaryProps, IErrorBoundaryState> {
  private resetTimeoutId: number | null = null;

  constructor(props: IErrorBoundaryProps) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
    };
  }

  static getDerivedStateFromError(error: Error): Partial<IErrorBoundaryState> {
    return {
      hasError: true,
      error,
    };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    this.setState({
      error,
      errorInfo,
    });

    // Call the onError callback if provided
    if (this.props.onError) {
      this.props.onError(error, errorInfo);
    }

    // Log error to console in development
    if (process.env.NODE_ENV === 'development') {
      console.error('ErrorBoundary caught an error:', error, errorInfo);
    }
  }

  componentDidUpdate(prevProps: IErrorBoundaryProps): void {
    const { resetKeys, resetOnPropsChange } = this.props;
    const { hasError } = this.state;

    if (hasError && resetOnPropsChange && resetKeys) {
      const hasResetKeyChanged = resetKeys.some(
        (key, index) => key !== prevProps.resetKeys?.[index]
      );

      if (hasResetKeyChanged) {
        this.resetErrorBoundary();
      }
    }
  }

  componentWillUnmount(): void {
    if (this.resetTimeoutId) {
      clearTimeout(this.resetTimeoutId);
    }
  }

  resetErrorBoundary = (): void => {
    if (this.resetTimeoutId) {
      clearTimeout(this.resetTimeoutId);
    }

    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
    });
  };

  render(): ReactNode {
    const { hasError, error } = this.state;
    const { children, fallback: Fallback } = this.props;

    if (hasError && error) {
      if (Fallback) {
        return <Fallback error={error} resetError={this.resetErrorBoundary} />;
      }

      return <DefaultErrorFallback error={error} resetError={this.resetErrorBoundary} />;
    }

    return children;
  }
}

const DefaultErrorFallback: React.FC<IErrorFallbackProps> = ({ error, resetError }) => {
  const isDevelopment = process.env.NODE_ENV === 'development';

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '400px',
        p: 3,
      }}
    >
      <Paper
        elevation={3}
        sx={{
          p: 4,
          maxWidth: 600,
          width: '100%',
          textAlign: 'center',
        }}
      >
        <ErrorOutline
          sx={{
            fontSize: 64,
            color: 'error.main',
            mb: 2,
          }}
        />

        <Typography variant="h4" gutterBottom color="error">
          Oops! Something went wrong
        </Typography>

        <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
          We're sorry, but something unexpected happened. Please try refreshing the page or contact
          support if the problem persists.
        </Typography>

        {isDevelopment && (
          <Alert severity="error" sx={{ mb: 3, textAlign: 'left' }}>
            <Typography variant="subtitle2" gutterBottom>
              Development Error Details:
            </Typography>
            <Typography variant="body2" component="pre" sx={{ fontSize: '0.75rem' }}>
              {error.message}
            </Typography>
            {error.stack && (
              <Typography variant="body2" component="pre" sx={{ fontSize: '0.75rem', mt: 1 }}>
                {error.stack}
              </Typography>
            )}
          </Alert>
        )}

        <Box sx={{ display: 'flex', gap: 2, justifyContent: 'center' }}>
          <Button variant="contained" startIcon={<Refresh />} onClick={resetError} color="primary">
            Try Again
          </Button>

          <Button variant="outlined" onClick={() => window.location.reload()} color="secondary">
            Refresh Page
          </Button>
        </Box>
      </Paper>
    </Box>
  );
};

// Higher-order component for easier usage
export function withErrorBoundary<P extends object>(
  Component: React.ComponentType<P>,
  errorBoundaryProps?: Omit<IErrorBoundaryProps, 'children'>
) {
  const WrappedComponent = (props: P) => (
    <ErrorBoundary {...errorBoundaryProps}>
      <Component {...props} />
    </ErrorBoundary>
  );

  WrappedComponent.displayName = `withErrorBoundary(${Component.displayName || Component.name})`;

  return WrappedComponent;
}

// Hook for functional components to trigger error boundary
export function useErrorHandler() {
  return (error: Error, _errorInfo?: ErrorInfo) => {
    throw error;
  };
}

export default ErrorBoundary;
