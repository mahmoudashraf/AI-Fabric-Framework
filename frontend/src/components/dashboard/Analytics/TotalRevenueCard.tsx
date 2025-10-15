'use client';

import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';
import ArrowDropUpIcon from '@mui/icons-material/ArrowDropUp';
import {
  Divider,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Typography,
  Skeleton,
} from '@mui/material';
import React, { memo, useMemo } from 'react';
import PerfectScrollbar from 'react-perfect-scrollbar';

// project imports
import { IRevenueData } from '@/types/common';
import MainCard from 'ui-component/cards/MainCard';

// assets

// types

// ===========================|| DASHBOARD ANALYTICS - TOTAL REVENUE CARD ||=========================== //

interface ITotalRevenueCardProps {
  data?: IRevenueData[];
  loading?: boolean;
  error?: string | null;
}

interface ICryptoItem {
  name: string;
  amount: number;
  change: number;
  changeType: 'increase' | 'decrease';
}

const TotalRevenueCard: React.FC<ITotalRevenueCardProps> = memo(
  ({ data: _data, loading = false, error = null }) => {
    // Mock data - in real app, this would come from props or API
    const mockData: ICryptoItem[] = useMemo(
      () => [
        { name: 'Bitcoin', amount: 145.85, change: 145.85, changeType: 'increase' },
        { name: 'Ethereum', amount: -6.368, change: 6.368, changeType: 'decrease' },
        { name: 'Ripple', amount: 458.63, change: 458.63, changeType: 'increase' },
        { name: 'Neo', amount: -5.631, change: 5.631, changeType: 'decrease' },
        { name: 'Ethereum', amount: -6.368, change: 6.368, changeType: 'decrease' },
        { name: 'Ripple', amount: 458.63, change: 458.63, changeType: 'increase' },
        { name: 'Neo', amount: -5.631, change: 5.631, changeType: 'decrease' },
        { name: 'Ethereum', amount: -6.368, change: 6.368, changeType: 'decrease' },
        { name: 'Ripple', amount: 458.63, change: 458.63, changeType: 'increase' },
        { name: 'Neo', amount: -5.631, change: 5.631, changeType: 'decrease' },
      ],
      []
    );

    const successSX = { color: 'success.dark' };
    const errorSX = { color: 'error.main' };

    const renderCryptoItem = (item: ICryptoItem, index: number) => (
      <React.Fragment key={`${item.name}-${index}`}>
        <ListItemButton>
          <ListItemIcon>
            {item.changeType === 'increase' ? (
              <ArrowDropUpIcon sx={successSX} />
            ) : (
              <ArrowDropDownIcon sx={errorSX} />
            )}
          </ListItemIcon>
          <ListItemText
            primary={
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <span>{item.name}</span>
                <Typography sx={item.changeType === 'increase' ? successSX : errorSX}>
                  {item.changeType === 'increase' ? '+' : '-'} ${Math.abs(item.amount).toFixed(3)}
                </Typography>
              </Stack>
            }
          />
        </ListItemButton>
        {index < mockData.length - 1 && <Divider />}
      </React.Fragment>
    );

    const renderSkeleton = () => (
      <>
        {Array.from({ length: 5 }).map((_, index) => (
          <React.Fragment key={index}>
            <ListItemButton>
              <ListItemIcon>
                <Skeleton variant="circular" width={32} height={32} />
              </ListItemIcon>
              <ListItemText
                primary={
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Skeleton variant="text" width={80} />
                    <Skeleton variant="text" width={60} />
                  </Stack>
                }
              />
            </ListItemButton>
            {index < 4 && <Divider />}
          </React.Fragment>
        ))}
      </>
    );

    if (error) {
      return (
        <MainCard title="Total Revenue" content={false}>
          <Typography color="error" sx={{ p: 2 }}>
            Error loading revenue data: {error}
          </Typography>
        </MainCard>
      );
    }

    return (
      <MainCard title="Total Revenue" content={false}>
        <PerfectScrollbar style={{ height: 370 }}>
          <List
            component="nav"
            aria-label="main mailbox folders"
            sx={{
              '& svg': {
                width: 32,
                my: -0.75,
                ml: -0.75,
                mr: 0.75,
              },
            }}
          >
            {loading ? renderSkeleton() : mockData.map(renderCryptoItem)}
          </List>
        </PerfectScrollbar>
      </MainCard>
    );
  }
);

TotalRevenueCard.displayName = 'TotalRevenueCard';

export default TotalRevenueCard;
