'use client';

import React, { useState, useEffect } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Chip,
  Grid,
  IconButton,
  Stack,
  Typography,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Avatar,
  Divider,
} from '@mui/material';
import {
  Person as PersonIcon,
  AdminPanelSettings as AdminIcon,
  Login as LoginIcon,
  Logout as LogoutIcon,
} from '@mui/icons-material';
import { mockAuthService, MockAuthResponse } from '@/services/mockAuthService';
import { MOCK_USERS, MockUser } from '@/data/mockUsers';
import { useSupabaseAuth } from '@/contexts/SupabaseAuthContext';
import { authConfig } from '@/config/authConfig';

/**
 * Mock User Tester Component
 * DEV-ONLY: This component should only be used in development mode
 */
const MockUserTester: React.FC = () => {
  // Verify we're in dev mode
  if (!authConfig.shouldUseMockAuth()) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">
          Mock User Tester is only available in development mode with mock authentication enabled.
        </Alert>
      </Box>
    );
  }
  const { user, isAuthenticated, refreshUser, isLoading } = useSupabaseAuth();
  const [currentUser, setCurrentUser] = useState<MockUser | null>(mockAuthService.getCurrentUser());
  const [isLoggedIn, setIsLoggedIn] = useState(mockAuthService.isAuthenticated());
  const [loginDialogOpen, setLoginDialogOpen] = useState(false);
  const [alert, setAlert] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // Debug logging
  console.log('🔍 MockUserTester - Main auth context:', {
    user: user ? `${user.firstName} ${user.lastName} (${user.role})` : 'null',
    isAuthenticated,
    isLoading,
    currentUser: currentUser ? `${currentUser.firstName} ${currentUser.lastName} (${currentUser.role})` : 'null',
    isLoggedIn
  });

  // Sync local state with main authentication context
  useEffect(() => {
    // Don't sync while authentication context is still loading
    if (isLoading) {
      console.log('🔄 Authentication context still loading, skipping sync');
      return;
    }

    if (isAuthenticated && user) {
      // Find the corresponding MockUser from the MOCK_USERS data
      const mockUser = MOCK_USERS.find(mu => mu.email === user.email);
      if (mockUser) {
        setCurrentUser(mockUser);
        setIsLoggedIn(true);
        console.log('🔄 Synced local state with main auth context');
      } else {
        console.log('⚠️ MockUser not found for authenticated user:', user.email);
      }
    } else if (!isAuthenticated) {
      setCurrentUser(null);
      setIsLoggedIn(false);
      console.log('🔄 Cleared local state - not authenticated');
    }
  }, [isAuthenticated, user, isLoading]);

  const handleLogin = async (user: MockUser) => {
    try {
      const credentials = Object.values({
        admin: { email: 'admin@easyluxury.com', password: 'admin123' },
        owner: { email: 'john.doe@example.com', password: 'owner123' },
        agencyOwner: { email: 'sarah.wilson@luxuryrealty.com', password: 'agency123' },
        agencyMember: { email: 'mike.chen@luxuryrealty.com', password: 'member123' },
        tenant: { email: 'emma.johnson@example.com', password: 'tenant123' },
      }).find(c => c.email === user.email);

      if (!credentials) {
        throw new Error('Credentials not found for this user');
      }

      await mockAuthService.signIn(credentials.email, credentials.password);
      setCurrentUser(user);
      setIsLoggedIn(true);
      setLoginDialogOpen(false);
      setAlert({ type: 'success', message: `Logged in as ${user.firstName} ${user.lastName} (${user.role})` });
      
      // Refresh the main authentication context
      await refreshUser();
    } catch (error: any) {
      setAlert({ type: 'error', message: error.message });
    }
  };

  const handleLogout = async () => {
    try {
      await mockAuthService.signOut();
      setCurrentUser(null);
      setIsLoggedIn(false);
      setAlert({ type: 'success', message: 'Logged out successfully' });
      
      // Refresh the main authentication context
      await refreshUser();
    } catch (error: any) {
      setAlert({ type: 'error', message: 'Logout failed' });
    }
  };

  const handleQuickLogin = async (role: MockUser['role']) => {
    try {
      let response: MockAuthResponse;
      
      switch (role) {
        case 'ADMIN':
          response = await mockAuthService.loginAsAdmin();
          break;
        // Only ADMIN role is available
        default:
          throw new Error('Invalid role');
      }

      setCurrentUser(response.user);
      setIsLoggedIn(true);
      setAlert({ type: 'success', message: `Quick login as ${response.user.role} successful` });
      
      // Refresh the main authentication context
      await refreshUser();
    } catch (error: any) {
      setAlert({ type: 'error', message: error.message });
    }
  };

  const getRoleIcon = (role: MockUser['role']) => {
    switch (role) {
      case 'ADMIN':
        return <AdminIcon />;
      default:
        return <PersonIcon />;
    }
  };

  const getRoleColor = (role: MockUser['role']) => {
    switch (role) {
      case 'ADMIN':
        return 'error';
      default:
        return 'default';
    }
  };

  const groupedUsers = MOCK_USERS.reduce((acc, user) => {
    if (!acc[user.role]) {
      acc[user.role] = [];
    }
    acc[user.role].push(user);
    return acc;
  }, {} as Record<MockUser['role'], MockUser[]>);

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" gutterBottom>
        Mock User Testing Panel
      </Typography>
      
      {alert && (
        <Alert 
          severity={alert.type} 
          onClose={() => setAlert(null)}
          sx={{ mb: 2 }}
        >
          {alert.message}
        </Alert>
      )}

      {/* Current User Status */}
      <Card sx={{ mb: 3 }}>
        <CardHeader 
          title="Current Authentication Status"
          avatar={<PersonIcon />}
        />
        <CardContent>
          <Stack spacing={2}>
            {/* Mock Auth Status */}
            <Box>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                Mock Authentication Status:
              </Typography>
              {isLoggedIn && currentUser ? (
                <Stack direction="row" spacing={2} alignItems="center">
                  <Avatar {...(currentUser.avatar && { src: currentUser.avatar })} />
                  <Box>
                    <Typography variant="h6">
                      {currentUser.firstName} {currentUser.lastName}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {currentUser.email}
                    </Typography>
                    <Chip 
                      icon={getRoleIcon(currentUser.role)}
                      label={currentUser.role}
                      color={getRoleColor(currentUser.role) as any}
                      size="small"
                      sx={{ mt: 1 }}
                    />
                  </Box>
                  <Button
                    variant="outlined"
                    startIcon={<LogoutIcon />}
                    onClick={handleLogout}
                    color="error"
                  >
                    Logout
                  </Button>
                </Stack>
              ) : (
                <Stack direction="row" spacing={2} alignItems="center">
                  <Typography variant="body1" color="text.secondary">
                    Not logged in
                  </Typography>
                  <Button
                    variant="contained"
                    startIcon={<LoginIcon />}
                    onClick={() => setLoginDialogOpen(true)}
                  >
                    Login
                  </Button>
                </Stack>
              )}
            </Box>
            
            {/* Main Auth Context Status */}
            <Box>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                Main Authentication Context:
              </Typography>
              <Typography variant="body2" color={isAuthenticated ? 'success.main' : 'error.main'}>
                {isAuthenticated ? `✅ Authenticated${user ? ` as ${user.firstName} ${user.lastName}` : ''}` : '❌ Not authenticated'}
              </Typography>
            </Box>
          </Stack>
        </CardContent>
      </Card>

      {/* Quick Login Buttons */}
      <Card sx={{ mb: 3 }}>
        <CardHeader title="Quick Login by Role" />
        <CardContent>
          <Stack direction="row" spacing={2} flexWrap="wrap">
            {Object.keys(groupedUsers).map((role) => (
              <Button
                key={role}
                variant="outlined"
                startIcon={getRoleIcon(role as MockUser['role'])}
                onClick={() => handleQuickLogin(role as MockUser['role'])}
                disabled={isLoggedIn}
                color={getRoleColor(role as MockUser['role']) as any}
              >
                Login as {role.replace('_', ' ')}
              </Button>
            ))}
          </Stack>
        </CardContent>
      </Card>

      {/* Mock Users List */}
      <Card>
        <CardHeader title="Available Mock Users" />
        <CardContent>
          {Object.entries(groupedUsers).map(([role, users]) => (
            <Box key={role} sx={{ mb: 3 }}>
              <Typography variant="h6" gutterBottom>
                {role.replace('_', ' ')} ({users.length})
              </Typography>
              <Grid container spacing={2}>
                {users.map((user) => (
                  <Grid size={{ xs: 12, sm: 6, md: 4 }} key={user.id}>
                    <Card variant="outlined">
                      <CardContent>
                        <Stack direction="row" spacing={2} alignItems="center">
                          <Avatar {...(user.avatar && { src: user.avatar })} />
                          <Box sx={{ flexGrow: 1 }}>
                            <Typography variant="subtitle1">
                              {user.firstName} {user.lastName}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              {user.email}
                            </Typography>
                            <Chip 
                              icon={getRoleIcon(user.role)}
                              label={user.role}
                              color={getRoleColor(user.role) as any}
                              size="small"
                              sx={{ mt: 1 }}
                            />
                          </Box>
                          <IconButton
                            onClick={() => handleLogin(user)}
                            disabled={isLoggedIn}
                            color="primary"
                          >
                            <LoginIcon />
                          </IconButton>
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>
                ))}
              </Grid>
              <Divider sx={{ mt: 2 }} />
            </Box>
          ))}
        </CardContent>
      </Card>

      {/* Login Dialog */}
      <Dialog open={loginDialogOpen} onClose={() => setLoginDialogOpen(false)}>
        <DialogTitle>Select User to Login</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Choose a mock user to login with:
          </Typography>
          <Stack spacing={1}>
            {MOCK_USERS.map((user) => (
              <Button
                key={user.id}
                variant="outlined"
                fullWidth
                startIcon={<Avatar sx={{ width: 24, height: 24 }}>{user.avatar ? <img src={user.avatar} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} /> : user.firstName[0]}</Avatar>}
                onClick={() => handleLogin(user)}
                sx={{ justifyContent: 'flex-start' }}
              >
                {user.firstName} {user.lastName} ({user.role})
              </Button>
            ))}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setLoginDialogOpen(false)}>Cancel</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default MockUserTester;
