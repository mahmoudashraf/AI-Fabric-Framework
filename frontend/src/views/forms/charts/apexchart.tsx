// material-ui
import { Grid  } from '@mui/material';

// project imports

// charts
import ApexAreaChart from 'components/forms/chart/Apexchart/ApexAreaChart';
import ApexBarChart from 'components/forms/chart/Apexchart/ApexBarChart';
import ApexColumnChart from 'components/forms/chart/Apexchart/ApexColumnChart';
import ApexLineChart from 'components/forms/chart/Apexchart/ApexLineChart';
import ApexMixedChart from 'components/forms/chart/Apexchart/ApexMixedChart';
import ApexPieChart from 'components/forms/chart/Apexchart/ApexPieChart';
import ApexPolarChart from 'components/forms/chart/Apexchart/ApexPolarChart';
import ApexRedialBarChart from 'components/forms/chart/Apexchart/ApexRedialChart';
import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';

// ==============================|| APEX CHARTS ||============================== //

const Apexchart = () => (
  <Grid container spacing={gridSpacing}>
    <Grid size={{ xs: 12, md: 6 }}>
      <MainCard title="Column Chart">
        <ApexColumnChart />
      </MainCard>
    </Grid>
    <Grid size={{ xs: 12, md: 6 }}>
      <MainCard title="Bar Chart">
        <ApexBarChart />
      </MainCard>
    </Grid>
    <Grid size={{ xs: 12, md: 6 }}>
      <MainCard title="Line Chart">
        <ApexLineChart />
      </MainCard>
    </Grid>
    <Grid size={{ xs: 12, md: 6 }}>
      <MainCard title="Area Chart">
        <ApexAreaChart />
      </MainCard>
    </Grid>
    <Grid size={{ xs: 12, md: 6 }}>
      <MainCard title="Mixed Chart">
        <ApexMixedChart />
      </MainCard>
    </Grid>
    <Grid size={{ xs: 12, md: 6 }}>
      <MainCard title="Redial Chart">
        <ApexRedialBarChart />
      </MainCard>
    </Grid>
    <Grid size={{ xs: 12, md: 6 }}>
      <MainCard title="Polar Chart">
        <ApexPolarChart />
      </MainCard>
    </Grid>
    <Grid size={{ xs: 12, md: 6 }}>
      <MainCard title="Pie Chart">
        <ApexPieChart />
      </MainCard>
    </Grid>
  </Grid>
);

export default Apexchart;
