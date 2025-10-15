"use client";
// project import
import MinimalLayout from 'layout/MinimalLayout';
import { withErrorBoundary } from '@/components/enterprise/HOCs';
import Landing from 'views/landing';

// ==============================|| HOME PAGE ||============================== //

function HomePage() {
  return (
    <MinimalLayout>
      <Landing />
    </MinimalLayout>
  );
}

export default withErrorBoundary(HomePage);
