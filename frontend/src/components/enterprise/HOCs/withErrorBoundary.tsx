import React, { Component, type ComponentType, type ErrorInfo, type ReactNode, useMemo } from 'react';

export interface ErrorFallbackProps {
  error: Error;
  resetError: () => void;
}

type ErrorBoundaryProps = {
  children?: ReactNode;
  onReset?: () => void;
  FallbackComponent?: ComponentType<ErrorFallbackProps>;
};

type ErrorBoundaryState = { hasError: boolean; error: Error | null };

class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false, error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(_error: Error, _info: ErrorInfo) {}

  componentDidUpdate(prevProps: ErrorBoundaryProps) {
    // If we are in an error state and the children change (e.g., props updated to not throw),
    // reset the boundary so it can attempt to render children again.
    if (this.state.hasError && prevProps.children !== this.props.children) {
      // eslint-disable-next-line react/no-did-update-set-state
      this.setState({ hasError: false, error: null });
    }
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null });
    this.props.onReset?.();
  };

  render() {
    const { hasError, error } = this.state;
    const { FallbackComponent, children } = this.props;
    if (hasError && error) {
      if (FallbackComponent) {
        return <FallbackComponent error={error} resetError={this.handleReset} />;
      }
      return null;
    }
    return children as React.ReactElement;
  }
}

export function withErrorBoundary<P extends object>(Wrapped: ComponentType<P>, Fallback?: ComponentType<ErrorFallbackProps>) {
  const ComponentWithBoundary = (props: P) => {
    const memoizedProps = useMemo(() => props, [props]);
    const boundaryProps = Fallback ? { FallbackComponent: Fallback } : {};
    return (
      <ErrorBoundary {...boundaryProps}>
        <Wrapped {...(memoizedProps as P)} />
      </ErrorBoundary>
    );
  };
  ComponentWithBoundary.displayName = `withErrorBoundary(${Wrapped.displayName || Wrapped.name || 'Component'})`;
  return ComponentWithBoundary;
}

