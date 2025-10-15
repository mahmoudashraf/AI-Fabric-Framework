'use client';

import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';
import ArrowDropUpIcon from '@mui/icons-material/ArrowDropUp';
import ImportantDevicesIcon from '@mui/icons-material/ImportantDevices';
import LaptopIcon from '@mui/icons-material/Laptop';
import PhonelinkLockIcon from '@mui/icons-material/PhonelinkLock';
import TabletAndroidIcon from '@mui/icons-material/TabletAndroid';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import { Grid  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import React, { useState } from 'react';

// material-ui

// third party
import { Props as ChartProps } from 'react-apexcharts';

// project imports
import {
  TotalLineCardChartOptions1,
  TotalLineCardChartOptions2,
  TotalLineCardChartOptions3,
  SalesLineCardChartOptions,
  MarketChartCardOptions,
  RevenueChartCardOptions,
  SeoChartCardOptions1,
  SeoChartCardOptions2,
  SeoChartCardOptions3,
  SeoChartCardOptions4,
  SeoChartCardOptions5,
  SeoChartCardOptions6,
  SeoChartCardOptions7,
  SeoChartCardOptions8,
  SeoChartCardOptions9,
  ConversionsChartCardOptions,
  SatisfactionChartCardOptions,
  AnalyticsChartCardOptions,
} from 'components/widget/Chart/chart-options';
import ConversionsChartCard from 'components/widget/Chart/ConversionsChartCard';
import MarketSaleChartCard from 'components/widget/Chart/MarketSaleChartCard';
import RevenueChartCard from 'components/widget/Chart/RevenueChartCard';
import SatisfactionChartCard from 'components/widget/Chart/SatisfactionChartCard';
import useConfig from 'hooks/useConfig';
import SalesLineChartCard from 'ui-component/cards/SalesLineChartCard';
import SeoChartCard from 'ui-component/cards/SeoChartCard';
import TotalLineChartCard from 'ui-component/cards/TotalLineChartCard';
import AnalyticsChartCard from 'ui-component/cards/AnalyticsChartCard';
import { gridSpacing } from 'constants/index';
import { ApexOptions } from 'apexcharts';

// chart data

// assets

// ================================|| CHART ||================================ //

const Chart = () => {
  const theme = useTheme();

  const [totalLineCardChartSeries1] = useState([
    {
      name: 'series1',
      data: [20, 10, 18, 12, 25, 10, 20],
    },
  ]);
  const [totalLineCardChartSeries2] = useState([
    {
      name: 'series1',
      data: [10, 20, 18, 25, 12, 10, 20],
    },
  ]);
  const [totalLineCardChartSeries3] = useState([
    {
      name: 'series1',
      data: [20, 10, 18, 12, 25, 10, 20],
    },
  ]);

  const [salesLineCardChartSeries] = useState([
    {
      name: 'series1',
      data: [55, 35, 75, 25, 90, 50],
    },
  ]);

  const [marketChartCardSeries] = useState([
    {
      name: 'Youtube',
      data: [10, 90, 65, 85, 40, 80, 30],
    },
    {
      name: 'Facebook',
      data: [50, 30, 25, 15, 60, 10, 25],
    },
    {
      name: 'Twitter',
      data: [5, 50, 40, 55, 20, 40, 20],
    },
  ]);

  const [revenueChartCardSeries] = useState([1258, 975, 500]);

  const [seoChartCardSeries1] = useState([
    {
      name: 'series1',
      data: [9, 66, 41, 89, 63, 25, 44, 12, 36, 20, 54, 25, 9],
    },
  ]);
  const [seoChartCardSeries2] = useState([
    {
      data: [25, 66, 41, 89, 63, 25, 44, 12, 36, 9, 54, 25, 66, 41, 89, 63],
    },
  ]);
  const [seoChartCardSeries3] = useState([
    {
      name: 'series1',
      data: [9, 66, 41, 89, 63, 25, 44, 12, 36, 20, 54, 25, 9],
    },
  ]);
  const [seoChartCardSeries4] = useState([
    {
      data: [2, 1, 2, 1, 1, 3, 0],
    },
  ]);
  const [seoChartCardSeries5] = useState([
    {
      data: [3, 0, 1, 2, 1, 1, 2],
    },
  ]);
  const [seoChartCardSeries6] = useState([
    {
      data: [2, 1, 2, 1, 1, 3, 0],
    },
  ]);
  const [seoChartCardSeries7] = useState([
    {
      data: [2, 1, 2, 1, 1, 3, 0],
    },
  ]);
  const [seoChartCardSeries8] = useState([
    {
      data: [2, 1, 2, 1, 1, 3, 0],
    },
  ]);
  const [seoChartCardSeries9] = useState([
    {
      data: [2, 1, 2, 1, 1, 3, 0],
    },
  ]);

  const [conversionsChartCardSeries] = useState([
    {
      data: [
        25, 66, 41, 89, 63, 25, 44, 12, 36, 9, 54, 25, 66, 41, 89, 63, 54, 25, 66, 41, 89, 63, 25,
        44, 12, 36, 9, 54,
      ],
    },
  ]);

  const [satisfactionChartCardSeries] = useState([66, 50, 40, 30]);

  const [analyticsChartCardSeries] = useState([
    {
      name: 'Requests',
      data: [66.6, 29.7, 32.8, 50],
    },
  ]);

  const [marketChartCardOptions, setMarketChartCardOptions] =
    useState<ChartProps>(MarketChartCardOptions);
  const [revenueChartCardOptions, setRevenueChartCardOptions] =
    useState<ChartProps>(RevenueChartCardOptions);
  const [seoChartCardOptions1, setSeoChartCardOptions1] =
    useState<ChartProps>(SeoChartCardOptions1);
  const [seoChartCardOptions2, setSeoChartCardOptions2] =
    useState<ChartProps>(SeoChartCardOptions2);
  const [seoChartCardOptions3, setSeoChartCardOptions3] =
    useState<ChartProps>(SeoChartCardOptions3);
  const [seoChartCardOptions4, setSeoChartCardOptions4] =
    useState<ChartProps>(SeoChartCardOptions4);
  const [seoChartCardOptions5, setSeoChartCardOptions5] =
    useState<ChartProps>(SeoChartCardOptions5);
  const [seoChartCardOptions6, setSeoChartCardOptions6] =
    useState<ChartProps>(SeoChartCardOptions6);
  const [seoChartCardOptions7, setSeoChartCardOptions7] =
    useState<ChartProps>(SeoChartCardOptions7);
  const [seoChartCardOptions8, setSeoChartCardOptions8] =
    useState<ChartProps>(SeoChartCardOptions8);
  const [seoChartCardOptions9, setSeoChartCardOptions9] =
    useState<ChartProps>(SeoChartCardOptions9);
  const [conversionsChartCardOptions, setConversionsChartCardOptions] = useState<ChartProps>(
    ConversionsChartCardOptions
  );
  const [satisfactionChartCardOptions, setSatisfactionChartCardOptions] = useState<ChartProps>(
    SatisfactionChartCardOptions
  );
  const [analyticsChartCardOptions, setAnalyticsChartCardOptions] =
    useState<ChartProps>(AnalyticsChartCardOptions);

  const { navType } = useConfig();

  const backColor = theme.palette.background.paper;
  const secondary = theme.palette.secondary.main;
  const error = theme.palette.error.main;
  const primary = theme.palette.primary.main;
  const successDark = theme.palette.success.dark;
  const orange = theme.palette.orange.main;
  const orangeDark = theme.palette.orange.dark;

  React.useEffect(() => {
    setAnalyticsChartCardOptions((prevState: any) => ({
      ...prevState,
      colors: [primary, successDark, error, orangeDark],
      tooltip: {
        theme: navType === 'dark' ? 'dark' : 'light',
      },
    }));

    setMarketChartCardOptions((prevState: any) => ({
      ...prevState,
      colors: [secondary, error, primary],
      tooltip: {
        theme: navType === 'dark' ? 'dark' : 'light',
      },
    }));

    setRevenueChartCardOptions((prevState: any) => ({
      ...prevState,
      colors: [error, primary, secondary],
      stroke: {
        colors: [backColor],
      },
    }));

    setSeoChartCardOptions1((prevState: any) => ({
      ...prevState,
      colors: [primary],
      tooltip: {
        theme: navType === 'dark' ? 'dark' : 'light',
      },
    }));

    setSeoChartCardOptions2((prevState: any) => ({
      ...prevState,
      colors: [successDark],
      tooltip: {
        theme: navType === 'dark' ? 'dark' : 'light',
      },
    }));

    setSeoChartCardOptions3((prevState: any) => ({
      ...prevState,
      colors: [error],
      tooltip: {
        theme: navType === 'dark' ? 'dark' : 'light',
      },
    }));

    setSeoChartCardOptions4((prevState: any) => ({
      ...prevState,
      colors: [orange],
      tooltip: {
        theme: navType === 'dark' ? 'dark' : 'light',
      },
    }));

    setSeoChartCardOptions5((prevState: any) => ({
      ...prevState,
      colors: [secondary],
      tooltip: {
        theme: navType === 'dark' ? 'dark' : 'light',
      },
    }));

    setSeoChartCardOptions6((prevState: any) => ({
      ...prevState,
      colors: [error],
      tooltip: {
        theme: navType === 'dark' ? 'dark' : 'light',
      },
    }));

    setSeoChartCardOptions7((prevState: any) => ({
      ...prevState,
      colors: [secondary],
      tooltip: {
        theme: navType === 'dark' ? 'dark' : 'light',
      },
    }));

    setSeoChartCardOptions8((prevState: any) => ({
      ...prevState,
      colors: [primary],
      tooltip: {
        theme: navType === 'dark' ? 'dark' : 'light',
      },
    }));

    setSeoChartCardOptions9((prevState: any) => ({
      ...prevState,
      colors: [successDark],
      tooltip: {
        theme: navType === 'dark' ? 'dark' : 'light',
      },
    }));

    setConversionsChartCardOptions((prevState: any) => ({
      ...prevState,
      colors: [secondary],
      tooltip: {
        theme: navType === 'dark' ? 'dark' : 'light',
      },
    }));

    setSatisfactionChartCardOptions((prevState: any) => ({
      ...prevState,
      theme: {
        monochrome: {
          enabled: true,
          color: orangeDark,
        },
      },
      stroke: {
        colors: [backColor],
      },
    }));
  }, [navType, backColor, secondary, error, primary, successDark, orange, orangeDark]);

  return (
    <Grid container spacing={gridSpacing} alignItems="center">
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <TotalLineChartCard
          chartData={{ series: totalLineCardChartSeries1 || [], options: TotalLineCardChartOptions1 as ApexOptions }}
          value={4000}
          title="Total Sales"
          percentage="42%"
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <TotalLineChartCard
          chartData={{ series: totalLineCardChartSeries2, options: TotalLineCardChartOptions2 as ApexOptions }}
          bgColor={theme.palette.error.main}
          value={2500}
          title="Total Comment"
          percentage="15%"
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <TotalLineChartCard
          chartData={{ series: totalLineCardChartSeries3, options: TotalLineCardChartOptions3 as ApexOptions }}
          bgColor={theme.palette.success.dark}
          value={2500}
          title="Total Status"
          percentage="95%"
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <TotalLineChartCard
          chartData={{ series: totalLineCardChartSeries3, options: TotalLineCardChartOptions3 as ApexOptions }}
          bgColor={theme.palette.secondary.main}
          value={12500}
          title="Total Visitors"
          percentage="75%"
        />
      </Grid>

      <Grid size={{ xs: 12, md: 6, lg: 7 }}>
        <MarketSaleChartCard
          chartData={{ series: marketChartCardSeries, options: marketChartCardOptions as ApexOptions }}
        />
      </Grid>
      <Grid size={{ xs: 12, md: 6, lg: 5 }}>
        <RevenueChartCard
          chartData={{ series: revenueChartCardSeries, options: revenueChartCardOptions as ApexOptions }}
        />
      </Grid>

      <Grid size={{ xs: 12, sm: 6, md: 4, lg: 2 }}>
        <SeoChartCard
          type={1}
          chartData={{ series: seoChartCardSeries4, options: seoChartCardOptions4 as ApexOptions }}
          value={798}
          title="Users"
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 6, md: 4, lg: 2 }}>
        <SeoChartCard
          type={1}
          chartData={{ series: seoChartCardSeries5, options: seoChartCardOptions5 as ApexOptions }}
          value={486}
          title="Timeout"
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 6, md: 4, lg: 2 }}>
        <SeoChartCard
          type={1}
          chartData={{ series: seoChartCardSeries6, options: seoChartCardOptions6 as ApexOptions }}
          value="9, 454"  
          title="Views"
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 6, md: 4, lg: 2 }}>
        <SeoChartCard
          type={1}
          chartData={{ series: seoChartCardSeries7, options: seoChartCardOptions7 as ApexOptions }}
          value={7.15}
          title="Session"
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 6, md: 4, lg: 2 }}>
        <SeoChartCard
          type={1}
          chartData={{ series: seoChartCardSeries8, options: seoChartCardOptions8 as ApexOptions }}
          value="04:30"
          title="Avg. Session"
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 6, md: 4, lg: 2 }}>
        <SeoChartCard
          type={1}
          chartData={{ series: seoChartCardSeries9, options: seoChartCardOptions9 as ApexOptions }}
          value="1.55%"
          title="Bounce Rate"
        />
      </Grid>

      <Grid size={{ xs: 12, sm: 6, md: 6, lg: 7 }}>
        <SalesLineChartCard
          chartData={{ series: salesLineCardChartSeries, options: SalesLineCardChartOptions as ApexOptions }}
          bgColor={theme.palette.error.main}
          title="Sales Per Day"
          percentage="3%"
          icon={<TrendingDownIcon />}
          footerData={[
            {
              value: '$4230',
              label: 'Total Revenue',
            },
            {
              value: '321',
              label: 'Today Sales',
            },
          ]}
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 6, md: 6, lg: 5 }}>
        <SalesLineChartCard
          chartData={{ series: salesLineCardChartSeries, options: SalesLineCardChartOptions as ApexOptions }}
          title="Order Per Month"
          percentage="28%"
          icon={<TrendingDownIcon />}
          footerData={[
            {
              value: '1695',
              label: 'Total Orders',
            },
            {
              value: '321',
              label: 'Today Orders',
            },
          ]}
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 12 }}>
        <AnalyticsChartCard
          chartData={{ series: analyticsChartCardSeries, options: analyticsChartCardOptions as ApexOptions }}
          title="Page view by device"
          dropData={{
            title: 'Weekly',
            options: [
              {
                value: 1,
                label: '1 Week',
              },
              {
                value: 104,
                label: '2 Years',
              },
              {
                value: 12,
                label: '3 Monthes',
              },
            ],
          }}
          listData={[
            {
              color: theme.palette.primary.main,
              icon: <ImportantDevicesIcon color="inherit" fontSize="small" />,
              value: 66.6,
              percentage: 2,
              state: 1,
            },
            {
              color: theme.palette.success.dark,
              icon: <PhonelinkLockIcon color="inherit" fontSize="small" />,
              value: 29.7,
              percentage: 3,
              state: 1,
            },
            {
              color: theme.palette.error.main,
              icon: <TabletAndroidIcon color="inherit" fontSize="small" />,
              value: 32.8,
              percentage: 8,
              state: 0,
            },
            {
              color: theme.palette.orange.dark,
              icon: <LaptopIcon color="inherit" fontSize="small" />,
              value: 50.2,
              percentage: 5,
              state: 1,
            },
          ]}
        />
      </Grid>

      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <SeoChartCard
          chartData={{ series: seoChartCardSeries1, options: seoChartCardOptions1 as ApexOptions }}
          value="$16, 756"
          title="Visits"
          icon={<ArrowDropDownIcon color="error" />}
        />
      </Grid>

      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <SeoChartCard
          chartData={{ series: seoChartCardSeries2, options: seoChartCardOptions2 as ApexOptions }}
          value="49.54%"
          title="Bounce Rate"
          icon={<ArrowDropUpIcon color="primary" />}
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 12 }}>
        <SeoChartCard
          chartData={{ series: seoChartCardSeries3, options: seoChartCardOptions3 as ApexOptions }}
          value="1, 62,564"
          title="Products"
          icon={<ArrowDropDownIcon color="error" />}
        />
      </Grid>

      <Grid size={{ xs: 12, md: 6 }}>
        <ConversionsChartCard
          chartData={{ series: conversionsChartCardSeries, options: conversionsChartCardOptions as ApexOptions }}
        />
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <SatisfactionChartCard
          chartData={{ series: satisfactionChartCardSeries, options: satisfactionChartCardOptions as ApexOptions }}
        />
      </Grid>
    </Grid>
  );
};

export default Chart;
