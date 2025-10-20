import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  IconButton,
  Tooltip,
  Alert,
  CircularProgress,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Badge,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  Divider,
} from '@mui/material';
import {
  Assignment as AssignmentIcon,
  Warning as WarningIcon,
  CheckCircle as CheckCircleIcon,
  Error as ErrorIcon,
  Refresh as RefreshIcon,
  Search as SearchIcon,
  ExpandMore as ExpandMoreIcon,
  Visibility as VisibilityIcon,
  Download as DownloadIcon,
} from '@mui/icons-material';

interface AuditLog {
  logId: string;
  userId: string;
  operationType: string;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  hasAnomalies: boolean;
  insights: string[];
  timestamp: string;
  ipAddress: string;
  userAgent: string;
  resourceType: string;
  resourceId: string;
  action: string;
  result: 'SUCCESS' | 'FAILURE' | 'PENDING';
  details: string;
  metadata: Record<string, any>;
  sessionId: string;
  durationMs: number;
  confidenceScore: number;
}

interface AuditStats {
  totalLogs: number;
  highRiskLogs: number;
  anomalyLogs: number;
  uniqueUsers: number;
  highRiskRate: number;
  anomalyRate: number;
  averageResponseTime: number;
  successRate: number;
  recentActivity: number;
  topOperations: Array<{ operation: string; count: number }>;
  riskDistribution: Record<string, number>;
}

interface AuditTrailProps {
  refreshInterval?: number;
  onLogClick?: (log: AuditLog) => void;
  onRefresh?: () => void;
  showFilters?: boolean;
  showStats?: boolean;
  maxLogs?: number;
}

const AuditTrail: React.FC<AuditTrailProps> = ({
  refreshInterval = 30000,
  onRefresh,
  showFilters = true,
  showStats = true,
  maxLogs = 100,
}) => {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [stats, setStats] = useState<AuditStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState({
    riskLevel: '',
    hasAnomalies: '',
    userId: '',
    operationType: '',
    result: '',
  });
  const [searchQuery, setSearchQuery] = useState('');
  const [expandedLogs, setExpandedLogs] = useState<Set<string>>(new Set());

  useEffect(() => {
    loadData();
    
    if (refreshInterval > 0) {
      const interval = setInterval(loadData, refreshInterval);
      return () => clearInterval(interval);
    }
    
    return undefined;
  }, [refreshInterval]);

  const loadData = async () => {
    setLoading(true);
    try {
      // This would be replaced with actual API calls
      const mockLogs: AuditLog[] = Array.from({ length: 50 }, (_, i) => ({
        logId: `AUDIT_${String(i + 1).padStart(3, '0')}`,
        userId: `user${Math.floor(Math.random() * 100) + 1}`,
        operationType: (['CREATE', 'READ', 'UPDATE', 'DELETE'][Math.floor(Math.random() * 4)]) as string,
        riskLevel: (['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'][Math.floor(Math.random() * 4)]) as any,
        hasAnomalies: Math.random() > 0.8,
        insights: [
          'Normal operation pattern detected',
          'Unusual access time detected',
          'High confidence in user identity',
          'Potential security concern identified',
        ].slice(0, Math.floor(Math.random() * 3) + 1),
        timestamp: new Date(Date.now() - Math.random() * 7 * 24 * 60 * 60 * 1000).toISOString(),
        ipAddress: `192.168.1.${Math.floor(Math.random() * 255)}`,
        userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        resourceType: (['USER', 'PRODUCT', 'ORDER', 'PAYMENT'][Math.floor(Math.random() * 4)]) as string,
        resourceId: `RES_${Math.floor(Math.random() * 1000)}`,
        action: (['LOGIN', 'VIEW', 'EDIT', 'DELETE', 'CREATE'][Math.floor(Math.random() * 5)]) as string,
        result: ['SUCCESS', 'FAILURE', 'PENDING'][Math.floor(Math.random() * 3)] as any,
        details: 'Operation completed successfully with standard security checks',
        metadata: {
          browser: 'Chrome',
          os: 'Windows 10',
          location: 'US',
        },
        sessionId: `SESS_${Math.floor(Math.random() * 10000)}`,
        durationMs: Math.floor(Math.random() * 5000) + 100,
        confidenceScore: Math.random(),
      }));

      const mockStats: AuditStats = {
        totalLogs: 12500,
        highRiskLogs: 45,
        anomalyLogs: 12,
        uniqueUsers: 89,
        highRiskRate: 0.036,
        anomalyRate: 0.0096,
        averageResponseTime: 250,
        successRate: 0.95,
        recentActivity: 150,
        topOperations: [
          { operation: 'READ', count: 8500 },
          { operation: 'CREATE', count: 2500 },
          { operation: 'UPDATE', count: 1200 },
          { operation: 'DELETE', count: 300 },
        ],
        riskDistribution: {
          'LOW': 11000,
          'MEDIUM': 1200,
          'HIGH': 250,
          'CRITICAL': 50,
        },
      };
      
      setLogs(mockLogs.slice(0, maxLogs));
      setStats(mockStats);
      setError(null);
    } catch (err) {
      setError('Failed to load audit data');
      console.error('Error loading audit data:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = async () => {
    if (!searchQuery.trim()) {
      loadData();
      return;
    }

    setLoading(true);
    try {
      // Mock search functionality
      const filteredLogs = logs.filter(log => 
        log.logId.toLowerCase().includes(searchQuery.toLowerCase()) ||
        log.userId.toLowerCase().includes(searchQuery.toLowerCase()) ||
        log.operationType.toLowerCase().includes(searchQuery.toLowerCase()) ||
        log.action.toLowerCase().includes(searchQuery.toLowerCase())
      );
      setLogs(filteredLogs);
      setError(null);
    } catch (err) {
      setError('Failed to search audit logs');
      console.error('Error searching audit logs:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleRefresh = () => {
    loadData();
    onRefresh?.();
  };

  const handleFilterChange = (field: string, value: string) => {
    setFilter(prev => ({ ...prev, [field]: value }));
  };

  const toggleLogExpansion = (logId: string) => {
    const newExpanded = new Set(expandedLogs);
    if (newExpanded.has(logId)) {
      newExpanded.delete(logId);
    } else {
      newExpanded.add(logId);
    }
    setExpandedLogs(newExpanded);
  };

  const getRiskLevelColor = (riskLevel: string) => {
    switch (riskLevel) {
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

  const getResultColor = (result: string) => {
    switch (result) {
      case 'SUCCESS':
        return 'success';
      case 'FAILURE':
        return 'error';
      case 'PENDING':
        return 'warning';
      default:
        return 'default';
    }
  };

  const filteredLogs = logs.filter(log => {
    if (filter.riskLevel && log.riskLevel !== filter.riskLevel) return false;
    if (filter.hasAnomalies && log.hasAnomalies.toString() !== filter.hasAnomalies) return false;
    if (filter.userId && !log.userId.includes(filter.userId)) return false;
    if (filter.operationType && log.operationType !== filter.operationType) return false;
    if (filter.result && log.result !== filter.result) return false;
    return true;
  });

  const renderStats = () => {
    if (!stats || !showStats) return null;

    return (
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography color="textSecondary" gutterBottom>
                    Total Logs
                  </Typography>
                  <Typography variant="h4">
                    {stats.totalLogs.toLocaleString()}
                  </Typography>
                </Box>
                <AssignmentIcon color="primary" sx={{ fontSize: 40 }} />
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
                    High Risk
                  </Typography>
                  <Typography variant="h4" color="error">
                    {stats.highRiskLogs}
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
                    Anomalies
                  </Typography>
                  <Typography variant="h4" color="warning.main">
                    {stats.anomalyLogs}
                  </Typography>
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
                    Success Rate
                  </Typography>
                  <Typography variant="h4" color="success.main">
                    {(stats.successRate * 100).toFixed(1)}%
                  </Typography>
                </Box>
                <CheckCircleIcon color="success" sx={{ fontSize: 40 }} />
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    );
  };

  const renderFilters = () => {
    if (!showFilters) return null;

    return (
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Filters & Search
          </Typography>
          <Grid container spacing={2} alignItems="center">
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                label="Search"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                fullWidth
                size="small"
                InputProps={{
                  endAdornment: (
                    <IconButton onClick={handleSearch} size="small">
                      <SearchIcon />
                    </IconButton>
                  ),
                }}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 2 }}>
              <TextField
                label="User ID"
                value={filter.userId}
                onChange={(e) => handleFilterChange('userId', e.target.value)}
                fullWidth
                size="small"
              />
            </Grid>
            <Grid size={{ xs: 12, md: 2 }}>
              <FormControl fullWidth size="small">
                <InputLabel>Risk Level</InputLabel>
                <Select
                  value={filter.riskLevel}
                  onChange={(e) => handleFilterChange('riskLevel', e.target.value)}
                >
                  <MenuItem value="">All</MenuItem>
                  <MenuItem value="CRITICAL">Critical</MenuItem>
                  <MenuItem value="HIGH">High</MenuItem>
                  <MenuItem value="MEDIUM">Medium</MenuItem>
                  <MenuItem value="LOW">Low</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid size={{ xs: 12, md: 2 }}>
              <FormControl fullWidth size="small">
                <InputLabel>Anomalies</InputLabel>
                <Select
                  value={filter.hasAnomalies}
                  onChange={(e) => handleFilterChange('hasAnomalies', e.target.value)}
                >
                  <MenuItem value="">All</MenuItem>
                  <MenuItem value="true">Yes</MenuItem>
                  <MenuItem value="false">No</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid size={{ xs: 12, md: 2 }}>
              <FormControl fullWidth size="small">
                <InputLabel>Result</InputLabel>
                <Select
                  value={filter.result}
                  onChange={(e) => handleFilterChange('result', e.target.value)}
                >
                  <MenuItem value="">All</MenuItem>
                  <MenuItem value="SUCCESS">Success</MenuItem>
                  <MenuItem value="FAILURE">Failure</MenuItem>
                  <MenuItem value="PENDING">Pending</MenuItem>
                </Select>
              </FormControl>
            </Grid>
          </Grid>
        </CardContent>
      </Card>
    );
  };

  if (loading && !logs.length) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h5" component="h2">
          Audit Trail
        </Typography>
        <Box display="flex" gap={1}>
          <Tooltip title="Refresh">
            <IconButton onClick={handleRefresh} disabled={loading}>
              <RefreshIcon />
            </IconButton>
          </Tooltip>
          <Tooltip title="Export">
            <IconButton>
              <DownloadIcon />
            </IconButton>
          </Tooltip>
        </Box>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      {renderStats()}
      {renderFilters()}

      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Audit Logs ({filteredLogs.length})
          </Typography>
          
          <TableContainer component={Paper}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Log ID</TableCell>
                  <TableCell>User</TableCell>
                  <TableCell>Operation</TableCell>
                  <TableCell>Resource</TableCell>
                  <TableCell>Action</TableCell>
                  <TableCell>Result</TableCell>
                  <TableCell>Risk</TableCell>
                  <TableCell>Anomalies</TableCell>
                  <TableCell>Duration</TableCell>
                  <TableCell>Timestamp</TableCell>
                  <TableCell>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {filteredLogs.map((log) => (
                  <TableRow key={log.logId}>
                    <TableCell>{log.logId}</TableCell>
                    <TableCell>{log.userId}</TableCell>
                    <TableCell>{log.operationType}</TableCell>
                    <TableCell>
                      <Box>
                        <Typography variant="body2" fontWeight="bold">
                          {log.resourceType}
                        </Typography>
                        <Typography variant="caption" color="textSecondary">
                          {log.resourceId}
                        </Typography>
                      </Box>
                    </TableCell>
                    <TableCell>{log.action}</TableCell>
                    <TableCell>
                      <Chip
                        label={log.result}
                        color={getResultColor(log.result) as any}
                        size="small"
                      />
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={log.riskLevel}
                        color={getRiskLevelColor(log.riskLevel) as any}
                        size="small"
                      />
                    </TableCell>
                    <TableCell>
                      <Badge
                        badgeContent={log.hasAnomalies ? 1 : 0}
                        color="error"
                      >
                        <Chip
                          label={log.hasAnomalies ? 'Yes' : 'No'}
                          color={log.hasAnomalies ? 'error' : 'default'}
                          size="small"
                        />
                      </Badge>
                    </TableCell>
                    <TableCell>{log.durationMs}ms</TableCell>
                    <TableCell>
                      {new Date(log.timestamp).toLocaleString()}
                    </TableCell>
                    <TableCell>
                      <Tooltip title="View Details">
                        <IconButton
                          size="small"
                          onClick={() => toggleLogExpansion(log.logId)}
                        >
                          <VisibilityIcon />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>

          {/* Expanded Log Details */}
          {Array.from(expandedLogs).map((logId) => {
            const log = logs.find(l => l.logId === logId);
            if (!log) return null;

            return (
              <Accordion key={`${logId}-details`} expanded={expandedLogs.has(logId)}>
                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                  <Typography variant="subtitle1">
                    Detailed Information - {log.logId}
                  </Typography>
                </AccordionSummary>
                <AccordionDetails>
                  <Grid container spacing={3}>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <Typography variant="h6" gutterBottom>
                        Operation Details
                      </Typography>
                      <List dense>
                        <ListItem>
                          <ListItemText
                            primary="Session ID"
                            secondary={log.sessionId}
                          />
                        </ListItem>
                        <ListItem>
                          <ListItemText
                            primary="IP Address"
                            secondary={log.ipAddress}
                          />
                        </ListItem>
                        <ListItem>
                          <ListItemText
                            primary="User Agent"
                            secondary={log.userAgent}
                          />
                        </ListItem>
                        <ListItem>
                          <ListItemText
                            primary="Confidence Score"
                            secondary={`${(log.confidenceScore * 100).toFixed(1)}%`}
                          />
                        </ListItem>
                      </List>
                    </Grid>
                    
                    <Grid size={{ xs: 12, md: 6 }}>
                      <Typography variant="h6" gutterBottom>
                        AI Insights
                      </Typography>
                      <List dense>
                        {log.insights.map((insight, index) => (
                          <ListItem key={index}>
                            <ListItemIcon>
                              <CheckCircleIcon color="primary" />
                            </ListItemIcon>
                            <ListItemText primary={insight} />
                          </ListItem>
                        ))}
                      </List>
                    </Grid>
                    
                    <Grid size={{ xs: 12 }}>
                      <Typography variant="h6" gutterBottom>
                        Additional Details
                      </Typography>
                      <Typography variant="body2" paragraph>
                        {log.details}
                      </Typography>
                      
                      <Divider sx={{ my: 2 }} />
                      
                      <Typography variant="h6" gutterBottom>
                        Metadata
                      </Typography>
                      <Box display="flex" flexWrap="wrap" gap={1}>
                        {Object.entries(log.metadata).map(([key, value]) => (
                          <Chip
                            key={key}
                            label={`${key}: ${value}`}
                            size="small"
                            variant="outlined"
                          />
                        ))}
                      </Box>
                    </Grid>
                  </Grid>
                </AccordionDetails>
              </Accordion>
            );
          })}
        </CardContent>
      </Card>
    </Box>
  );
};

export default AuditTrail;