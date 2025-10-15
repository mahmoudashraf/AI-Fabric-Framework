import React, { type ComponentType } from 'react';

export interface WithLoadingProps {
  loading?: boolean;
}

export function withLoading<P extends object>(Wrapped: ComponentType<P>) {
  const ComponentWithLoading = ({ loading, ...rest }: P & WithLoadingProps) => {
    if (loading) {
      return null;
    }
    return <Wrapped {...(rest as P)} />;
  };
  ComponentWithLoading.displayName = `withLoading(${Wrapped.displayName || Wrapped.name || 'Component'})`;
  return ComponentWithLoading;
}

