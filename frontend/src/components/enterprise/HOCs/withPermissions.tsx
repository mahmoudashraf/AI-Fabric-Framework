import React, { type ComponentType } from 'react';

export interface WithPermissionsOptions {
  required?: string[];
  anyOf?: string[];
}

export interface WithPermissionsProps {
  permissions?: string[];
}

export function withPermissions<P extends object>(
  Wrapped: ComponentType<P>,
  options: WithPermissionsOptions = {}
) {
  const { required = [], anyOf = [] } = options;
  const ComponentWithPermissions = ({ permissions = [], ...rest }: P & WithPermissionsProps) => {
    const hasRequired = required.every((p) => permissions.includes(p));
    const hasAny = anyOf.length === 0 || anyOf.some((p) => permissions.includes(p));
    if (!hasRequired || !hasAny) {
      return null;
    }
    return <Wrapped {...(rest as P)} />;
  };
  ComponentWithPermissions.displayName = `withPermissions(${Wrapped.displayName || Wrapped.name || 'Component'})`;
  return ComponentWithPermissions;
}

