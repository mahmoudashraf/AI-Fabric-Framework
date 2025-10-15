'use client';

import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import { Box, Button, Grid, Menu, MenuItem, Typography  } from '@mui/material';
import dynamic from 'next/dynamic';
import React, { useState } from 'react';

// material-ui

// third party
import { Props as ChartProps } from 'react-apexcharts';

// project imports
import { GenericCardProps } from 'types';

import MainCard from './MainCard';

// types

// assets

const ReactApexChart = dynamic(() => import('react-apexcharts'), { ssr: false });

// ==========================|| ANALYTICS CHART CARD ||========================== //

interface AnalyticsChartCardProps extends GenericCardProps {
  title: string;
  chartData: ChartProps;
  dropData: { options: { label: string; value: number }[]; title: string };
  listData: {
    color: string;
    value: number;
    icon: React.ReactNode | string;
    state: number;
    percentage: number;
  }[];
}

const AnalyticsChartCard = ({ title, chartData, dropData, listData }: AnalyticsChartCardProps) => {
  const [anchorEl, setAnchorEl] = useState<Element | (() => Element) | null | undefined>(null);

  let dropHtml;
  if (dropData) {
    const handleClick = (event: React.MouseEvent<HTMLButtonElement> | undefined) => {
      setAnchorEl(event?.currentTarget);
    };

    const handleClose = () => {
      setAnchorEl(null);
    };

    dropHtml = (
      <>
        <Button
          variant="text"
          disableElevation
          size="small"
          aria-controls="simple-menu"
          aria-haspopup="true"
          onClick={handleClick}
        >
          {dropData.title}
        </Button>
        <Menu
          anchorOrigin={{
            vertical: 'bottom',
            horizontal: 'right',
          }}
          transformOrigin={{
            vertical: 'top',
            horizontal: 'right',
          }}
          id="simple-menu"
          anchorEl={anchorEl}
          keepMounted
          open={Boolean(anchorEl)}
          onClose={handleClose}
        >
          {dropData?.options.map((option, index) => (
            <MenuItem key={index} onClick={handleClose}>
              {option.label}
            </MenuItem>
          ))}
        </Menu>
      </>
    );
  }

  let listItem;
  if (listData) {
    listItem = listData.map((item, index) => (
      <Grid key={index}>
        <Box
          sx={{
            color: item.color,
          }}
        >
          <Grid container spacing={2} justifyContent="center">
            <Grid>{item.icon}</Grid>
            <Grid>
              <Typography variant="subtitle1">{item.value}%</Typography>
            </Grid>
            <Grid>
              {item.state === 1 && (
                <ArrowUpwardIcon fontSize="inherit" color="inherit" sx={{ mt: 0.5 }} />
              )}
              {item.state === 0 && (
                <ArrowDownwardIcon fontSize="inherit" color="inherit" sx={{ mt: 0.5 }} />
              )}
            </Grid>
            <Grid>
              <Typography variant="subtitle1" color="inherit">
                {item.percentage}%
              </Typography>
            </Grid>
          </Grid>
        </Box>
      </Grid>
    ));
  }

  return (
    <MainCard>
      <Grid container justifyContent="space-between" alignItems="center">
        {title && (
          <Grid>
            <Typography variant="subtitle1">{title}</Typography>
          </Grid>
        )}
        <Grid>{dropHtml}</Grid>
      </Grid>
      <Grid container justifyContent="center" alignItems="center">
        <Grid container direction="column" spacing={1} size={{ xs: 12 }}>
          <Box
            sx={{
              mt: 3,
              display: 'block',
            }}
          >
            {listItem}
          </Box>
        </Grid>
        <Grid size={{ xs: 12, sm: 6 }}>
          <ReactApexChart
            options={chartData.options || {}}
            series={chartData.series || []}
            type={(chartData.options?.chart?.type as ChartProps['type']) || 'line'}
            height={chartData.options?.chart?.height || 350}
          />
        </Grid>
      </Grid>
    </MainCard>
  );
};

export default AnalyticsChartCard;
