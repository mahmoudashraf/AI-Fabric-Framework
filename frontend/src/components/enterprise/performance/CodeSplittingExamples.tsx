'use client';

import React, { Suspense } from 'react';
import dynamic from 'next/dynamic';
import { Box, Typography, CircularProgress } from '@mui/material';

/**
 * PHASE 6: Performance Optimization - Code Splitting Examples
 * 
 * This file demonstrates various code splitting strategies:
 * 1. Route-based code splitting
 * 2. Component-based code splitting
 * 3. Library code splitting
 * 4. Dynamic imports with Next.js
 */

// ==================== EXAMPLE 1: Next.js Dynamic Imports ====================

/**
 * Using Next.js dynamic() for automatic code splitting
 * This is the preferred method for Next.js applications
 */

// Basic dynamic import with loading
const DynamicComponent = dynamic(() => import('./heavy-components/HeavyComponent'), {
  loading: () => <CircularProgress />,
  ssr: false, // Disable SSR if needed
});

// Dynamic import with custom loading
const DynamicChart = dynamic(() => import('./heavy-components/Chart'), {
  loading: () => (
    <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}>
      <CircularProgress />
      <Typography sx={{ ml: 2 }}>Loading chart...</Typography>
    </Box>
  ),
});

// ==================== EXAMPLE 2: Route-Based Code Splitting ====================

/**
 * Split code by routes for optimal initial load
 * Each route loads only when navigated to
 */
export const routeComponents = {
  // Dashboard routes - loaded on demand
  dashboard: dynamic(() => import('@/views/dashboard/default')),
  analytics: dynamic(() => import('@/views/dashboard/analytics')),
  
  // E-commerce routes
  productList: dynamic(() => import('@/views/apps/e-commerce/product-list')),
  productDetails: dynamic(() => import('@/views/apps/e-commerce/product-details')),
  checkout: dynamic(() => import('@/views/apps/e-commerce/checkout')),
  
  // Customer management
  customerList: dynamic(() => import('@/views/apps/customer/customer-list')),
  orderList: dynamic(() => import('@/views/apps/customer/order-list')),
  
  // User profile
  socialProfile: dynamic(() => import('@/views/apps/user/social-profile')),
  
  // Forms
  formValidation: dynamic(() => import('@/views/forms/forms-validation')),
  formWizard: dynamic(() => import('@/views/forms/forms-wizard')),
  
  // With loading states
  kanban: dynamic(() => import('@/views/apps/kanban/board'), {
    loading: () => <CircularProgress />,
  }),
};

// ==================== EXAMPLE 3: Feature-Based Code Splitting ====================

/**
 * Split by feature modules
 * Large features can be loaded on demand
 */

// Calendar feature - only loads when needed
export const CalendarFeature = dynamic(() => import('@/views/apps/calendar'), {
  loading: () => (
    <Box sx={{ p: 3, textAlign: 'center' }}>
      <CircularProgress />
      <Typography>Loading calendar...</Typography>
    </Box>
  ),
});

// Chat feature - only loads when needed
export const ChatFeature = dynamic(() => import('@/views/apps/chat'), {
  loading: () => <CircularProgress />,
  ssr: false, // Chat doesn't need SSR
});

// Mail feature - only loads when needed
export const MailFeature = dynamic(() => import('@/views/apps/mail'), {
  loading: () => <CircularProgress />,
  ssr: false,
});

// ==================== EXAMPLE 4: Library Code Splitting ====================

/**
 * Split heavy third-party libraries
 * Load them only when components that use them are rendered
 */

// Heavy chart library - only load when chart is shown
export const LazyChartComponent = dynamic(
  () =>
    import('react-apexcharts').then((mod) => {
      // Return a wrapper component
      return {
        default: ({ options, series, type }: any) => {
          const Chart = mod.default;
          return <Chart options={options} series={series} type={type} />;
        },
      };
    }),
  { ssr: false }
);

// Date picker with heavy localization - only load when needed
export const DatePickerWithLocale = dynamic(
  async () => {
    // Dynamically import both the library and locale
    const [DatePicker] = await Promise.all([
      import('@mui/x-date-pickers'),
      import('@mui/x-date-pickers/locales'),
    ]);
    
    return {
      default: DatePicker.DatePicker,
    };
  },
  { ssr: false }
);

// ==================== EXAMPLE 5: Modal/Dialog Code Splitting ====================

/**
 * Modals and dialogs are perfect candidates for code splitting
 * They're not needed until user triggers them
 */

// Product edit modal - only loads when opened
export const ProductEditModal = dynamic(
  () => import('./modals/ProductEditModal'),
  { ssr: false }
);

// User settings dialog - only loads when opened
export const UserSettingsDialog = dynamic(
  () => import('./dialogs/UserSettingsDialog'),
  { ssr: false }
);

// Confirmation dialog with heavy form validation
export const ConfirmationDialog = dynamic(
  () => import('./dialogs/ConfirmationDialog'),
  { ssr: false }
);

// ==================== EXAMPLE 6: Tab-Based Code Splitting ====================

/**
 * Split content by tabs
 * Only load tab content when the tab is active
 */
interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

const TabPanel = ({ children, value, index }: TabPanelProps) => (
  <div hidden={value !== index}>
    {value === index && <Box sx={{ p: 3 }}>{children}</Box>}
  </div>
);

// Lazy load tab content
export const ProfileTab = dynamic(() => import('./tabs/ProfileTab'));
export const SecurityTab = dynamic(() => import('./tabs/SecurityTab'));
export const BillingTab = dynamic(() => import('./tabs/BillingTab'));
export const NotificationsTab = dynamic(() => import('./tabs/NotificationsTab'));

export const TabbedInterface = ({ activeTab }: { activeTab: number }) => (
  <Box>
    <TabPanel value={activeTab} index={0}>
      <Suspense fallback={<CircularProgress />}>
        <ProfileTab />
      </Suspense>
    </TabPanel>
    
    <TabPanel value={activeTab} index={1}>
      <Suspense fallback={<CircularProgress />}>
        <SecurityTab />
      </Suspense>
    </TabPanel>
    
    <TabPanel value={activeTab} index={2}>
      <Suspense fallback={<CircularProgress />}>
        <BillingTab />
      </Suspense>
    </TabPanel>
    
    <TabPanel value={activeTab} index={3}>
      <Suspense fallback={<CircularProgress />}>
        <NotificationsTab />
      </Suspense>
    </TabPanel>
  </Box>
);

// ==================== EXAMPLE 7: Viewport-Based Code Splitting ====================

/**
 * Load components when they enter the viewport
 * Useful for below-the-fold content
 */

export const LazyFooter = dynamic(() => import('./sections/Footer'), {
  loading: () => null, // No loading indicator needed
});

export const LazyTestimonials = dynamic(() => import('./sections/Testimonials'), {
  loading: () => <Box sx={{ minHeight: 300 }} />, // Preserve layout
});

export const LazyPricing = dynamic(() => import('./sections/Pricing'), {
  loading: () => <Box sx={{ minHeight: 400 }} />,
});

// ==================== EXAMPLE 8: Conditional Code Splitting ====================

/**
 * Load components based on conditions
 */

interface ConditionalFeatureProps {
  userRole: 'admin' | 'user' | 'guest';
  feature: string;
}

export const ConditionalFeature = ({ userRole }: ConditionalFeatureProps) => {
  // Admin dashboard - only loads for admins
  const AdminDashboard = userRole === 'admin' 
    ? dynamic(() => import('./admin/AdminDashboard'))
    : null;

  // Analytics - loads for admins and users, not guests
  const Analytics = userRole !== 'guest'
    ? dynamic(() => import('./analytics/Analytics'))
    : null;

  return (
    <Box>
      {AdminDashboard && (
        <Suspense fallback={<CircularProgress />}>
          <AdminDashboard />
        </Suspense>
      )}
      
      {Analytics && (
        <Suspense fallback={<CircularProgress />}>
          <Analytics />
        </Suspense>
      )}
    </Box>
  );
};

// ==================== BEST PRACTICES ====================

/**
 * CODE SPLITTING BEST PRACTICES:
 * 
 * 1. Route-level splitting:
 *    - Always split by routes/pages
 *    - Each route should be its own chunk
 *    - Use Next.js dynamic() for automatic optimization
 * 
 * 2. Component-level splitting:
 *    - Split large components (> 50KB)
 *    - Split components below the fold
 *    - Split modals, dialogs, and overlays
 *    - Split tab content
 * 
 * 3. Library splitting:
 *    - Heavy charting libraries
 *    - Rich text editors
 *    - PDF viewers
 *    - Date/time pickers with localization
 * 
 * 4. Loading states:
 *    - Always provide loading fallbacks
 *    - Use skeleton loaders for better UX
 *    - Preserve layout to prevent content shift
 * 
 * 5. SSR considerations:
 *    - Disable SSR for client-only libraries
 *    - Use ssr: false for dynamic imports when needed
 * 
 * 6. Bundle analysis:
 *    - Regularly analyze bundle sizes
 *    - Identify large chunks
 *    - Monitor chunk loading times
 * 
 * 7. Preloading:
 *    - Preload critical chunks
 *    - Use predictive preloading
 *    - Preload on hover/focus for better UX
 * 
 * 8. Chunk naming:
 *    - Use meaningful chunk names
 *    - Group related chunks
 *    - Configure webpack chunk names
 */

// ==================== BUNDLE ANALYSIS HELPERS ====================

/**
 * Helper to analyze when components load
 * Useful for debugging and optimization
 */
export const withLoadAnalytics = (
  componentName: string,
  importFunc: () => Promise<any>
) => {
  return dynamic(
    () =>
      importFunc().then((mod) => {
        console.log(`[Bundle] Loaded: ${componentName}`);
        return mod;
      }),
    {
      loading: () => {
        console.log(`[Bundle] Loading: ${componentName}...`);
        return <CircularProgress />;
      },
    }
  );
};

// Usage:
// const AnalyticsWithLogging = withLoadAnalytics(
//   'Analytics',
//   () => import('./Analytics')
// );

export default {
  DynamicComponent,
  DynamicChart,
  routeComponents,
  TabbedInterface,
  ConditionalFeature,
  withLoadAnalytics,
  CalendarFeature,
  ChatFeature,
  MailFeature,
  LazyChartComponent,
  DatePickerWithLocale,
  ProductEditModal,
  UserSettingsDialog,
  ConfirmationDialog,
  ProfileTab,
  SecurityTab,
  BillingTab,
  NotificationsTab,
  LazyFooter,
  LazyTestimonials,
  LazyPricing,
};
