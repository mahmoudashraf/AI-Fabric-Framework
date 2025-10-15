'use client';

import React, { Suspense, useEffect, useState } from 'react';
import dynamic from 'next/dynamic';
import { Grid, Skeleton, Box } from '@mui/material';
import { gridSpacing } from 'constants/index';

// ==============================|| OPTIMIZED DEFAULT DASHBOARD ||============================== //

/**
 * OPTIMIZED DEFAULT DASHBOARD
 * 
 * This version demonstrates code splitting improvements:
 * 1. Critical cards load immediately
 * 2. Heavy charts are lazy loaded
 * 3. Progressive loading with skeleton UI
 * 4. Better performance and user experience
 */

// ==================== IMMEDIATE LOADING (Critical Cards) ====================
// These cards are small and critical for initial dashboard view
import EarningCard from 'components/dashboard/Default/EarningCard';
import TotalOrderLineChartCard from 'components/dashboard/Default/TotalOrderLineChartCard';
import TotalIncomeDarkCard from 'components/dashboard/Default/TotalIncomeDarkCard';
import TotalIncomeLightCard from 'components/dashboard/Default/TotalIncomeLightCard';

// ==================== LAZY LOADING (Heavy Charts) ====================
// These components are loaded on demand with loading states

const TotalGrowthBarChart = dynamic(() => import('components/dashboard/Default/TotalGrowthBarChart'), {
  loading: () => (
    <Box sx={{ p: 2 }}>
      <Skeleton variant="rectangular" height={40} sx={{ mb: 2 }} />
      <Skeleton variant="rectangular" height={200} />
    </Box>
  ),
  ssr: false, // Charts don't need SSR
});

const PopularCard = dynamic(() => import('components/dashboard/Default/PopularCard'), {
  loading: () => (
    <Box sx={{ p: 2 }}>
      <Skeleton variant="rectangular" height={30} sx={{ mb: 1 }} />
      <Skeleton variant="rectangular" height={30} sx={{ mb: 1 }} />
      <Skeleton variant="rectangular" height={30} sx={{ mb: 1 }} />
      <Skeleton variant="rectangular" height={30} />
    </Box>
  ),
});

// ==================== OPTIMIZED DASHBOARD COMPONENT ====================

const OptimizedDashboard = () => {
  const [isLoading, setLoading] = useState(true);
  
  useEffect(() => {
    // Simulate initial data loading
    const timer = setTimeout(() => {
      setLoading(false);
    }, 1000);
    
    return () => clearTimeout(timer);
  }, []);

  return (
    <Grid container spacing={gridSpacing}>
      {/* Top Row: Critical Cards - Load immediately */}
      <Grid size={{ xs: 12 }}>
        <Grid container spacing={gridSpacing}>
          <Grid size={{ xs: 12, sm: 6, md: 6, lg: 4 }}>
            <EarningCard isLoading={isLoading} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 6, lg: 4 }}>
            <TotalOrderLineChartCard isLoading={isLoading} />
          </Grid>
          <Grid size={{ xs: 12, sm: 12, md: 12, lg: 4 }}>
            <Grid container spacing={gridSpacing}>
              <Grid size={{ xs: 12, sm: 6, md: 6, lg: 12 }}>
                <TotalIncomeDarkCard isLoading={isLoading} />
              </Grid>
              <Grid size={{ xs: 12, sm: 6, md: 6, lg: 12 }}>
                <TotalIncomeLightCard isLoading={isLoading} />
              </Grid>
            </Grid>
          </Grid>
        </Grid>
      </Grid>
      
      {/* Bottom Row: Heavy Charts - Lazy loaded */}
      <Grid size={{ xs: 12 }}>
        <Grid container spacing={gridSpacing}>
          <Grid size={{ xs: 12, md: 8 }}>
            <Suspense fallback={
              <Box sx={{ p: 2 }}>
                <Skeleton variant="rectangular" height={40} sx={{ mb: 2 }} />
                <Skeleton variant="rectangular" height={200} />
              </Box>
            }>
              <TotalGrowthBarChart isLoading={isLoading} />
            </Suspense>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <Suspense fallback={
              <Box sx={{ p: 2 }}>
                <Skeleton variant="rectangular" height={30} sx={{ mb: 1 }} />
                <Skeleton variant="rectangular" height={30} sx={{ mb: 1 }} />
                <Skeleton variant="rectangular" height={30} sx={{ mb: 1 }} />
                <Skeleton variant="rectangular" height={30} />
              </Box>
            }>
              <PopularCard isLoading={isLoading} />
            </Suspense>
          </Grid>
        </Grid>
      </Grid>
    </Grid>
  );
};

export default OptimizedDashboard;
