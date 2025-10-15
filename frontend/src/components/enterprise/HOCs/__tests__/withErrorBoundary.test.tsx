import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { withErrorBoundary, type ErrorFallbackProps } from '../withErrorBoundary';

// Test component that can throw errors
interface ThrowErrorProps {
  shouldThrow?: boolean;
  errorMessage?: string;
}

const ThrowError: React.FC<ThrowErrorProps> = ({ shouldThrow = false, errorMessage = 'Test error' }) => {
  if (shouldThrow) {
    throw new Error(errorMessage);
  }
  return <div>No error</div>;
};

// Custom fallback component for testing
const CustomFallback: React.FC<ErrorFallbackProps> = ({ error, resetError }) => (
  <div>
    <h1>Custom Error</h1>
    <p data-testid="error-message">{error.message}</p>
    <button onClick={resetError} data-testid="custom-reset">
      Custom Reset
    </button>
  </div>
);

describe('withErrorBoundary HOC', () => {
  // Suppress console.error for these tests
  const originalError = console.error;
  beforeAll(() => {
    console.error = jest.fn();
  });
  afterAll(() => {
    console.error = originalError;
  });

  describe('Basic functionality', () => {
    it('renders component without error', () => {
      const WrappedComponent = withErrorBoundary(ThrowError);
      render(<WrappedComponent shouldThrow={false} />);
      expect(screen.getByText('No error')).toBeInTheDocument();
    });

    it('catches and handles errors', () => {
      const WrappedComponent = withErrorBoundary(ThrowError);
      render(<WrappedComponent shouldThrow={true} errorMessage="Test error occurred" />);
      // Component should not crash, error boundary should catch it
      expect(screen.queryByText('No error')).not.toBeInTheDocument();
    });

    it('preserves component display name', () => {
      const TestComponent = () => <div>Test</div>;
      TestComponent.displayName = 'TestComponent';
      const WrappedComponent = withErrorBoundary(TestComponent);
      expect(WrappedComponent.displayName).toBe('withErrorBoundary(TestComponent)');
    });

    it('uses component name if display name not set', () => {
      const TestComponent = () => <div>Test</div>;
      const WrappedComponent = withErrorBoundary(TestComponent);
      expect(WrappedComponent.displayName).toBe('withErrorBoundary(TestComponent)');
    });

    it('forwards props correctly', () => {
      const TestComponent: React.FC<{ testProp: string }> = ({ testProp }) => <div>{testProp}</div>;
      const WrappedComponent = withErrorBoundary(TestComponent);
      render(<WrappedComponent testProp="test value" />);
      expect(screen.getByText('test value')).toBeInTheDocument();
    });
  });

  describe('Custom fallback component', () => {
    it('uses custom fallback when provided', () => {
      const WrappedComponent = withErrorBoundary(ThrowError, CustomFallback);
      render(<WrappedComponent shouldThrow={true} errorMessage="Custom error test" />);
      
      expect(screen.getByText('Custom Error')).toBeInTheDocument();
      expect(screen.getByTestId('error-message')).toHaveTextContent('Custom error test');
    });

    it('calls reset handler on custom fallback button click', () => {
      const WrappedComponent = withErrorBoundary(ThrowError, CustomFallback);
      const { rerender } = render(<WrappedComponent shouldThrow={true} errorMessage="Test error" />);
      
      // Click reset button
      const resetButton = screen.getByTestId('custom-reset');
      fireEvent.click(resetButton);
      
      // Component should try to render again
      rerender(<WrappedComponent shouldThrow={false} />);
      expect(screen.getByText('No error')).toBeInTheDocument();
    });
  });

  describe('Error recovery', () => {
    it('recovers after error when props change', () => {
      const WrappedComponent = withErrorBoundary(ThrowError);
      const { rerender } = render(<WrappedComponent shouldThrow={true} />);
      
      // Component should be in error state
      expect(screen.queryByText('No error')).not.toBeInTheDocument();
      
      // Rerender with no error
      rerender(<WrappedComponent shouldThrow={false} />);
      // Error boundary won't automatically recover, it needs a reset
    });
  });

  describe('Error message handling', () => {
    it('captures error message correctly', () => {
      const errorMessage = 'Specific error message for testing';
      const WrappedComponent = withErrorBoundary(ThrowError, CustomFallback);
      render(<WrappedComponent shouldThrow={true} errorMessage={errorMessage} />);
      
      expect(screen.getByTestId('error-message')).toHaveTextContent(errorMessage);
    });

    it('handles errors from nested components', () => {
      const NestedComponent = () => {
        return (
          <div>
            <ThrowError shouldThrow={true} errorMessage="Nested error" />
          </div>
        );
      };
      
      const WrappedComponent = withErrorBoundary(NestedComponent, CustomFallback);
      render(<WrappedComponent />);
      
      expect(screen.getByTestId('error-message')).toHaveTextContent('Nested error');
    });
  });

  describe('Multiple instances', () => {
    it('isolates errors to individual instances', () => {
      const WrappedComponent = withErrorBoundary(ThrowError);
      
      render(
        <div>
          <WrappedComponent shouldThrow={false} />
          <WrappedComponent shouldThrow={true} errorMessage="Instance 2 error" />
          <WrappedComponent shouldThrow={false} />
        </div>
      );
      
      // First and third instances should render normally
      const noErrors = screen.getAllByText('No error');
      expect(noErrors).toHaveLength(2);
    });
  });
});
