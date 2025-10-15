'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

// project imports
import Loader from 'components/ui-component/Loader';
import { DASHBOARD_PATH } from 'config';
import useAuth from 'hooks/useAuth';

// types
import { GuardProps } from 'types';

// ==============================|| GUEST GUARD ||============================== //

/**
 * Guest guard for routes having no auth required
 * @param {PropTypes.node} children children element/node
 */

const GuestGuard = ({ children }: GuardProps) => {
  const { isLoggedIn } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (isLoggedIn) {
      router.push(DASHBOARD_PATH);
    }
    // eslint-disable-next-line
  }, [isLoggedIn]);

  if (isLoggedIn) return <Loader />;

  return children;
};

export default GuestGuard;
