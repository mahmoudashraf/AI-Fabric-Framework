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
  LinearProgress,
} from '@mui/material';
import {
  Gavel as GavelIcon,
  CheckCircle as CheckCircleIcon,
  Error as ErrorIcon,
  Warning as WarningIcon,
  Refresh as RefreshIcon,
  Download as DownloadIcon,
  Settings as SettingsIcon,
  Assessment as AssessmentIcon,
} from '@mui/icons-material';

interface ComplianceReport {
  reportId: string;
  userId: string;
  dataPrivacyCompliant: boolean;
  regulatoryCompliant: boolean;
  auditCompliant: boolean;
  retentionCompliant: boolean;
  overallCompliant: boolean;
  complianceScore: number;
  violations: string[];
  timestamp: string;
  regulationTypes: string[];
}

interface ComplianceStats {
  totalReports: number;
  compliantReports: number;
  complianceRate: number;
  totalAuditLogs: number;
}

const CompliancePage: React.FC = () => {
  const [activeTab, setActiveTab] = useState(0);
  const [reports] = useState<ComplianceReport[]>([]);
  const [stats] = useState<ComplianceStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState({
    regulationType: '',
    complianceStatus: '',
    userId: '',
  });


  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      // TODO: Implement data loading
      console.log('Loading compliance data...');
      setError(null);
    } catch (err) {
      setError('Failed to load compliance data');
      console.error('Error loading compliance data:', err);
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

  const getComplianceColor = (compliant: boolean) => {
    return compliant ? 'success' : 'error';
  };

  const getComplianceIcon = (compliant: boolean) => {
    return compliant ? <CheckCircleIcon /> : <ErrorIcon />;
  };

  const filteredReports = reports.filter(report => {
    if (filter.regulationType && !report.regulationTypes.includes(filter.regulationType)) return false;
    if (filter.complianceStatus) {
      const status = report.overallCompliant ? 'COMPLIANT' : 'NON_COMPLIANT';
      if (status !== filter.complianceStatus) return false;
    }
    if (filter.userId && !report.userId.includes(filter.userId)) return false;
    return true;
  });

  const renderOverview = () => (
    <Grid container spacing={3}>
      <Grid item xs={12} sm={6} md={3}>
        <Card>
          <CardContent>
            <Box display="flex" alignItems="center" justifyContent="space-between">
              <Box>
                <Typography color="textSecondary" gutterBottom>
                  Total Reports
                </Typography>
                <Typography variant="h4">
                  {stats?.totalReports || 0}
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
                  {stats?.compliantReports || 0}
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
                  {stats?.complianceRate ? `${(stats.complianceRate * 100).toFixed(1)}%` : '0%'}
                </Typography>
                <LinearProgress
                  variant="determinate"
                  value={stats?.complianceRate ? stats.complianceRate * 100 : 0}
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
                  {stats?.totalAuditLogs || 0}
                </Typography>
              </Box>
              <WarningIcon color="warning" sx={{ fontSize: 40 }} />
            </Box>
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );

  const renderReports = () => (
    <Card>
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
          <Typography variant="h6">Compliance Reports</Typography>
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

        <Box display="flex" gap={2} mb={2}>
          <TextField
            label="User ID"
            value={filter.userId}
            onChange={(e) => handleFilterChange('userId', e.target.value)}
            size="small"
          />
          <FormControl size="small" sx={{ minWidth: 150 }}>
            <InputLabel>Regulation Type</InputLabel>
            <Select
              value={filter.regulationType}
              onChange={(e) => handleFilterChange('regulationType', e.target.value)}
            >
              <MenuItem value="">All</MenuItem>
              <MenuItem value="GDPR">GDPR</MenuItem>
              <MenuItem value="CCPA">CCPA</MenuItem>
              <MenuItem value="SOX">SOX</MenuItem>
              <MenuItem value="HIPAA">HIPAA</MenuItem>
              <MenuItem value="PCI-DSS">PCI-DSS</MenuItem>
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: 150 }}>
            <InputLabel>Compliance Status</InputLabel>
            <Select
              value={filter.complianceStatus}
              onChange={(e) => handleFilterChange('complianceStatus', e.target.value)}
            >
              <MenuItem value="">All</MenuItem>
              <MenuItem value="COMPLIANT">Compliant</MenuItem>
              <MenuItem value="NON_COMPLIANT">Non-Compliant</MenuItem>
            </Select>
          </FormControl>
        </Box>

        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Report ID</TableCell>
                <TableCell>User ID</TableCell>
                <TableCell>Data Privacy</TableCell>
                <TableCell>Regulatory</TableCell>
                <TableCell>Audit</TableCell>
                <TableCell>Retention</TableCell>
                <TableCell>Overall</TableCell>
                <TableCell>Score</TableCell>
                <TableCell>Violations</TableCell>
                <TableCell>Regulations</TableCell>
                <TableCell>Timestamp</TableCell>
                <TableCell>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredReports.map((report) => (
                <TableRow key={report.reportId}>
                  <TableCell>{report.reportId}</TableCell>
                  <TableCell>{report.userId}</TableCell>
                  <TableCell>
                    <Chip
                      icon={getComplianceIcon(report.dataPrivacyCompliant)}
                      label={report.dataPrivacyCompliant ? 'Compliant' : 'Non-Compliant'}
                      color={getComplianceColor(report.dataPrivacyCompliant) as any}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    <Chip
                      icon={getComplianceIcon(report.regulatoryCompliant)}
                      label={report.regulatoryCompliant ? 'Compliant' : 'Non-Compliant'}
                      color={getComplianceColor(report.regulatoryCompliant) as any}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    <Chip
                      icon={getComplianceIcon(report.auditCompliant)}
                      label={report.auditCompliant ? 'Compliant' : 'Non-Compliant'}
                      color={getComplianceColor(report.auditCompliant) as any}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    <Chip
                      icon={getComplianceIcon(report.retentionCompliant)}
                      label={report.retentionCompliant ? 'Compliant' : 'Non-Compliant'}
                      color={getComplianceColor(report.retentionCompliant) as any}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    <Chip
                      icon={getComplianceIcon(report.overallCompliant)}
                      label={report.overallCompliant ? 'Compliant' : 'Non-Compliant'}
                      color={getComplianceColor(report.overallCompliant) as any}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    <Box display="flex" alignItems="center" gap={1}>
                      <Typography variant="body2">
                        {report.complianceScore.toFixed(1)}
                      </Typography>
                      <LinearProgress
                        variant="determinate"
                        value={report.complianceScore}
                        sx={{ width: 50, height: 8 }}
                      />
                    </Box>
                  </TableCell>
                  <TableCell>
                    {report.violations.map((violation) => (
                      <Chip
                        key={violation}
                        label={violation}
                        size="small"
                        color="error"
                        sx={{ mr: 0.5, mb: 0.5 }}
                      />
                    ))}
                  </TableCell>
                  <TableCell>
                    {report.regulationTypes.map((regulation) => (
                      <Chip
                        key={regulation}
                        label={regulation}
                        size="small"
                        color="primary"
                        sx={{ mr: 0.5, mb: 0.5 }}
                      />
                    ))}
                  </TableCell>
                  <TableCell>
                    {new Date(report.timestamp).toLocaleString()}
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
        AI Compliance Dashboard
      </Typography>
      
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs value={activeTab} onChange={handleTabChange}>
          <Tab label="Overview" />
          <Tab label="Reports" />
          <Tab label="Settings" />
        </Tabs>
      </Box>

      {activeTab === 0 && renderOverview()}
      {activeTab === 1 && renderReports()}
      {activeTab === 2 && (
        <Card>
          <CardContent>
            <Typography variant="h6">Compliance Settings</Typography>
            <Typography color="textSecondary">
              Compliance configuration options will be available here.
            </Typography>
          </CardContent>
        </Card>
      )}
    </Box>
  );
};

export default CompliancePage;