import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Chip,
  LinearProgress,
  IconButton,
  Tooltip,
  Alert,
  CircularProgress,
} from '@mui/material';
import {
  Security as SecurityIcon,
  Warning as WarningIcon,
  CheckCircle as CheckCircleIcon,
  Error as ErrorIcon,
  Refresh as RefreshIcon,
} from '@mui/icons-material';

interface SecurityMetrics {
  totalEvents: number;
  blockedEvents: number;
  blockRate: number;
  uniqueUsers: number;
  highRiskEvents: number;
  mediumRiskEvents: number;
  lowRiskEvents: number;
  criticalEvents: number;
  averageResponseTime: number;
  threatTypes: Record<string, number>;
  recentEvents: SecurityEvent[];
}

interface SecurityEvent {
  eventId: string;
  userId: string;
  eventType: string;
  threatsDetected: string[];
  securityScore: number;
  severity: string;
  timestamp: string;
  status: string;
}

interface SecurityDashboardProps {
  refreshInterval?: number;
  onEventClick?: (event: SecurityEvent) => void;
  onRefresh?: () => void;
}

const SecurityDashboard: React.FC<SecurityDashboardProps> = ({
  refreshInterval = 30000,
  onEventClick,
  onRefresh,
}) => {
  const [metrics, setMetrics] = useState<SecurityMetrics | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadMetrics();
    
    if (refreshInterval > 0) {
      const interval = setInterval(loadMetrics, refreshInterval);
      return () => clearInterval(interval);
    }
    
    return () => {};
  }, [refreshInterval]);

  const loadMetrics = async () => {
    setLoading(true);
    try {
      // This would be replaced with actual API calls
      const mockMetrics: SecurityMetrics = {
        totalEvents: 1250,
        blockedEvents: 45,
        blockRate: 0.036,
        uniqueUsers: 89,
        highRiskEvents: 12,
        mediumRiskEvents: 28,
        lowRiskEvents: 1200,
        criticalEvents: 3,
        averageResponseTime: 150,
        threatTypes: {
          'INJECTION_ATTACK': 15,
          'PROMPT_INJECTION': 8,
          'DATA_EXFILTRATION': 12,
          'SYSTEM_MANIPULATION': 5,
          'SENSITIVE_DATA_EXPOSURE': 3,
        },
        recentEvents: [
          {
            eventId: 'SEC_001',
            userId: 'user123',
            eventType: 'BLOCKED_REQUEST',
            threatsDetected: ['INJECTION_ATTACK'],
            securityScore: 25,
            severity: 'HIGH',
            timestamp: new Date().toISOString(),
            status: 'ACTIVE',
          },
          {
            eventId: 'SEC_002',
            userId: 'user456',
            eventType: 'SECURITY_CHECK',
            threatsDetected: [],
            securityScore: 85,
            severity: 'LOW',
            timestamp: new Date(Date.now() - 300000).toISOString(),
            status: 'RESOLVED',
          },
        ],
      };
      
      setMetrics(mockMetrics);
      setError(null);
    } catch (err) {
      setError('Failed to load security metrics');
      console.error('Error loading security metrics:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleRefresh = () => {
    loadMetrics();
    onRefresh?.();
  };

  const getSeverityColor = (severity: string) => {
    switch (severity) {
      case 'CRITICAL':
        return 'error';
      case 'HIGH':
        return 'warning';
      case 'MEDIUM':
        return 'info';
      case 'LOW':
        return 'success';
      default:
        return 'default';
    }
  };

  const getThreatColor = (count: number, total: number) => {
    const percentage = (count / total) * 100;
    if (percentage > 20) return 'error';
    if (percentage > 10) return 'warning';
    if (percentage > 5) return 'info';
    return 'success';
  };

  if (loading && !metrics) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="200px">
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <Alert severity="error" action={
        <IconButton color="inherit" size="small" onClick={handleRefresh}>
          <RefreshIcon />
        </IconButton>
      }>
        {error}
      </Alert>
    );
  }

  if (!metrics) {
    return null;
  }

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h5" component="h2">
          Security Dashboard
        </Typography>
        <Tooltip title="Refresh">
          <IconButton onClick={handleRefresh} disabled={loading}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
      </Box>

      <Grid container spacing={3}>
        {/* Key Metrics */}
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography color="textSecondary" gutterBottom>
                    Total Events
                  </Typography>
                  <Typography variant="h4">
                    {metrics.totalEvents.toLocaleString()}
                  </Typography>
                </Box>
                <SecurityIcon color="primary" sx={{ fontSize: 40 }} />
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography color="textSecondary" gutterBottom>
                    Blocked Events
                  </Typography>
                  <Typography variant="h4" color="error">
                    {metrics.blockedEvents.toLocaleString()}
                  </Typography>
                </Box>
                <WarningIcon color="error" sx={{ fontSize: 40 }} />
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography color="textSecondary" gutterBottom>
                    Block Rate
                  </Typography>
                  <Typography variant="h4">
                    {(metrics.blockRate * 100).toFixed(1)}%
                  </Typography>
                  <LinearProgress
                    variant="determinate"
                    value={metrics.blockRate * 100}
                    sx={{ mt: 1 }}
                  />
                </Box>
                <ErrorIcon color="warning" sx={{ fontSize: 40 }} />
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography color="textSecondary" gutterBottom>
                    Unique Users
                  </Typography>
                  <Typography variant="h4">
                    {metrics.uniqueUsers.toLocaleString()}
                  </Typography>
                </Box>
                <CheckCircleIcon color="success" sx={{ fontSize: 40 }} />
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* Risk Level Distribution */}
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Risk Level Distribution
              </Typography>
              <Grid container spacing={2}>
                <Grid size={{ xs: 6 }}>
                  <Box display="flex" alignItems="center" gap={1}>
                    <Chip label="Critical" color="error" size="small" />
                    <Typography variant="h6">{metrics.criticalEvents}</Typography>
                  </Box>
                </Grid>
                <Grid size={{ xs: 6 }}>
                  <Box display="flex" alignItems="center" gap={1}>
                    <Chip label="High" color="warning" size="small" />
                    <Typography variant="h6">{metrics.highRiskEvents}</Typography>
                  </Box>
                </Grid>
                <Grid size={{ xs: 6 }}>
                  <Box display="flex" alignItems="center" gap={1}>
                    <Chip label="Medium" color="info" size="small" />
                    <Typography variant="h6">{metrics.mediumRiskEvents}</Typography>
                  </Box>
                </Grid>
                <Grid size={{ xs: 6 }}>
                  <Box display="flex" alignItems="center" gap={1}>
                    <Chip label="Low" color="success" size="small" />
                    <Typography variant="h6">{metrics.lowRiskEvents}</Typography>
                  </Box>
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>

        {/* Performance Metrics */}
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Performance Metrics
              </Typography>
              <Box mb={2}>
                <Typography variant="body2" color="textSecondary">
                  Average Response Time
                </Typography>
                <Typography variant="h5">
                  {metrics.averageResponseTime}ms
                </Typography>
                <LinearProgress
                  variant="determinate"
                  value={Math.min((metrics.averageResponseTime / 500) * 100, 100)}
                  sx={{ mt: 1 }}
                />
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* Threat Types */}
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Threat Types
              </Typography>
              <Box display="flex" flexDirection="column" gap={1}>
                {Object.entries(metrics.threatTypes).map(([threat, count]) => (
                  <Box key={threat} display="flex" justifyContent="space-between" alignItems="center">
                    <Typography variant="body2">{threat.replace(/_/g, ' ')}</Typography>
                    <Box display="flex" alignItems="center" gap={1}>
                      <Typography variant="body2">{count}</Typography>
                      <Chip
                        label={`${((count / metrics.totalEvents) * 100).toFixed(1)}%`}
                        color={getThreatColor(count, metrics.totalEvents) as any}
                        size="small"
                      />
                    </Box>
                  </Box>
                ))}
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* Recent Events */}
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Recent Events
              </Typography>
              <Box display="flex" flexDirection="column" gap={1}>
                {metrics.recentEvents.map((event) => (
                  <Box
                    key={event.eventId}
                    display="flex"
                    justifyContent="space-between"
                    alignItems="center"
                    p={1}
                    sx={{
                      border: '1px solid',
                      borderColor: 'divider',
                      borderRadius: 1,
                      cursor: 'pointer',
                      '&:hover': {
                        backgroundColor: 'action.hover',
                      },
                    }}
                    onClick={() => onEventClick?.(event)}
                  >
                    <Box>
                      <Typography variant="body2" fontWeight="bold">
                        {event.eventType}
                      </Typography>
                      <Typography variant="caption" color="textSecondary">
                        {event.userId} • {new Date(event.timestamp).toLocaleString()}
                      </Typography>
                    </Box>
                    <Box display="flex" alignItems="center" gap={1}>
                      <Chip
                        label={event.severity}
                        color={getSeverityColor(event.severity) as any}
                        size="small"
                      />
                      <Typography variant="body2">
                        {event.securityScore}
                      </Typography>
                    </Box>
                  </Box>
                ))}
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};

export default SecurityDashboard;