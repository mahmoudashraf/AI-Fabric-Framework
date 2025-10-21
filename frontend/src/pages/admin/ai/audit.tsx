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
  Button,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Alert,
  CircularProgress,
  Tabs,
  Tab,
  IconButton,
  Tooltip,
  Badge,
} from '@mui/material';
import {
  Assignment as AssignmentIcon,
  Warning as WarningIcon,
  CheckCircle as CheckCircleIcon,
  Error as ErrorIcon,
  Refresh as RefreshIcon,
  Download as DownloadIcon,
  Settings as SettingsIcon,
  Search as SearchIcon,
} from '@mui/icons-material';

interface AuditLog {
  logId: string;
  userId: string;
  operationType: string;
  riskLevel: string;
  hasAnomalies: boolean;
  insights: string[];
  timestamp: string;
  ipAddress: string;
  userAgent: string;
  resourceType: string;
  resourceId: string;
  action: string;
  result: string;
}

interface AuditStats {
  totalLogs: number;
  highRiskLogs: number;
  anomalyLogs: number;
  uniqueUsers: number;
  highRiskRate: number;
  anomalyRate: number;
}

const AuditPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState(0);
  const [logs] = useState<AuditLog[]>([]);
  const [stats] = useState<AuditStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState({
    riskLevel: '',
    hasAnomalies: '',
    userId: '',
    operationType: '',
  });
  const [searchQuery, setSearchQuery] = useState('');


  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      // TODO: Implement data loading
      console.log('Loading audit data...');
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
      // TODO: Implement search functionality
      console.log('Searching audit logs...', searchQuery, filter);
      setError(null);
    } catch (err) {
      setError('Failed to search audit logs');
      console.error('Error searching audit logs:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  const handleFilterChange = (field: string, value: string) => {
    setFilter(prev => ({ ...prev, [field]: value }));
  };

  const getRiskLevelColor = (riskLevel: string) => {
    switch (riskLevel) {
      case 'HIGH':
        return 'error';
      case 'MEDIUM':
        return 'warning';
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
    return true;
  });

  const renderOverview = () => (
    <Grid container spacing={3}>
      <Grid size={{ xs: 12, sm: 6, md: 3 }}>
        <Card>
          <CardContent>
            <Box display="flex" alignItems="center" justifyContent="space-between">
              <Box>
                <Typography color="textSecondary" gutterBottom>
                  Total Logs
                </Typography>
                <Typography variant="h4">
                  {stats?.totalLogs || 0}
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
                  High Risk Logs
                </Typography>
                <Typography variant="h4" color="error">
                  {stats?.highRiskLogs || 0}
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
                  Anomaly Logs
                </Typography>
                <Typography variant="h4" color="warning.main">
                  {stats?.anomalyLogs || 0}
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
                  Unique Users
                </Typography>
                <Typography variant="h4">
                  {stats?.uniqueUsers || 0}
                </Typography>
              </Box>
              <CheckCircleIcon color="success" sx={{ fontSize: 40 }} />
            </Box>
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );

  const renderLogs = () => (
    <Card>
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
          <Typography variant="h6">Audit Logs</Typography>
          <Box display="flex" gap={1}>
            <Button
              variant="outlined"
              startIcon={<RefreshIcon />}
              onClick={loadData}
              disabled={loading}
            >
              Refresh
            </Button>
            <Button
              variant="outlined"
              startIcon={<DownloadIcon />}
            >
              Export
            </Button>
          </Box>
        </Box>

        <Box display="flex" gap={2} mb={2} alignItems="center">
          <TextField
            label="Search"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            size="small"
            sx={{ flexGrow: 1 }}
            InputProps={{
              endAdornment: (
                <IconButton onClick={handleSearch} size="small">
                  <SearchIcon />
                </IconButton>
              ),
            }}
          />
          <TextField
            label="User ID"
            value={filter.userId}
            onChange={(e) => handleFilterChange('userId', e.target.value)}
            size="small"
          />
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <InputLabel>Risk Level</InputLabel>
            <Select
              value={filter.riskLevel}
              onChange={(e) => handleFilterChange('riskLevel', e.target.value)}
            >
              <MenuItem value="">All</MenuItem>
              <MenuItem value="HIGH">High</MenuItem>
              <MenuItem value="MEDIUM">Medium</MenuItem>
              <MenuItem value="LOW">Low</MenuItem>
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: 120 }}>
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
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <InputLabel>Operation</InputLabel>
            <Select
              value={filter.operationType}
              onChange={(e) => handleFilterChange('operationType', e.target.value)}
            >
              <MenuItem value="">All</MenuItem>
              <MenuItem value="CREATE">Create</MenuItem>
              <MenuItem value="READ">Read</MenuItem>
              <MenuItem value="UPDATE">Update</MenuItem>
              <MenuItem value="DELETE">Delete</MenuItem>
            </Select>
          </FormControl>
        </Box>

        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Log ID</TableCell>
                <TableCell>User ID</TableCell>
                <TableCell>Operation</TableCell>
                <TableCell>Resource</TableCell>
                <TableCell>Action</TableCell>
                <TableCell>Result</TableCell>
                <TableCell>Risk Level</TableCell>
                <TableCell>Anomalies</TableCell>
                <TableCell>Insights</TableCell>
                <TableCell>IP Address</TableCell>
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
                  <TableCell>
                    <Box>
                      {log.insights.slice(0, 2).map((insight, index) => (
                        <Typography
                          key={index}
                          variant="caption"
                          display="block"
                          sx={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis' }}
                        >
                          {insight}
                        </Typography>
                      ))}
                      {log.insights.length > 2 && (
                        <Typography variant="caption" color="textSecondary">
                          +{log.insights.length - 2} more
                        </Typography>
                      )}
                    </Box>
                  </TableCell>
                  <TableCell>{log.ipAddress}</TableCell>
                  <TableCell>
                    {new Date(log.timestamp).toLocaleString()}
                  </TableCell>
                  <TableCell>
                    <Tooltip title="View Details">
                      <IconButton size="small">
                        <SettingsIcon />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </CardContent>
    </Card>
  );

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        AI Audit Dashboard
      </Typography>
      
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs value={activeTab} onChange={handleTabChange}>
          <Tab label="Overview" />
          <Tab label="Logs" />
          <Tab label="Settings" />
        </Tabs>
      </Box>

      {activeTab === 0 && renderOverview()}
      {activeTab === 1 && renderLogs()}
      {activeTab === 2 && (
        <Card>
          <CardContent>
            <Typography variant="h6">Audit Settings</Typography>
            <Typography color="textSecondary">
              Audit configuration options will be available here.
            </Typography>
          </CardContent>
        </Card>
      )}
    </Box>
  );
};

export default AuditPage;