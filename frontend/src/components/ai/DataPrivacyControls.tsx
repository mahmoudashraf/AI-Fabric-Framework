import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Switch,
  FormControlLabel,
  Slider,
  Button,
  Alert,
  CircularProgress,
  Tabs,
  Tab,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  ListItemSecondaryAction,
  Chip,
  IconButton,
  Tooltip,
} from '@mui/material';
import {
  Error as ErrorIcon,
  Refresh as RefreshIcon,
  Add as AddIcon,
  Shield as ShieldIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Lock as PrivacyIcon,
} from '@mui/icons-material';

interface PrivacySettings {
  dataMinimization: boolean;
  purposeLimitation: boolean;
  storageLimitation: boolean;
  accuracy: boolean;
  integrityAndConfidentiality: boolean;
  accountability: boolean;
  transparency: boolean;
  fairness: boolean;
  lawfulness: boolean;
  consentRequired: boolean;
  dataRetentionPeriod: number;
  anonymizationLevel: number;
  encryptionEnabled: boolean;
  accessLogging: boolean;
  auditTrail: boolean;
  rightToErasure: boolean;
  rightToRectification: boolean;
  rightToRestriction: boolean;
  rightToObject: boolean;
  dataPortability: boolean;
  automatedDecisionMaking: boolean;
  profiling: boolean;
  crossBorderTransfer: boolean;
  dataBreachNotification: boolean;
}

interface DataClassification {
  id: string;
  name: string;
  level: 'PUBLIC' | 'INTERNAL' | 'CONFIDENTIAL' | 'RESTRICTED';
  description: string;
  retentionPeriod: number;
  encryptionRequired: boolean;
  accessControls: string[];
  createdAt: string;
  updatedAt: string;
}

interface PrivacyViolation {
  id: string;
  type: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  description: string;
  timestamp: string;
  status: 'ACTIVE' | 'RESOLVED' | 'IGNORED';
  userId: string;
  dataSubject: string;
  regulation: string;
  impact: string;
  remediation: string;
}

interface DataSubject {
  id: string;
  name: string;
  email: string;
  dataCategories: string[];
  consentGiven: boolean;
  consentTimestamp: string;
  lastUpdated: string;
  rights: string[];
  preferences: Record<string, any>;
}

interface DataPrivacyControlsProps {
  refreshInterval?: number;
  onSettingsChange?: (settings: PrivacySettings) => void;
  onViolationClick?: (violation: PrivacyViolation) => void;
  onDataSubjectClick?: (subject: DataSubject) => void;
  onRefresh?: () => void;
}

const DataPrivacyControls: React.FC<DataPrivacyControlsProps> = ({
  refreshInterval = 30000,
  onSettingsChange,
  onViolationClick,
  onDataSubjectClick,
}) => {
  const [activeTab, setActiveTab] = useState(0);
  const [settings, setSettings] = useState<PrivacySettings>({
    dataMinimization: true,
    purposeLimitation: true,
    storageLimitation: true,
    accuracy: true,
    integrityAndConfidentiality: true,
    accountability: true,
    transparency: true,
    fairness: true,
    lawfulness: true,
    consentRequired: true,
    dataRetentionPeriod: 365,
    anonymizationLevel: 50,
    encryptionEnabled: true,
    accessLogging: true,
    auditTrail: true,
    rightToErasure: true,
    rightToRectification: true,
    rightToRestriction: true,
    rightToObject: true,
    dataPortability: true,
    automatedDecisionMaking: false,
    profiling: false,
    crossBorderTransfer: false,
    dataBreachNotification: true,
  });
  const [classifications, setClassifications] = useState<DataClassification[]>([]);
  const [violations, setViolations] = useState<PrivacyViolation[]>([]);
  const [dataSubjects, setDataSubjects] = useState<DataSubject[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadData();
    
    if (refreshInterval > 0) {
      const interval = setInterval(loadData, refreshInterval);
      return () => clearInterval(interval);
    }
    
    return () => {};
  }, [refreshInterval]);

  const loadData = async () => {
    setLoading(true);
    try {
      // Mock data loading
      const mockClassifications: DataClassification[] = [
        {
          id: 'CLASS_001',
          name: 'Personal Data',
          level: 'CONFIDENTIAL',
          description: 'Personal information that can identify an individual',
          retentionPeriod: 365,
          encryptionRequired: true,
          accessControls: ['AUTHENTICATION', 'AUTHORIZATION', 'AUDIT_LOGGING'],
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
        {
          id: 'CLASS_002',
          name: 'Financial Data',
          level: 'RESTRICTED',
          description: 'Financial information including payment details',
          retentionPeriod: 2555,
          encryptionRequired: true,
          accessControls: ['AUTHENTICATION', 'AUTHORIZATION', 'AUDIT_LOGGING', 'ENCRYPTION'],
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
        {
          id: 'CLASS_003',
          name: 'Public Information',
          level: 'PUBLIC',
          description: 'Information that is publicly available',
          retentionPeriod: 2555,
          encryptionRequired: false,
          accessControls: ['AUTHENTICATION'],
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      ];

      const mockViolations: PrivacyViolation[] = [
        {
          id: 'VIOL_001',
          type: 'DATA_PRIVACY_VIOLATION',
          severity: 'HIGH',
          description: 'Personal data processed without explicit consent',
          timestamp: new Date().toISOString(),
          status: 'ACTIVE',
          userId: 'user123',
          dataSubject: 'John Doe',
          regulation: 'GDPR',
          impact: 'High risk to data subject privacy',
          remediation: 'Obtain explicit consent before processing',
        },
        {
          id: 'VIOL_002',
          type: 'DATA_RETENTION_VIOLATION',
          severity: 'MEDIUM',
          description: 'Data retained beyond specified retention period',
          timestamp: new Date(Date.now() - 3600000).toISOString(),
          status: 'RESOLVED',
          userId: 'user456',
          dataSubject: 'Jane Smith',
          regulation: 'CCPA',
          impact: 'Medium risk to data subject privacy',
          remediation: 'Data deleted according to retention policy',
        },
      ];

      const mockDataSubjects: DataSubject[] = [
        {
          id: 'SUBJ_001',
          name: 'John Doe',
          email: 'john.doe@example.com',
          dataCategories: ['PERSONAL', 'CONTACT', 'PREFERENCES'],
          consentGiven: true,
          consentTimestamp: new Date().toISOString(),
          lastUpdated: new Date().toISOString(),
          rights: ['ACCESS', 'RECTIFICATION', 'ERASURE', 'PORTABILITY'],
          preferences: {
            marketing: false,
            analytics: true,
            personalization: true,
          },
        },
        {
          id: 'SUBJ_002',
          name: 'Jane Smith',
          email: 'jane.smith@example.com',
          dataCategories: ['PERSONAL', 'FINANCIAL', 'TRANSACTION'],
          consentGiven: true,
          consentTimestamp: new Date(Date.now() - 86400000).toISOString(),
          lastUpdated: new Date().toISOString(),
          rights: ['ACCESS', 'RECTIFICATION', 'ERASURE', 'RESTRICTION'],
          preferences: {
            marketing: true,
            analytics: false,
            personalization: false,
          },
        },
      ];

      setClassifications(mockClassifications);
      setViolations(mockViolations);
      setDataSubjects(mockDataSubjects);
      setError(null);
    } catch (err) {
      setError('Failed to load privacy data');
      console.error('Error loading privacy data:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSettingsChange = (field: keyof PrivacySettings, value: any) => {
    const newSettings = { ...settings, [field]: value };
    setSettings(newSettings);
    onSettingsChange?.(newSettings);
  };

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
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

  const getClassificationColor = (level: string) => {
    switch (level) {
      case 'RESTRICTED':
        return 'error';
      case 'CONFIDENTIAL':
        return 'warning';
      case 'INTERNAL':
        return 'info';
      case 'PUBLIC':
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
      default:
        return 'default';
    }
  };

  const renderSettings = () => (
    <Grid container spacing={3}>
      <Grid size={{ xs: 12, md: 6 }}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Data Protection Principles
            </Typography>
            <Box display="flex" flexDirection="column" gap={2}>
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.dataMinimization}
                    onChange={(e) => handleSettingsChange('dataMinimization', e.target.checked)}
                  />
                }
                label="Data Minimization"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.purposeLimitation}
                    onChange={(e) => handleSettingsChange('purposeLimitation', e.target.checked)}
                  />
                }
                label="Purpose Limitation"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.storageLimitation}
                    onChange={(e) => handleSettingsChange('storageLimitation', e.target.checked)}
                  />
                }
                label="Storage Limitation"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.accuracy}
                    onChange={(e) => handleSettingsChange('accuracy', e.target.checked)}
                  />
                }
                label="Accuracy"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.integrityAndConfidentiality}
                    onChange={(e) => handleSettingsChange('integrityAndConfidentiality', e.target.checked)}
                  />
                }
                label="Integrity and Confidentiality"
              />
            </Box>
          </CardContent>
        </Card>
      </Grid>

      <Grid size={{ xs: 12, md: 6 }}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Data Subject Rights
            </Typography>
            <Box display="flex" flexDirection="column" gap={2}>
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.rightToErasure}
                    onChange={(e) => handleSettingsChange('rightToErasure', e.target.checked)}
                  />
                }
                label="Right to Erasure"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.rightToRectification}
                    onChange={(e) => handleSettingsChange('rightToRectification', e.target.checked)}
                  />
                }
                label="Right to Rectification"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.rightToRestriction}
                    onChange={(e) => handleSettingsChange('rightToRestriction', e.target.checked)}
                  />
                }
                label="Right to Restriction"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.rightToObject}
                    onChange={(e) => handleSettingsChange('rightToObject', e.target.checked)}
                  />
                }
                label="Right to Object"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.dataPortability}
                    onChange={(e) => handleSettingsChange('dataPortability', e.target.checked)}
                  />
                }
                label="Data Portability"
              />
            </Box>
          </CardContent>
        </Card>
      </Grid>

      <Grid size={{ xs: 12, md: 6 }}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Security Controls
            </Typography>
            <Box display="flex" flexDirection="column" gap={2}>
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.encryptionEnabled}
                    onChange={(e) => handleSettingsChange('encryptionEnabled', e.target.checked)}
                  />
                }
                label="Encryption Enabled"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.accessLogging}
                    onChange={(e) => handleSettingsChange('accessLogging', e.target.checked)}
                  />
                }
                label="Access Logging"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.auditTrail}
                    onChange={(e) => handleSettingsChange('auditTrail', e.target.checked)}
                  />
                }
                label="Audit Trail"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.dataBreachNotification}
                    onChange={(e) => handleSettingsChange('dataBreachNotification', e.target.checked)}
                  />
                }
                label="Data Breach Notification"
              />
            </Box>
          </CardContent>
        </Card>
      </Grid>

      <Grid size={{ xs: 12, md: 6 }}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Data Retention & Anonymization
            </Typography>
            <Box display="flex" flexDirection="column" gap={3}>
              <Box>
                <Typography variant="body2" gutterBottom>
                  Data Retention Period (days): {settings.dataRetentionPeriod}
                </Typography>
                <Slider
                  value={settings.dataRetentionPeriod}
                  onChange={(_, value) => handleSettingsChange('dataRetentionPeriod', value)}
                  min={30}
                  max={2555}
                  step={30}
                  marks={[
                    { value: 30, label: '30d' },
                    { value: 365, label: '1y' },
                    { value: 2555, label: '7y' },
                  ]}
                />
              </Box>
              <Box>
                <Typography variant="body2" gutterBottom>
                  Anonymization Level: {settings.anonymizationLevel}%
                </Typography>
                <Slider
                  value={settings.anonymizationLevel}
                  onChange={(_, value) => handleSettingsChange('anonymizationLevel', value)}
                  min={0}
                  max={100}
                  step={10}
                  marks={[
                    { value: 0, label: '0%' },
                    { value: 50, label: '50%' },
                    { value: 100, label: '100%' },
                  ]}
                />
              </Box>
            </Box>
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );

  const renderClassifications = () => (
    <Card>
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
          <Typography variant="h6">Data Classifications</Typography>
          <Button variant="outlined" startIcon={<AddIcon />}>
            Add Classification
          </Button>
        </Box>
        <List>
          {classifications.map((classification) => (
            <ListItem
              key={classification.id}
              sx={{
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 1,
                mb: 1,
              }}
            >
              <ListItemIcon>
                <ShieldIcon />
              </ListItemIcon>
              <ListItemText
                primary={
                  <Box display="flex" alignItems="center" gap={1}>
                    <Typography variant="subtitle1" fontWeight="bold">
                      {classification.name}
                    </Typography>
                    <Chip
                      label={classification.level}
                      color={getClassificationColor(classification.level) as any}
                      size="small"
                    />
                  </Box>
                }
                secondary={
                  <Box>
                    <Typography variant="body2" color="textSecondary">
                      {classification.description}
                    </Typography>
                    <Typography variant="caption" color="textSecondary">
                      Retention: {classification.retentionPeriod} days • 
                      Encryption: {classification.encryptionRequired ? 'Required' : 'Not Required'} • 
                      Updated: {new Date(classification.updatedAt).toLocaleDateString()}
                    </Typography>
                  </Box>
                }
              />
              <ListItemSecondaryAction>
                <Tooltip title="Edit">
                  <IconButton size="small">
                    <EditIcon />
                  </IconButton>
                </Tooltip>
                <Tooltip title="Delete">
                  <IconButton size="small">
                    <DeleteIcon />
                  </IconButton>
                </Tooltip>
              </ListItemSecondaryAction>
            </ListItem>
          ))}
        </List>
      </CardContent>
    </Card>
  );

  const renderViolations = () => (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Privacy Violations
        </Typography>
        <List>
          {violations.map((violation) => (
            <ListItem
              key={violation.id}
              component="button"
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
                    <Typography variant="subtitle1" fontWeight="bold">
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
                    <Typography variant="body2" color="textSecondary">
                      {violation.description}
                    </Typography>
                    <Typography variant="caption" color="textSecondary">
                      {violation.regulation} • {violation.dataSubject} • {new Date(violation.timestamp).toLocaleString()}
                    </Typography>
                  </Box>
                }
              />
            </ListItem>
          ))}
        </List>
      </CardContent>
    </Card>
  );

  const renderDataSubjects = () => (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Data Subjects
        </Typography>
        <List>
          {dataSubjects.map((subject) => (
            <ListItem
              key={subject.id}
              component="button"
              onClick={() => onDataSubjectClick?.(subject)}
              sx={{
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 1,
                mb: 1,
              }}
            >
              <ListItemIcon>
                <PrivacyIcon />
              </ListItemIcon>
              <ListItemText
                primary={
                  <Box display="flex" alignItems="center" gap={1}>
                    <Typography variant="subtitle1" fontWeight="bold">
                      {subject.name}
                    </Typography>
                    <Chip
                      label={subject.consentGiven ? 'Consent Given' : 'No Consent'}
                      color={subject.consentGiven ? 'success' : 'error'}
                      size="small"
                    />
                  </Box>
                }
                secondary={
                  <Box>
                    <Typography variant="body2" color="textSecondary">
                      {subject.email}
                    </Typography>
                    <Typography variant="caption" color="textSecondary">
                      Categories: {subject.dataCategories.join(', ')} • 
                      Rights: {subject.rights.join(', ')} • 
                      Last Updated: {new Date(subject.lastUpdated).toLocaleDateString()}
                    </Typography>
                  </Box>
                }
              />
            </ListItem>
          ))}
        </List>
      </CardContent>
    </Card>
  );

  if (loading && !classifications.length) {
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
          Data Privacy Controls
        </Typography>
        <Tooltip title="Refresh">
          <IconButton onClick={loadData} disabled={loading}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs value={activeTab} onChange={handleTabChange}>
          <Tab label="Settings" />
          <Tab label="Classifications" />
          <Tab label="Violations" />
          <Tab label="Data Subjects" />
        </Tabs>
      </Box>

      {activeTab === 0 && renderSettings()}
      {activeTab === 1 && renderClassifications()}
      {activeTab === 2 && renderViolations()}
      {activeTab === 3 && renderDataSubjects()}
    </Box>
  );
};

export default DataPrivacyControls;