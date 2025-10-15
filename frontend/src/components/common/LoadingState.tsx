'use client';

import { Box, Skeleton } from '@mui/material';
import React from 'react';

interface ILoadingStateProps {
  variant?: 'card' | 'list' | 'table' | 'chart';
  count?: number;
  height?: number;
  width?: string | number;
}

const LoadingState: React.FC<ILoadingStateProps> = ({
  variant = 'card',
  count = 3,
  height = 200,
  width = '100%',
}) => {
  const renderCardSkeleton = () => (
    <Box sx={{ p: 2 }}>
      <Skeleton variant="rectangular" height={height} width={width} sx={{ mb: 2 }} />
      <Skeleton variant="text" width="60%" />
      <Skeleton variant="text" width="40%" />
    </Box>
  );

  const renderListSkeleton = () => (
    <Box sx={{ p: 2 }}>
      {Array.from({ length: count }).map((_, index) => (
        <Box key={index} sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
          <Skeleton variant="circular" width={40} height={40} sx={{ mr: 2 }} />
          <Box sx={{ flex: 1 }}>
            <Skeleton variant="text" width="80%" />
            <Skeleton variant="text" width="60%" />
          </Box>
        </Box>
      ))}
    </Box>
  );

  const renderTableSkeleton = () => (
    <Box sx={{ p: 2 }}>
      <Skeleton variant="rectangular" height={40} width={width} sx={{ mb: 1 }} />
      {Array.from({ length: count }).map((_, index) => (
        <Box key={index} sx={{ display: 'flex', mb: 1 }}>
          <Skeleton variant="text" width="25%" sx={{ mr: 1 }} />
          <Skeleton variant="text" width="25%" sx={{ mr: 1 }} />
          <Skeleton variant="text" width="25%" sx={{ mr: 1 }} />
          <Skeleton variant="text" width="25%" />
        </Box>
      ))}
    </Box>
  );

  const renderChartSkeleton = () => (
    <Box sx={{ p: 2 }}>
      <Skeleton variant="rectangular" height={height} width={width} />
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 2 }}>
        <Skeleton variant="text" width="20%" />
        <Skeleton variant="text" width="20%" />
        <Skeleton variant="text" width="20%" />
        <Skeleton variant="text" width="20%" />
      </Box>
    </Box>
  );

  switch (variant) {
    case 'list':
      return renderListSkeleton();
    case 'table':
      return renderTableSkeleton();
    case 'chart':
      return renderChartSkeleton();
    default:
      return renderCardSkeleton();
  }
};

export default LoadingState;
