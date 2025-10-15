'use client';

// PROJECT IMPORTS
import DashboardLayout from 'layout/MainLayout';
import AuthGuard from 'utils/route-guard/AuthGuard';
import { usePathname } from 'next/navigation';

// ==============================|| DASHBOARD LAYOUT ||============================== //

export default function Layout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  
  // Allow mock-users page to bypass authentication
  if (pathname === '/mock-users') {
    return (
      <DashboardLayout>{children}</DashboardLayout>
    );
  }
  
  return (
    <AuthGuard>
      <DashboardLayout>{children}</DashboardLayout>
    </AuthGuard>
  );
}
