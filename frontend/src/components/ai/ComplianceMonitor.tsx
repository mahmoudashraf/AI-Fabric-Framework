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
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  Divider,
} from '@mui/material';
import {
  Gavel as GavelIcon,
  CheckCircle as CheckCircleIcon,
  Error as ErrorIcon,
  Warning as WarningIcon,
  Refresh as RefreshIcon,
  Assessment as AssessmentIcon,
  TrendingUp as TrendingUpIcon,
  TrendingDown as TrendingDownIcon,
  Security as SecurityIcon,
} from '@mui/icons-material';

interface ComplianceMetrics {
  totalReports: number;
  compliantReports: number;
  complianceRate: number;
  totalAuditLogs: number;
  dataPrivacyCompliance: number;
  regulatoryCompliance: number;
  auditCompliance: number;
  retentionCompliance: number;
  violationsByType: Record<string, number>;
  complianceTrend: 'up' | 'down' | 'stable';
  recentViolations: ComplianceViolation[];
  upcomingReviews: ComplianceReview[];
}

interface ComplianceViolation {
  id: string;
  type: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  description: string;
  timestamp: string;
  status: 'ACTIVE' | 'RESOLVED' | 'IGNORED';
  regulation: string;
  userId: string;
}

interface ComplianceReview {
  id: string;
  title: string;
  dueDate: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';
  assignedTo: string;
  regulation: string;
}

interface ComplianceMonitorProps {
  refreshInterval?: number;
  onViolationClick?: (violation: ComplianceViolation) => void;
  onReviewClick?: (review: ComplianceReview) => void;
  onRefresh?: () => void;
}

const ComplianceMonitor: React.FC<ComplianceMonitorProps> = ({
  refreshInterval = 30000,
  onViolationClick,
  onReviewClick,
  onRefresh,
}) => {
  const [metrics, setMetrics] = useState<ComplianceMetrics | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadMetrics();
    
    if (refreshInterval > 0) {
      const interval = setInterval(loadMetrics, refreshInterval);
      return () => clearInterval(interval);
    }
  }, [refreshInterval]);

  const loadMetrics = async () => {
    setLoading(true);
    try {
      // This would be replaced with actual API calls
      const mockMetrics: ComplianceMetrics = {
        totalReports: 450,
        compliantReports: 380,
        complianceRate: 0.844,
        totalAuditLogs: 12500,
        dataPrivacyCompliance: 0.92,
        regulatoryCompliance: 0.78,
        auditCompliance: 0.95,
        retentionCompliance: 0.88,
        violationsByType: {
          'DATA_PRIVACY_VIOLATION': 12,
          'REGULATORY_VIOLATION': 8,
          'AUDIT_VIOLATION': 3,
          'RETENTION_VIOLATION': 5,
        },
        complianceTrend: 'up',
        recentViolations: [
          {
            id: 'VIOL_001',
            type: 'DATA_PRIVACY_VIOLATION',
            severity: 'HIGH',
            description: 'Personal data processed without explicit consent',
            timestamp: new Date().toISOString(),
            status: 'ACTIVE',
            regulation: 'GDPR',
            userId: 'user123',
          },
          {
            id: 'VIOL_002',
            type: 'REGULATORY_VIOLATION',
            severity: 'MEDIUM',
            description: 'Missing audit trail for financial transactions',
            timestamp: new Date(Date.now() - 3600000).toISOString(),
            status: 'RESOLVED',
            regulation: 'SOX',
            userId: 'user456',
          },
        ],
        upcomingReviews: [
          {
            id: 'REV_001',
            title: 'GDPR Compliance Review',
            dueDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
            priority: 'HIGH',
            status: 'PENDING',
            assignedTo: 'compliance@company.com',
            regulation: 'GDPR',
          },
          {
            id: 'REV_002',
            title: 'SOX Audit Preparation',
            dueDate: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString(),
            priority: 'CRITICAL',
            status: 'IN_PROGRESS',
            assignedTo: 'audit@company.com',
            regulation: 'SOX',
          },
        ],
      };
      
      setMetrics(mockMetrics);
      setError(null);
    } catch (err) {
      setError('Failed to load compliance metrics');
      console.error('Error loading compliance metrics:', err);
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

  const getPriorityColor = (priority: string) => {
    switch (priority) {
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

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'ACTIVE':
        return 'error';
      case 'RESOLVED':
        return 'success';
      case 'IGNORED':
        return 'default';
      case 'PENDING':
        return 'warning';
      case 'IN_PROGRESS':
        return 'info';
      case 'COMPLETED':
        return 'success';
      default:
        return 'default';
    }
  };

  const getComplianceColor = (rate: number) => {
    if (rate >= 0.9) return 'success';
    if (rate >= 0.7) return 'warning';
    return 'error';
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
          Compliance Monitor
        </Typography>
        <Tooltip title="Refresh">
          <IconButton onClick={handleRefresh} disabled={loading}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
      </Box>

      <Grid container spacing={3}>
        {/* Key Metrics */}
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography color="textSecondary" gutterBottom>
                    Total Reports
                  </Typography>
                  <Typography variant="h4">
                    {metrics.totalReports.toLocaleString()}
                  </Typography>
                </Box>
                <AssessmentIcon color="primary" sx={{ fontSize: 40 }} />
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography color="textSecondary" gutterBottom>
                    Compliant Reports
                  </Typography>
                  <Typography variant="h4" color="success.main">
                    {metrics.compliantReports.toLocaleString()}
                  </Typography>
                </Box>
                <CheckCircleIcon color="success" sx={{ fontSize: 40 }} />
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography color="textSecondary" gutterBottom>
                    Compliance Rate
                  </Typography>
                  <Typography variant="h4">
                    {(metrics.complianceRate * 100).toFixed(1)}%
                  </Typography>
                  <LinearProgress
                    variant="determinate"
                    value={metrics.complianceRate * 100}
                    color={getComplianceColor(metrics.complianceRate) as any}
                    sx={{ mt: 1 }}
                  />
                </Box>
                <GavelIcon color="primary" sx={{ fontSize: 40 }} />
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography color="textSecondary" gutterBottom>
                    Audit Logs
                  </Typography>
                  <Typography variant="h4">
                    {metrics.totalAuditLogs.toLocaleString()}
                  </Typography>
                </Box>
                <SecurityIcon color="warning" sx={{ fontSize: 40 }} />
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* Compliance Breakdown */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Compliance Breakdown
              </Typography>
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Box mb={2}>
                    <Typography variant="body2" color="textSecondary">
                      Data Privacy
                    </Typography>
                    <Typography variant="h6">
                      {(metrics.dataPrivacyCompliance * 100).toFixed(1)}%
                    </Typography>
                    <LinearProgress
                      variant="determinate"
                      value={metrics.dataPrivacyCompliance * 100}
                      color={getComplianceColor(metrics.dataPrivacyCompliance) as any}
                      sx={{ mt: 1 }}
                    />
                  </Box>
                </Grid>
                <Grid item xs={6}>
                  <Box mb={2}>
                    <Typography variant="body2" color="textSecondary">
                      Regulatory
                    </Typography>
                    <Typography variant="h6">
                      {(metrics.regulatoryCompliance * 100).toFixed(1)}%
                    </Typography>
                    <LinearProgress
                      variant="determinate"
                      value={metrics.regulatoryCompliance * 100}
                      color={getComplianceColor(metrics.regulatoryCompliance) as any}
                      sx={{ mt: 1 }}
                    />
                  </Box>
                </Grid>
                <Grid item xs={6}>
                  <Box mb={2}>
                    <Typography variant="body2" color="textSecondary">
                      Audit
                    </Typography>
                    <Typography variant="h6">
                      {(metrics.auditCompliance * 100).toFixed(1)}%
                    </Typography>
                    <LinearProgress
                      variant="determinate"
                      value={metrics.auditCompliance * 100}
                      color={getComplianceColor(metrics.auditCompliance) as any}
                      sx={{ mt: 1 }}
                    />
                  </Box>
                </Grid>
                <Grid item xs={6}>
                  <Box mb={2}>
                    <Typography variant="body2" color="textSecondary">
                      Retention
                    </Typography>
                    <Typography variant="h6">
                      {(metrics.retentionCompliance * 100).toFixed(1)}%
                    </Typography>
                    <LinearProgress
                      variant="determinate"
                      value={metrics.retentionCompliance * 100}
                      color={getComplianceColor(metrics.retentionCompliance) as any}
                      sx={{ mt: 1 }}
                    />
                  </Box>
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>

        {/* Violations by Type */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Violations by Type
              </Typography>
              <Box display="flex" flexDirection="column" gap={1}>
                {Object.entries(metrics.violationsByType).map(([type, count]) => (
                  <Box key={type} display="flex" justifyContent="space-between" alignItems="center">
                    <Typography variant="body2">{type.replace(/_/g, ' ')}</Typography>
                    <Box display="flex" alignItems="center" gap={1}>
                      <Typography variant="body2">{count}</Typography>
                      <Chip
                        label={`${((count / metrics.totalReports) * 100).toFixed(1)}%`}
                        color="error"
                        size="small"
                      />
                    </Box>
                  </Box>
                ))}
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* Recent Violations */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Recent Violations
              </Typography>
              <List dense>
                {metrics.recentViolations.map((violation) => (
                  <ListItem
                    key={violation.id}
                    button
                    onClick={() => onViolationClick?.(violation)}
                    sx={{
                      border: '1px solid',
                      borderColor: 'divider',
                      borderRadius: 1,
                      mb: 1,
                    }}
                  >
                    <ListItemIcon>
                      <ErrorIcon color="error" />
                    </ListItemIcon>
                    <ListItemText
                      primary={
                        <Box display="flex" alignItems="center" gap={1}>
                          <Typography variant="body2" fontWeight="bold">
                            {violation.type.replace(/_/g, ' ')}
                          </Typography>
                          <Chip
                            label={violation.severity}
                            color={getSeverityColor(violation.severity) as any}
                            size="small"
                          />
                          <Chip
                            label={violation.status}
                            color={getStatusColor(violation.status) as any}
                            size="small"
                          />
                        </Box>
                      }
                      secondary={
                        <Box>
                          <Typography variant="caption" color="textSecondary">
                            {violation.description}
                          </Typography>
                          <Typography variant="caption" display="block" color="textSecondary">
                            {violation.regulation} • {violation.userId} • {new Date(violation.timestamp).toLocaleString()}
                          </Typography>
                        </Box>
                      }
                    />
                  </ListItem>
                ))}
              </List>
            </CardContent>
          </Card>
        </Grid>

        {/* Upcoming Reviews */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Upcoming Reviews
              </Typography>
              <List dense>
                {metrics.upcomingReviews.map((review) => (
                  <ListItem
                    key={review.id}
                    button
                    onClick={() => onReviewClick?.(review)}
                    sx={{
                      border: '1px solid',
                      borderColor: 'divider',
                      borderRadius: 1,
                      mb: 1,
                    }}
                  >
                    <ListItemIcon>
                      <AssessmentIcon color="primary" />
                    </ListItemIcon>
                    <ListItemText
                      primary={
                        <Box display="flex" alignItems="center" gap={1}>
                          <Typography variant="body2" fontWeight="bold">
                            {review.title}
                          </Typography>
                          <Chip
                            label={review.priority}
                            color={getPriorityColor(review.priority) as any}
                            size="small"
                          />
                          <Chip
                            label={review.status}
                            color={getStatusColor(review.status) as any}
                            size="small"
                          />
                        </Box>
                      }
                      secondary={
                        <Box>
                          <Typography variant="caption" color="textSecondary">
                            Due: {new Date(review.dueDate).toLocaleDateString()}
                          </Typography>
                          <Typography variant="caption" display="block" color="textSecondary">
                            {review.regulation} • {review.assignedTo}
                          </Typography>
                        </Box>
                      }
                    />
                  </ListItem>
                ))}
              </List>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};

export default ComplianceMonitor;