'use client';

// material-ui
import PersonIcon from '@mui/icons-material/Person';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import DescriptionIcon from '@mui/icons-material/Description';
import StoreIcon from '@mui/icons-material/Store';
import GroupAddIcon from '@mui/icons-material/GroupAdd';
import MenuIcon from '@mui/icons-material/Menu';
import { 
  Grid, 
  Typography, 
  Box, 
  Card, 
  CardContent, 
  Tabs, 
  Tab, 
  Select, 
  MenuItem, 
  FormControl,
  Button,
  Chip,
  IconButton
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import React from 'react';

// project imports
import MainCard from 'ui-component/cards/MainCard';
import { gridSpacing } from 'constants/index';

// ===========================|| CRM LEAD SUMMARY ||=========================== //

const CrmLeadSummary = () => {
  const theme = useTheme();
  const [tabValue, setTabValue] = React.useState(3); // New Lead is active by default
  const [timeframe, setTimeframe] = React.useState('Today');

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setTabValue(newValue);
  };

  const handleTimeframeChange = (event: any) => {
    setTimeframe(event.target.value);
  };

  // Enhanced chart data with more realistic values
  const monthlyData = [
    { month: 'Jan', value: 120, revenue: 15000 },
    { month: 'Feb', value: 180, revenue: 22000 },
    { month: 'Mar', value: 160, revenue: 19500 },
    { month: 'Apr', value: 240, revenue: 28000 },
    { month: 'May', value: 35, revenue: 4500 },
    { month: 'Jun', value: 320, revenue: 38000 },
    { month: 'Jul', value: 280, revenue: 33000 },
    { month: 'Aug', value: 220, revenue: 26000 },
    { month: 'Sep', value: 300, revenue: 35000 },
    { month: 'Oct', value: 260, revenue: 31000 }
  ];

  const leadSourceData = [
    { name: 'Social Media', value: 45, percentage: 18, color: '#1976d2' },
    { name: 'Website', value: 120, percentage: 48, color: '#7b1fa2' },
    { name: 'Phone Call', value: 60, percentage: 24, color: '#9c27b0' },
    { name: 'Email', value: 25, percentage: 10, color: '#424242' }
  ];

  const salesData = [
    { day: 'Mon', value: 180, deals: 12, revenue: 22000 },
    { day: 'Tue', value: 140, deals: 8, revenue: 18000 },
    { day: 'Wed', value: 280, deals: 18, revenue: 34000 },
    { day: 'Thu', value: 220, deals: 14, revenue: 27000 },
    { day: 'Fri', value: 350, deals: 22, revenue: 42000 },
    { day: 'Sat', value: 160, deals: 10, revenue: 20000 },
    { day: 'Sun', value: 120, deals: 7, revenue: 15000 }
  ];

  // Calculate totals
  const totalLeads = leadSourceData.reduce((sum, item) => sum + item.value, 0);
  const totalRevenue = monthlyData.reduce((sum, item) => sum + item.revenue, 0);
  const totalDeals = salesData.reduce((sum, item) => sum + item.deals, 0);

  return (
    <Grid container spacing={gridSpacing}>
      {/* Header Section */}
      <Grid size={{ xs: 12 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
          <Typography variant="h3" component="h1">
            Lead Summary
          </Typography>
          
          {/* Navigation Tabs */}
          <Tabs
            value={tabValue}
            onChange={handleTabChange}
            variant="scrollable"
            scrollButtons="auto"
            sx={{
              '& .MuiTab-root': {
                minHeight: 48,
                textTransform: 'none',
                fontSize: '0.875rem',
                fontWeight: 500,
              },
              '& .Mui-selected': {
                color: theme.palette.primary.main,
              },
              '& .MuiTabs-indicator': {
                backgroundColor: theme.palette.primary.main,
                height: 3,
              },
            }}
          >
            <Tab
              icon={<PersonIcon />}
              label="Customer"
              iconPosition="start"
            />
            <Tab
              icon={<CheckCircleIcon />}
              label="Complete"
              iconPosition="start"
            />
            <Tab
              icon={<TrendingDownIcon />}
              label="Loss Lead"
              iconPosition="start"
            />
            <Tab
              icon={<PersonAddIcon />}
              label="New Lead"
              iconPosition="start"
            />
          </Tabs>
        </Box>
      </Grid>

      {/* Total Growth Section */}
      <Grid size={{ xs: 12 }}>
        <MainCard>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
            <Typography variant="h4" component="h2">
              Total Growth
            </Typography>
            <FormControl size="small" sx={{ minWidth: 120 }}>
              <Select
                value={timeframe}
                onChange={handleTimeframeChange}
                displayEmpty
              >
                <MenuItem value="Today">Today</MenuItem>
                <MenuItem value="Week">This Week</MenuItem>
                <MenuItem value="Month">This Month</MenuItem>
                <MenuItem value="Year">This Year</MenuItem>
              </Select>
            </FormControl>
          </Box>
          
          <Typography variant="h2" component="div" sx={{ mb: 3, fontWeight: 'bold', color: theme.palette.primary.main }}>
            ${totalRevenue.toLocaleString()}
          </Typography>

          {/* Simple Chart Representation */}
          <Box sx={{ height: 200, position: 'relative', mb: 2 }}>
            {/* Chart Background */}
            <Box sx={{ 
              position: 'absolute', 
              bottom: 0, 
              left: 0, 
              right: 0, 
              height: '100%',
              display: 'flex',
              alignItems: 'end',
              justifyContent: 'space-between',
              px: 2
            }}>
              {monthlyData.map((item, index) => (
                <Box key={index} sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', flex: 1 }}>
                  {/* Bar */}
                  <Box sx={{
                    width: '80%',
                    height: `${(item.value / 350) * 100}%`,
                    backgroundColor: 'rgba(255, 193, 7, 0.3)',
                    borderRadius: '4px 4px 0 0',
                    position: 'relative',
                    mb: 1,
                    transition: 'all 0.3s ease',
                    '&:hover': {
                      backgroundColor: 'rgba(255, 193, 7, 0.5)',
                      transform: 'scale(1.05)'
                    }
                  }}>
                    {/* Line overlay */}
                    <Box sx={{
                      position: 'absolute',
                      top: '50%',
                      left: '50%',
                      transform: 'translate(-50%, -50%)',
                      width: '2px',
                      height: '100%',
                      backgroundColor: '#FFC107',
                      borderRadius: '1px'
                    }} />
                    {/* Revenue tooltip */}
                    <Box sx={{
                      position: 'absolute',
                      top: '-30px',
                      left: '50%',
                      transform: 'translateX(-50%)',
                      backgroundColor: 'rgba(0,0,0,0.8)',
                      color: 'white',
                      padding: '2px 6px',
                      borderRadius: '4px',
                      fontSize: '0.7rem',
                      opacity: 0,
                      transition: 'opacity 0.3s ease',
                      '&:hover': {
                        opacity: 1
                      }
                    }}>
                      ${item.revenue.toLocaleString()}
                    </Box>
                  </Box>
                  {/* Month label */}
                  <Typography variant="caption" sx={{ fontSize: '0.75rem' }}>
                    {item.month}
                  </Typography>
                </Box>
              ))}
            </Box>
          </Box>
        </MainCard>
      </Grid>

      {/* Key Metrics Cards */}
      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <Card sx={{ 
          background: 'linear-gradient(135deg, #1976d2 0%, #42a5f5 100%)',
          color: 'white',
          position: 'relative',
          overflow: 'hidden'
        }}>
          <CardContent>
            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <Box>
                <Typography variant="h4" sx={{ fontWeight: 'bold', mb: 1 }}>
                  ${(totalRevenue / 1000).toFixed(0)}k
                </Typography>
                <Typography variant="body2" sx={{ opacity: 0.9 }}>
                  Total Income
                </Typography>
              </Box>
              <AttachMoneyIcon sx={{ fontSize: 40, opacity: 0.8 }} />
            </Box>
          </CardContent>
        </Card>
      </Grid>

      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <Card sx={{ 
          background: 'linear-gradient(135deg, #f8bbd9 0%, #fce4ec 100%)',
          color: '#c2185b',
          position: 'relative',
          overflow: 'hidden'
        }}>
          <CardContent>
            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <Box>
                <Typography variant="h4" sx={{ fontWeight: 'bold', mb: 1 }}>
                  {totalDeals}
                </Typography>
                <Typography variant="body2" sx={{ opacity: 0.8 }}>
                  Meeting attends
                </Typography>
              </Box>
              <DescriptionIcon sx={{ fontSize: 40, opacity: 0.8 }} />
            </Box>
          </CardContent>
        </Card>
      </Grid>

      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <Card sx={{ 
          background: 'linear-gradient(135deg, #fff3e0 0%, #ffe0b2 100%)',
          color: '#f57c00',
          position: 'relative',
          overflow: 'hidden'
        }}>
          <CardContent>
            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <Box>
                <Typography variant="h4" sx={{ fontWeight: 'bold', mb: 1 }}>
                  {Math.round((totalRevenue / monthlyData.length) / 1000)}k
                </Typography>
                <Typography variant="body2" sx={{ opacity: 0.8 }}>
                  Sales improve
                </Typography>
              </Box>
              <StoreIcon sx={{ fontSize: 40, opacity: 0.8 }} />
            </Box>
          </CardContent>
        </Card>
      </Grid>

      <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
        <Card sx={{ 
          background: 'linear-gradient(135deg, #fff3e0 0%, #ffe0b2 100%)',
          color: '#f57c00',
          position: 'relative',
          overflow: 'hidden'
        }}>
          <CardContent>
            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <Box>
                <Typography variant="h4" sx={{ fontWeight: 'bold', mb: 1 }}>
                  {totalLeads}
                </Typography>
                <Typography variant="body2" sx={{ opacity: 0.8 }}>
                  New users
                </Typography>
              </Box>
              <GroupAddIcon sx={{ fontSize: 40, opacity: 0.8 }} />
            </Box>
          </CardContent>
        </Card>
      </Grid>

      {/* Lead Source Section */}
      <Grid size={{ xs: 12, md: 6 }}>
        <MainCard>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
            <Typography variant="h5" component="h3">
              Lead Source
            </Typography>
            <FormControl size="small" sx={{ minWidth: 100 }}>
              <Select
                value={timeframe}
                onChange={handleTimeframeChange}
                displayEmpty
              >
                <MenuItem value="Today">Today</MenuItem>
                <MenuItem value="Week">This Week</MenuItem>
                <MenuItem value="Month">This Month</MenuItem>
              </Select>
            </FormControl>
          </Box>

          {/* Radar Chart Representation */}
          <Box sx={{ height: 200, display: 'flex', alignItems: 'center', justifyContent: 'center', mb: 3 }}>
            <Box sx={{ 
              width: 150, 
              height: 150, 
              borderRadius: '50%',
              background: 'conic-gradient(from 0deg, #7b1fa2 0deg 90deg, #1976d2 90deg 180deg, #9c27b0 180deg 270deg, #424242 270deg 360deg)',
              position: 'relative',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}>
              <Box sx={{ 
                width: 100, 
                height: 100, 
                borderRadius: '50%',
                backgroundColor: 'white',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <Typography variant="h6" sx={{ fontWeight: 'bold' }}>
                  Leads
                </Typography>
              </Box>
            </Box>
          </Box>

          {/* Legend */}
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {leadSourceData.map((item, index) => (
              <Box key={index} sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <Box sx={{ 
                  width: 12, 
                  height: 12, 
                  borderRadius: '50%', 
                  backgroundColor: item.color 
                }} />
                <Typography variant="body2" sx={{ flex: 1 }}>
                  {item.name}
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 'bold' }}>
                  {item.value} ({item.percentage}%)
                </Typography>
              </Box>
            ))}
          </Box>
        </MainCard>
      </Grid>

      {/* Sales Performance Section */}
      <Grid size={{ xs: 12, md: 6 }}>
        <MainCard>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
            <Typography variant="h5" component="h3">
              Sales Performance
            </Typography>
            <IconButton>
              <MenuIcon />
            </IconButton>
          </Box>

          {/* KPI Cards */}
          <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap' }}>
            <Chip label={`${Math.round((totalDeals / totalLeads) * 100)}% Conversion Rate`} color="primary" variant="outlined" />
            <Chip label={`$${Math.round(totalRevenue / totalDeals).toLocaleString()} Average Deal`} color="secondary" variant="outlined" />
            <Chip label={`${Math.round(totalLeads * 1.2)} Sales Target`} color="success" variant="outlined" />
          </Box>

          {/* Stacked Bar Chart */}
          <Box sx={{ height: 200, position: 'relative' }}>
            <Box sx={{ 
              position: 'absolute', 
              bottom: 0, 
              left: 0, 
              right: 0, 
              height: '100%',
              display: 'flex',
              alignItems: 'end',
              justifyContent: 'space-between',
              px: 1
            }}>
              {salesData.map((item, index) => (
                <Box key={index} sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', flex: 1 }}>
                  {/* Stacked Bar */}
                  <Box sx={{ width: '80%', height: `${(item.value / 400) * 100}%`, mb: 1 }}>
                    <Box sx={{ 
                      height: '33%', 
                      backgroundColor: '#1976d2',
                      borderRadius: '4px 4px 0 0'
                    }} />
                    <Box sx={{ 
                      height: '33%', 
                      backgroundColor: '#42a5f5'
                    }} />
                    <Box sx={{ 
                      height: '34%', 
                      backgroundColor: '#90caf9',
                      borderRadius: '0 0 4px 4px'
                    }} />
                  </Box>
                  {/* Day label */}
                  <Typography variant="caption" sx={{ fontSize: '0.75rem' }}>
                    {item.day}
                  </Typography>
                </Box>
              ))}
            </Box>
          </Box>
        </MainCard>
      </Grid>

      {/* Upcoming Task & Follow-ups */}
      <Grid size={{ xs: 12, md: 6 }}>
        <MainCard>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Box>
              <Typography variant="h5" component="h3" sx={{ mb: 1 }}>
                Upcoming Task & Follow-ups
              </Typography>
              <Typography variant="h3" sx={{ fontWeight: 'bold', color: theme.palette.primary.main }}>
                {Math.round(totalLeads * 0.8)}
              </Typography>
            </Box>
            <Button variant="contained" color="primary">
              Follow-up
            </Button>
          </Box>
        </MainCard>
      </Grid>
    </Grid>
  );
};

export default CrmLeadSummary;
