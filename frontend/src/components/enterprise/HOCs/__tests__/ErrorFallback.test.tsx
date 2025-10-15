import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { ErrorFallback } from '../ErrorFallback';

const theme = createTheme();

const renderWithTheme = (component: React.ReactElement) => {
  return render(<ThemeProvider theme={theme}>{component}</ThemeProvider>);
};

describe('ErrorFallback Component', () => {
  const mockResetError = jest.fn();
  const testError = new Error('Test error message');

  beforeEach(() => {
    mockResetError.mockClear();
  });

  describe('Rendering', () => {
    it('renders error message', () => {
      renderWithTheme(<ErrorFallback error={testError} resetError={mockResetError} />);
      
      expect(screen.getByText('Something went wrong')).toBeInTheDocument();
      expect(screen.getByText('Test error message')).toBeInTheDocument();
    });

    it('renders Try Again button', () => {
      renderWithTheme(<ErrorFallback error={testError} resetError={mockResetError} />);
      
      expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
    });

    it('displays default message when error has no message', () => {
      const errorWithoutMessage = {} as Error;
      renderWithTheme(<ErrorFallback error={errorWithoutMessage} resetError={mockResetError} />);
      
      expect(screen.getByText('An unexpected error occurred')).toBeInTheDocument();
    });
  });

  describe('User interaction', () => {
    it('calls resetError when Try Again button is clicked', () => {
      renderWithTheme(<ErrorFallback error={testError} resetError={mockResetError} />);
      
      const button = screen.getByRole('button', { name: /try again/i });
      fireEvent.click(button);
      
      expect(mockResetError).toHaveBeenCalledTimes(1);
    });

    it('can be clicked multiple times', () => {
      renderWithTheme(<ErrorFallback error={testError} resetError={mockResetError} />);
      
      const button = screen.getByRole('button', { name: /try again/i });
      fireEvent.click(button);
      fireEvent.click(button);
      fireEvent.click(button);
      
      expect(mockResetError).toHaveBeenCalledTimes(3);
    });
  });

  describe('Styling and layout', () => {
    it('applies correct styling classes', () => {
      const { container } = renderWithTheme(
        <ErrorFallback error={testError} resetError={mockResetError} />
      );
      
      // Check that Box component is rendered
      const box = container.querySelector('[class*="MuiBox"]');
      expect(box).toBeInTheDocument();
    });

    it('renders Alert component with error severity', () => {
      renderWithTheme(<ErrorFallback error={testError} resetError={mockResetError} />);
      
      const alert = screen.getByRole('alert');
      expect(alert).toBeInTheDocument();
    });

    it('renders Typography components', () => {
      renderWithTheme(<ErrorFallback error={testError} resetError={mockResetError} />);
      
      // Check for heading
      expect(screen.getByText('Something went wrong')).toBeInTheDocument();
      // Check for error message
      expect(screen.getByText('Test error message')).toBeInTheDocument();
    });
  });

  describe('Different error types', () => {
    it('handles Error objects', () => {
      const error = new Error('Standard error');
      renderWithTheme(<ErrorFallback error={error} resetError={mockResetError} />);
      
      expect(screen.getByText('Standard error')).toBeInTheDocument();
    });

    it('handles errors with multiline messages', () => {
      const multilineError = new Error('Line 1\nLine 2\nLine 3');
      renderWithTheme(<ErrorFallback error={multilineError} resetError={mockResetError} />);
      
      expect(screen.getByText(/Line 1/)).toBeInTheDocument();
    });

    it('handles errors with special characters', () => {
      const specialError = new Error('Error with <special> & "characters"');
      renderWithTheme(<ErrorFallback error={specialError} resetError={mockResetError} />);
      
      expect(screen.getByText(/Error with <special> & "characters"/)).toBeInTheDocument();
    });

    it('handles errors with long messages', () => {
      const longMessage = 'A'.repeat(500);
      const longError = new Error(longMessage);
      renderWithTheme(<ErrorFallback error={longError} resetError={mockResetError} />);
      
      expect(screen.getByText(longMessage)).toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('has accessible button', () => {
      renderWithTheme(<ErrorFallback error={testError} resetError={mockResetError} />);
      
      const button = screen.getByRole('button', { name: /try again/i });
      expect(button).toBeEnabled();
    });

    it('alert has proper role', () => {
      renderWithTheme(<ErrorFallback error={testError} resetError={mockResetError} />);
      
      const alert = screen.getByRole('alert');
      expect(alert).toHaveAccessibleName();
    });
  });

  describe('Component updates', () => {
    it('updates when error message changes', () => {
      const { rerender } = renderWithTheme(
        <ErrorFallback error={testError} resetError={mockResetError} />
      );
      
      expect(screen.getByText('Test error message')).toBeInTheDocument();
      
      const newError = new Error('Updated error message');
      rerender(
        <ThemeProvider theme={theme}>
          <ErrorFallback error={newError} resetError={mockResetError} />
        </ThemeProvider>
      );
      
      expect(screen.getByText('Updated error message')).toBeInTheDocument();
      expect(screen.queryByText('Test error message')).not.toBeInTheDocument();
    });

    it('updates when resetError handler changes', () => {
      const newResetError = jest.fn();
      const { rerender } = renderWithTheme(
        <ErrorFallback error={testError} resetError={mockResetError} />
      );
      
      const button = screen.getByRole('button', { name: /try again/i });
      fireEvent.click(button);
      expect(mockResetError).toHaveBeenCalled();
      
      rerender(
        <ThemeProvider theme={theme}>
          <ErrorFallback error={testError} resetError={newResetError} />
        </ThemeProvider>
      );
      
      fireEvent.click(button);
      expect(newResetError).toHaveBeenCalled();
    });
  });
});
