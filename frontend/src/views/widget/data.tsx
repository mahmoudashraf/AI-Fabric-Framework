'use client';

// material-ui
import { Button, CardActions, CardContent, Divider, Grid  } from '@mui/material';

// project imports

import ActiveTickets from 'components/widget/Data/ActiveTickets';
import ApplicationSales from 'components/widget/Data/ApplicationSales';
import FeedsCard from 'components/widget/Data/FeedsCard';

import LatestOrder from 'components/widget/Data/LatestOrder';

import IncomingRequests from 'components/widget/Data/IncomingRequests';
import LatestCustomers from 'components/widget/Data/LatestCustomers';
import LatestMessages from 'components/widget/Data/LatestMessages';
import LatestPosts from 'components/widget/Data/LatestPosts';
import NewCustomers from 'components/widget/Data/NewCustomers';
import ProductSales from 'components/widget/Data/ProductSales';
import ProjectTable from 'components/widget/Data/ProjectTable';
import RecentTickets from 'components/widget/Data/RecentTickets';
import TasksCard from 'components/widget/Data/TasksCard';
import TeamMembers from 'components/widget/Data/TeamMembers';
import ToDoList from 'components/widget/Data/ToDoList';
import TotalRevenue from 'components/widget/Data/TotalRevenue';
import TrafficSources from 'components/widget/Data/TrafficSources';
import UserActivity from 'components/widget/Data/UserActivity';
import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';

// ===========================|| WIDGET DATA ||=========================== //

const WidgetData = () => (
  <Grid container spacing={gridSpacing}>
    <Grid size={{ xs: 12, md: 6, lg: 4 }}>
      <ToDoList />
    </Grid>
    <Grid size={{ xs: 12, md: 6, lg: 4 }}>
      <TrafficSources />
    </Grid>
    <Grid size={{ xs: 12, md: 12, lg: 4 }}>
      <TeamMembers />
    </Grid>

    <Grid size={{ xs: 12, md: 7, lg: 6 }}>
      <UserActivity />
    </Grid>
    <Grid size={{ xs: 12, md: 5, lg: 6 }}>
      <LatestMessages />
    </Grid>

    <Grid size={{ xs: 12, md: 6, lg: 6 }}>
      <MainCard title="Projects" content={false}>
        <CardContent sx={{ p: 0 }}>
          <ProjectTable />
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
      <ProductSales />
    </Grid>

    <Grid size={{ xs: 12, md: 4 }}>
      <TasksCard />
    </Grid>
    <Grid size={{ xs: 12, md: 8 }}>
      <ApplicationSales />
    </Grid>

    <Grid size={{ xs: 12, md: 8 }}>
      <ActiveTickets />
    </Grid>
    <Grid size={{ xs: 12, md: 4 }}>
      <LatestPosts />
    </Grid>

    <Grid size={{ xs: 12, md: 5, lg: 4 }}>
      <FeedsCard />
    </Grid>
    <Grid size={{ xs: 12, md: 7, lg: 8 }}>
      <LatestCustomers />
    </Grid>

    <Grid size={{ xs: 12 }}>
      <LatestOrder />
    </Grid>

    <Grid size={{ xs: 12, md: 6, lg: 4 }}>
      <IncomingRequests />
    </Grid>
    <Grid size={{ xs: 12, md: 6, lg: 4 }}>
      <TotalRevenue />
    </Grid>
    <Grid size={{ xs: 12, md: 12, lg: 4 }}>
      <NewCustomers />
    </Grid>

    <Grid size={{ xs: 12, md: 12, lg: 8 }}>
      <RecentTickets />
    </Grid>
  </Grid>
);

export default WidgetData;
