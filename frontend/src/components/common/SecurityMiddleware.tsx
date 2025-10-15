'use client';

import { useRouter } from 'next/navigation';
import React, { useEffect, useRef } from 'react';

interface ISecurityMiddlewareProps {
  children: React.ReactNode;
  requireAuth?: boolean;
  allowedRoles?: string[];
  redirectTo?: string;
}

const SecurityMiddleware: React.FC<ISecurityMiddlewareProps> = ({
  children,
  requireAuth = false,
  allowedRoles = [],
  redirectTo = '/login',
}) => {
  const router = useRouter();
  const hasChecked = useRef(false);

  useEffect(() => {
    if (hasChecked.current) return;
    hasChecked.current = true;

    // Check authentication status
    if (requireAuth) {
      const isAuthenticated = checkAuthentication();
      if (!isAuthenticated) {
        router.push(redirectTo);
        return;
      }
    }

    // Check role-based access
    if (allowedRoles.length > 0) {
      const userRole = getUserRole();
      if (!userRole || !allowedRoles.includes(userRole)) {
        router.push('/unauthorized');
        return;
      }
    }
  }, [requireAuth, allowedRoles, redirectTo, router]);

  // Mock functions - replace with actual authentication logic
  const checkAuthentication = (): boolean => {
    // Check if user is authenticated
    const token = localStorage.getItem('auth-token');
    return !!token;
  };

  const getUserRole = (): string | null => {
    // Get user role from token or API
    const userData = localStorage.getItem('user-data');
    if (userData) {
      try {
        const parsed = JSON.parse(userData);
        return parsed.role || null;
      } catch {
        return null;
      }
    }
    return null;
  };

  return <>{children}</>;
};

export default SecurityMiddleware;
