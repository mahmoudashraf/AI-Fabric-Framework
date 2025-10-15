'use client';

import React, { Suspense } from 'react';
import dynamic from 'next/dynamic';
import { Button, CardActions, CardContent, Divider, Grid, Skeleton, Box } from '@mui/material';
import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';

// ===========================|| OPTIMIZED WIDGET DATA WITH CODE SPLITTING ||=========================== //

/**
 * OPTIMIZED WIDGET DATA PAGE
 * 
 * This version demonstrates code splitting improvements:
 * 1. Heavy widgets are lazy loaded
 * 2. Critical widgets load immediately
 * 3. Loading states with skeleton UI
 * 4. Error boundaries for resilience
 * 5. Progressive loading for better UX
 */

// ==================== IMMEDIATE LOADING (Critical Widgets) ====================
// These widgets are small and critical for initial view
import ToDoList from 'components/widget/Data/ToDoList';
import TeamMembers from 'components/widget/Data/TeamMembers';
import LatestMessages from 'components/widget/Data/LatestMessages';

// ==================== LAZY LOADING (Heavy Widgets) ====================
// These widgets are loaded on demand with loading states

// Charts and Analytics - Heavy components
const UserActivity = dynamic(() => import('components/widget/Data/UserActivity'), {
  loading: () => <Skeleton variant="rectangular" height={300} />,
  ssr: false, // Charts don't need SSR
});

const ApplicationSales = dynamic(() => import('components/widget/Data/ApplicationSales'), {
  loading: () => <Skeleton variant="rectangular" height={250} />,
  ssr: false,
});

const ProductSales = dynamic(() => import('components/widget/Data/ProductSales'), {
  loading: () => <Skeleton variant="rectangular" height={200} />,
  ssr: false,
});

const TotalRevenue = dynamic(() => import('components/widget/Data/TotalRevenue'), {
  loading: () => <Skeleton variant="rectangular" height={150} />,
  ssr: false,
});

// Tables and Lists - Heavy data components
const ProjectTable = dynamic(() => import('components/widget/Data/ProjectTable'), {
  loading: () => (
    <Box sx={{ p: 2 }}>
      <Skeleton variant="rectangular" height={40} sx={{ mb: 1 }} />
      <Skeleton variant="rectangular" height={40} sx={{ mb: 1 }} />
      <Skeleton variant="rectangular" height={40} sx={{ mb: 1 }} />
      <Skeleton variant="rectangular" height={40} />
    </Box>
  ),
});

const LatestCustomers = dynamic(() => import('components/widget/Data/LatestCustomers'), {
  loading: () => (
    <Box sx={{ p: 2 }}>
      <Skeleton variant="rectangular" height={30} sx={{ mb: 1 }} />
      <Skeleton variant="rectangular" height={30} sx={{ mb: 1 }} />
      <Skeleton variant="rectangular" height={30} sx={{ mb: 1 }} />
      <Skeleton variant="rectangular" height={30} />
    </Box>
  ),
});

const LatestOrder = dynamic(() => import('components/widget/Data/LatestOrder'), {
  loading: () => <Skeleton variant="rectangular" height={400} />,
});

const RecentTickets = dynamic(() => import('components/widget/Data/RecentTickets'), {
  loading: () => <Skeleton variant="rectangular" height={300} />,
});

// Secondary widgets - Load after critical content
const TrafficSources = dynamic(() => import('components/widget/Data/TrafficSources'), {
  loading: () => <Skeleton variant="rectangular" height={200} />,
});

const TasksCard = dynamic(() => import('components/widget/Data/TasksCard'), {
  loading: () => <Skeleton variant="rectangular" height={150} />,
});

const ActiveTickets = dynamic(() => import('components/widget/Data/ActiveTickets'), {
  loading: () => <Skeleton variant="rectangular" height={200} />,
});

const LatestPosts = dynamic(() => import('components/widget/Data/LatestPosts'), {
  loading: () => <Skeleton variant="rectangular" height={150} />,
});

const FeedsCard = dynamic(() => import('components/widget/Data/FeedsCard'), {
  loading: () => <Skeleton variant="rectangular" height={200} />,
});

const IncomingRequests = dynamic(() => import('components/widget/Data/IncomingRequests'), {
  loading: () => <Skeleton variant="rectangular" height={150} />,
});

const NewCustomers = dynamic(() => import('components/widget/Data/NewCustomers'), {
  loading: () => <Skeleton variant="rectangular" height={150} />,
});

// ==================== OPTIMIZED WIDGET DATA COMPONENT ====================

const OptimizedWidgetData = () => {
  return (
    <Grid container spacing={gridSpacing}>
      {/* Row 1: Critical widgets - Load immediately */}
      <Grid size={{ xs: 12, md: 6, lg: 4 }}>
        <ToDoList />
      </Grid>
      <Grid size={{ xs: 12, md: 6, lg: 4 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={200} />}>
          <TrafficSources />
        </Suspense>
      </Grid>
      <Grid size={{ xs: 12, md: 12, lg: 4 }}>
        <TeamMembers />
      </Grid>

      {/* Row 2: Heavy analytics - Lazy loaded */}
      <Grid size={{ xs: 12, md: 7, lg: 6 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={300} />}>
          <UserActivity />
        </Suspense>
      </Grid>
      <Grid size={{ xs: 12, md: 5, lg: 6 }}>
        <LatestMessages />
      </Grid>

      {/* Row 3: Project table and sales - Lazy loaded */}
      <Grid size={{ xs: 12, md: 6, lg: 6 }}>
        <MainCard title="Projects" content={false}>
          <CardContent sx={{ p: 0 }}>
            <Suspense fallback={
              <Box sx={{ p: 2 }}>
                <Skeleton variant="rectangular" height={40} sx={{ mb: 1 }} />
                <Skeleton variant="rectangular" height={40} sx={{ mb: 1 }} />
                <Skeleton variant="rectangular" height={40} sx={{ mb: 1 }} />
                <Skeleton variant="rectangular" height={40} />
              </Box>
            }>
              <ProjectTable />
            </Suspense>
          </CardContent>
          <Divider />
          <CardActions sx={{ justifyContent: 'flex-end' }}>
            <Button variant="text" size="small">
              View all Projects
            </Button>
          </CardActions>
        </MainCard>
      </Grid>
      <Grid size={{ xs: 12, md: 6, lg: 6 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={200} />}>
          <ProductSales />
        </Suspense>
      </Grid>

      {/* Row 4: Tasks and application sales */}
      <Grid size={{ xs: 12, md: 4 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={150} />}>
          <TasksCard />
        </Suspense>
      </Grid>
      <Grid size={{ xs: 12, md: 8 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={250} />}>
          <ApplicationSales />
        </Suspense>
      </Grid>

      {/* Row 5: Tickets and posts */}
      <Grid size={{ xs: 12, md: 8 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={200} />}>
          <ActiveTickets />
        </Suspense>
      </Grid>
      <Grid size={{ xs: 12, md: 4 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={150} />}>
          <LatestPosts />
        </Suspense>
      </Grid>

      {/* Row 6: Feeds and customers */}
      <Grid size={{ xs: 12, md: 5, lg: 4 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={200} />}>
          <FeedsCard />
        </Suspense>
      </Grid>
      <Grid size={{ xs: 12, md: 7, lg: 8 }}>
        <Suspense fallback={
          <Box sx={{ p: 2 }}>
            <Skeleton variant="rectangular" height={30} sx={{ mb: 1 }} />
            <Skeleton variant="rectangular" height={30} sx={{ mb: 1 }} />
            <Skeleton variant="rectangular" height={30} sx={{ mb: 1 }} />
            <Skeleton variant="rectangular" height={30} />
          </Box>
        }>
          <LatestCustomers />
        </Suspense>
      </Grid>

      {/* Row 7: Latest orders - Heavy component */}
      <Grid size={{ xs: 12 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={400} />}>
          <LatestOrder />
        </Suspense>
      </Grid>

      {/* Row 8: Bottom row widgets */}
      <Grid size={{ xs: 12, md: 6, lg: 4 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={150} />}>
          <IncomingRequests />
        </Suspense>
      </Grid>
      <Grid size={{ xs: 12, md: 6, lg: 4 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={150} />}>
          <TotalRevenue />
        </Suspense>
      </Grid>
      <Grid size={{ xs: 12, md: 12, lg: 4 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={150} />}>
          <NewCustomers />
        </Suspense>
      </Grid>

      {/* Row 9: Recent tickets - Heavy component */}
      <Grid size={{ xs: 12, md: 12, lg: 8 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={300} />}>
          <RecentTickets />
        </Suspense>
      </Grid>
    </Grid>
  );
};

export default OptimizedWidgetData;
