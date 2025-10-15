'use client';

// material-ui
import { Typography, Card, CardContent, Box, Stack } from '@mui/material';

// ==============================|| ADMIN DASHBOARD ||============================== //

export default function AdminDashboard() {
  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" gutterBottom>
        Admin Dashboard
      </Typography>
      <Typography variant="body1" sx={{ mb: 3 }}>
        Welcome to the admin dashboard. This is a simplified version of the application with basic admin functionality.
      </Typography>
      
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={3}>
        <Card sx={{ flex: 1 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              System Status
            </Typography>
            <Typography variant="body2" color="text.secondary">
              All systems operational
            </Typography>
          </CardContent>
        </Card>
        
        <Card sx={{ flex: 1 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              User Management
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Admin users only
            </Typography>
          </CardContent>
        </Card>
        
        <Card sx={{ flex: 1 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Configuration
            </Typography>
            <Typography variant="body2" color="text.secondary">
              System settings
            </Typography>
          </CardContent>
        </Card>
      </Stack>
    </Box>
  );
}