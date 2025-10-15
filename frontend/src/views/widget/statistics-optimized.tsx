'use client';

import React, { Suspense } from 'react';
import dynamic from 'next/dynamic';
import { Grid, Skeleton, Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { gridSpacing } from 'constants/index';

// ===========================|| OPTIMIZED WIDGET STATISTICS WITH CODE SPLITTING ||=========================== //

/**
 * OPTIMIZED WIDGET STATISTICS
 * 
 * This version demonstrates code splitting improvements:
 * 1. Critical cards load immediately
 * 2. Heavy/complex cards are lazy loaded
 * 3. Progressive loading with skeleton UI
 * 4. Better performance and user experience
 */

// ==================== IMMEDIATE LOADING (Critical Cards) ====================
// These are simple cards that load quickly and are critical for initial view
import ReportCard from 'ui-component/cards/ReportCard';
import IconNumberCard from 'ui-component/cards/IconNumberCard';
import SideIconCard from 'ui-component/cards/SideIconCard';

// ==================== LAZY LOADING (Heavy Cards) ====================
// These cards are loaded on demand with loading states

// Revenue cards - More complex with charts/data
const RevenueCard = dynamic(() => import('ui-component/cards/RevenueCard'), {
  loading: () => <Skeleton variant="rectangular" height={120} />,
});

// Hover cards - Interactive components
const HoverDataCard = dynamic(() => import('ui-component/cards/HoverDataCard'), {
  loading: () => <Skeleton variant="rectangular" height={100} />,
});

const HoverSocialCard = dynamic(() => import('ui-component/cards/HoverSocialCard'), {
  loading: () => <Skeleton variant="rectangular" height={100} />,
});

// Round icon cards - Complex styling
const RoundIconCard = dynamic(() => import('ui-component/cards/RoundIconCard'), {
  loading: () => <Skeleton variant="rectangular" height={120} />,
});

// User count cards - Data visualization
const UserCountCard = dynamic(() => import('ui-component/cards/UserCountCard'), {
  loading: () => <Skeleton variant="rectangular" height={100} />,
});

// Complex widget cards - Heavy components
const ProjectTaskCard = dynamic(() => import('components/widget/Statistics/ProjectTaskCard'), {
  loading: () => (
    <Box sx={{ p: 2 }}>
      <Skeleton variant="rectangular" height={40} sx={{ mb: 1 }} />
      <Skeleton variant="rectangular" height={200} />
    </Box>
  ),
});

const CustomerSatisfactionCard = dynamic(() => import('components/widget/Statistics/CustomerSatisfactionCard'), {
  loading: () => <Skeleton variant="rectangular" height={200} />,
});

const IconGridCard = dynamic(() => import('components/widget/Statistics/IconGridCard'), {
  loading: () => <Skeleton variant="rectangular" height={200} />,
});

const WeatherCard = dynamic(() => import('components/widget/Statistics/WeatherCard'), {
  loading: () => <Skeleton variant="rectangular" height={150} />,
});

// ==================== OPTIMIZED WIDGET STATISTICS COMPONENT ====================

const OptimizedWidgetStatistics = () => {
  const theme = useTheme();

  return (
    <Grid container spacing={gridSpacing}>
      {/* Row 1: Critical Report Cards - Load immediately */}
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <ReportCard
          primary="$30200"
          secondary="All Earnings"
          color={theme.palette.secondary.main}
          iconPrimary={require('@mui/icons-material/AssessmentTwoTone').default}
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <ReportCard
          primary="145"
          secondary="Task"
          color={theme.palette.error.main}
          iconPrimary={require('@mui/icons-material/CalendarTodayTwoTone').default}
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <ReportCard
          primary="290+"
          secondary="Page Views"
          color={theme.palette.success.dark}
          iconPrimary={require('@mui/icons-material/DescriptionTwoTone').default}
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <ReportCard
          primary="500"
          secondary="Downloads"
          color={theme.palette.primary.main}
          iconPrimary={require('@mui/icons-material/ThumbDownAltTwoTone').default}
        />
      </Grid>

      {/* Row 2: Revenue Cards - Lazy loaded */}
      <Grid size={{ xs: 12, md: 12, lg: 4 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={120} />}>
          <RevenueCard
            primary="Revenue"
            secondary="$42,562"
            content="$50,032 Last Month"
            iconPrimary={require('@mui/icons-material/MonetizationOnTwoTone').default}
            color={theme.palette.secondary.main}
          />
        </Suspense>
      </Grid>
      <Grid size={{ xs: 12, md: 12, lg: 4 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={120} />}>
          <RevenueCard
            primary="Orders Received"
            secondary="486"
            content="20% Increase"
            iconPrimary={require('@mui/icons-material/AccountCircleTwoTone').default}
            color={theme.palette.primary.dark}
          />
        </Suspense>
      </Grid>
      <Grid size={{ xs: 12, md: 12, lg: 4 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={120} />}>
          <RevenueCard
            primary="Total Sales"
            secondary="1641"
            content="$1,055 Revenue Generated"
            iconPrimary={require('@mui/icons-material/LocalMallTwoTone').default}
            color={theme.palette.orange.dark}
          />
        </Suspense>
      </Grid>

      {/* Row 3: Icon Number Cards - Load immediately */}
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <IconNumberCard
          title="Visitors"
          primary="6035"
          color={theme.palette.primary.dark}
          iconPrimary={require('@mui/icons-material/AccountCircleTwoTone').default}
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <IconNumberCard
          title="Invoices"
          primary="19"
          color={theme.palette.error.main}
          iconPrimary={require('@mui/icons-material/DescriptionTwoTone').default}
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <IconNumberCard
          title="Issues"
          primary="63"
          color={theme.palette.warning.dark}
          iconPrimary={require('@mui/icons-material/BugReportTwoTone').default}
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <IconNumberCard
          title="Projects"
          primary="95%"
          color={theme.palette.success.dark}
          iconPrimary={require('@mui/icons-material/FolderOpenTwoTone').default}
        />
      </Grid>

      {/* Row 4: Side Icon Cards - Load immediately */}
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <SideIconCard
          iconPrimary={require('@mui/icons-material/AccountCircleTwoTone').default}
          primary="2,672"
          secondary="Last week"
          secondarySub="users"
          color={theme.palette.secondary.main}
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <SideIconCard
          iconPrimary={require('@mui/icons-material/AccountBalanceWalletTwoTone').default}
          primary="$6391"
          secondary="Total"
          secondarySub="earning"
          color={theme.palette.primary.dark}
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <SideIconCard
          iconPrimary={require('@mui/icons-material/EmojiEmotionsTwoTone').default}
          primary="9,276"
          secondary="Today"
          secondarySub="visitors"
          color={theme.palette.success.dark}
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <SideIconCard
          iconPrimary={require('@mui/icons-material/ShoppingCartTwoTone').default}
          primary="3,619"
          secondary="New"
          secondarySub="order"
          color={theme.palette.error.main}
        />
      </Grid>

      {/* Row 5: Hover Data Cards - Lazy loaded */}
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={100} />}>
          <HoverDataCard
            title="Total Paid Users"
            iconPrimary={require('@mui/icons-material/ArrowDownward').default}
            primary={7652}
            secondary="8% less Last 3 Months"
            color={theme.palette.error.main}
          />
        </Suspense>
      </Grid>
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={100} />}>
          <HoverDataCard
            title="Order Status"
            iconPrimary={require('@mui/icons-material/ArrowUpward').default}
            primary={625}
            secondary="6% From Last 3 Months"
            color={theme.palette.success.dark}
          />
        </Suspense>
      </Grid>
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={100} />}>
          <HoverDataCard
            title="Unique Visitors"
            iconPrimary={require('@mui/icons-material/ArrowDownward').default}
            primary={6522}
            secondary="10% From Last 6 Months"
            color={theme.palette.error.main}
          />
        </Suspense>
      </Grid>
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={100} />}>
          <HoverDataCard
            title="Monthly Earnings"
            iconPrimary={require('@mui/icons-material/ArrowUpward').default}
            primary={5963}
            secondary="36% From Last 6 Months"
            color={theme.palette.success.dark}
          />
        </Suspense>
      </Grid>

      {/* Row 6: Hover Social Cards - Lazy loaded */}
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={100} />}>
          <HoverSocialCard
            primary="Facebook Users"
            secondary="1165 +"
            iconPrimary={require('@mui/icons-material/Facebook').default}
            color={theme.palette.secondary.main}
          />
        </Suspense>
      </Grid>
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={100} />}>
          <HoverSocialCard
            primary="Twitter Users"
            secondary="780 +"
            iconPrimary={require('@mui/icons-material/Twitter').default}
            color={theme.palette.info.main}
          />
        </Suspense>
      </Grid>
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={100} />}>
          <HoverSocialCard
            primary="Linked In Users"
            secondary="998 +"
            iconPrimary={require('@mui/icons-material/LinkedIn').default}
            color={theme.palette.dark.main}
          />
        </Suspense>
      </Grid>
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={100} />}>
          <HoverSocialCard
            primary="Youtube Videos"
            secondary="650 +"
            iconPrimary={require('@mui/icons-material/YouTube').default}
            color={theme.palette.error.main}
          />
        </Suspense>
      </Grid>

      {/* Row 7: Round Icon Cards - Lazy loaded */}
      <Grid size={{ xs: 12, md: 12, sm: 6, lg: 4 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={120} />}>
          <RoundIconCard
            primary="Impressions"
            secondary="1,563"
            content="May 23 - June 01 (2018)"
            iconPrimary={require('@mui/icons-material/RemoveRedEyeTwoTone').default}
            color="primary.main"
            bgcolor="primary.light"
          />
        </Suspense>
      </Grid>
      <Grid size={{ xs: 12, md: 12, sm: 6, lg: 4 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={120} />}>
          <RoundIconCard
            primary="Goal"
            secondary="30,564"
            content="May 28 - June 01 (2018)"
            iconPrimary={require('@mui/icons-material/RadioButtonCheckedTwoTone').default}
            color="success.dark"
            bgcolor="success.light"
          />
        </Suspense>
      </Grid>
      <Grid size={{ xs: 12, md: 12, sm: 6, lg: 4 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={120} />}>
          <RoundIconCard
            primary="Impact"
            secondary="42.6%"
            content="May 30 - June 01 (2018)"
            iconPrimary={require('@mui/icons-material/PanToolTwoTone').default}
            color="warning.dark"
            bgcolor="warning.light"
          />
        </Suspense>
      </Grid>

      {/* Row 8: Project Task Card - Heavy component */}
      <Grid size={{ xs: 12 }}>
        <Suspense fallback={
          <Box sx={{ p: 2 }}>
            <Skeleton variant="rectangular" height={40} sx={{ mb: 1 }} />
            <Skeleton variant="rectangular" height={200} />
          </Box>
        }>
          <ProjectTaskCard />
        </Suspense>
      </Grid>

      {/* Row 9: User Count Cards - Lazy loaded */}
      <Grid size={{ xs: 12, md: 12, sm: 6, lg: 4 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={100} />}>
          <UserCountCard
            primary="Daily user"
            secondary="1,658"
            iconPrimary={require('@mui/icons-material/AccountCircleTwoTone').default}
            color={theme.palette.secondary.main}
          />
        </Suspense>
      </Grid>
      <Grid size={{ xs: 12, md: 12, sm: 6, lg: 4 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={100} />}>
          <UserCountCard
            primary="Daily page view"
            secondary="1K"
            iconPrimary={require('@mui/icons-material/DescriptionTwoTone').default}
            color={theme.palette.primary.dark}
          />
        </Suspense>
      </Grid>
      <Grid size={{ xs: 12, md: 12, sm: 6, lg: 4 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={100} />}>
          <UserCountCard
            primary="Last month visitor"
            secondary="5,678"
            iconPrimary={require('@mui/icons-material/EmojiEventsTwoTone').default}
            color={theme.palette.success.dark}
          />
        </Suspense>
      </Grid>

      {/* Row 10: Complex Widget Cards - Lazy loaded */}
      <Grid size={{ xs: 12, md: 12, sm: 6, lg: 4 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={200} />}>
          <CustomerSatisfactionCard />
        </Suspense>
      </Grid>

      <Grid size={{ xs: 12, md: 12, sm: 6, lg: 4 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={200} />}>
          <IconGridCard />
        </Suspense>
      </Grid>

      <Grid size={{ xs: 12, md: 12, lg: 4 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={150} />}>
          <WeatherCard />
        </Suspense>
      </Grid>
    </Grid>
  );
};

export default OptimizedWidgetStatistics;
