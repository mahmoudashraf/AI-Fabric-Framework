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
  TextField,
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
  Accordion,
  AccordionSummary,
  AccordionDetails,
  IconButton,
  Tooltip,
  LinearProgress,
} from '@mui/material';
import {
  FilterList as FilterIcon,
  Security as SecurityIcon,
  CheckCircle as CheckCircleIcon,
  Warning as WarningIcon,
  Refresh as RefreshIcon,
  ExpandMore as ExpandMoreIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Add as AddIcon,
  Block as BlockIcon,
  Flag as FlagIcon,
} from '@mui/icons-material';

interface FilterSettings {
  enableContentFiltering: boolean;
  enableHateSpeechDetection: boolean;
  enableHarassmentDetection: boolean;
  enableViolenceDetection: boolean;
  enableExplicitContentDetection: boolean;
  enableSpamDetection: boolean;
  enableMisinformationDetection: boolean;
  enableAIAnalysis: boolean;
  enableRuleBasedFiltering: boolean;
  enableUserReporting: boolean;
  enableAutomaticModeration: boolean;
  enableAppealProcess: boolean;
  minContentScore: number;
  maxViolations: number;
  violationThreshold: number;
  autoBlockThreshold: number;
  reviewThreshold: number;
  appealWindow: number;
}

interface ContentPolicy {
  id: string;
  name: string;
  category: string;
  description: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  enabled: boolean;
  actions: string[];
  keywords: string[];
  patterns: string[];
  createdAt: string;
  updatedAt: string;
  violations: number;
  falsePositives: number;
  accuracy: number;
}

interface ContentViolation {
  id: string;
  content: string;
  type: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  confidence: number;
  detectedBy: string;
  timestamp: string;
  status: 'PENDING' | 'REVIEWED' | 'APPROVED' | 'REJECTED' | 'APPEALED';
  reviewer: string;
  reviewTimestamp: string;
  action: string;
  userId: string;
  contentId: string;
  context: string;
  metadata: Record<string, any>;
}

interface FilterStats {
  totalContentProcessed: number;
  violationsDetected: number;
  falsePositives: number;
  accuracy: number;
  averageProcessingTime: number;
  blockedContent: number;
  reviewedContent: number;
  appealedContent: number;
  violationTypes: Record<string, number>;
  severityDistribution: Record<string, number>;
  recentActivity: number;
}

interface ContentFilterProps {
  refreshInterval?: number;
  onSettingsChange?: (settings: FilterSettings) => void;
  onViolationClick?: (violation: ContentViolation) => void;
  onPolicyClick?: (policy: ContentPolicy) => void;
  onRefresh?: () => void;
}

const ContentFilter: React.FC<ContentFilterProps> = ({
  refreshInterval = 30000,
  onSettingsChange,
}) => {
  const [activeTab, setActiveTab] = useState(0);
  const [settings, setSettings] = useState<FilterSettings>({
    enableContentFiltering: true,
    enableHateSpeechDetection: true,
    enableHarassmentDetection: true,
    enableViolenceDetection: true,
    enableExplicitContentDetection: true,
    enableSpamDetection: true,
    enableMisinformationDetection: true,
    enableAIAnalysis: true,
    enableRuleBasedFiltering: true,
    enableUserReporting: true,
    enableAutomaticModeration: true,
    enableAppealProcess: true,
    minContentScore: 0.5,
    maxViolations: 3,
    violationThreshold: 0.7,
    autoBlockThreshold: 0.9,
    reviewThreshold: 0.6,
    appealWindow: 7,
  });
  const [policies, setPolicies] = useState<ContentPolicy[]>([]);
  const [violations, setViolations] = useState<ContentViolation[]>([]);
  const [stats, setStats] = useState<FilterStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [expandedViolations, setExpandedViolations] = useState<Set<string>>(new Set());

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
      const mockPolicies: ContentPolicy[] = [
        {
          id: 'POL_001',
          name: 'Hate Speech Detection',
          category: 'HATE_SPEECH',
          description: 'Detects and filters hate speech content',
          severity: 'HIGH',
          enabled: true,
          actions: ['BLOCK', 'FLAG', 'REVIEW'],
          keywords: ['hate', 'racist', 'discrimination'],
          patterns: ['hate.*speech', 'racist.*comment'],
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          violations: 45,
          falsePositives: 3,
          accuracy: 0.93,
        },
        {
          id: 'POL_002',
          name: 'Spam Detection',
          category: 'SPAM',
          description: 'Identifies and filters spam content',
          severity: 'MEDIUM',
          enabled: true,
          actions: ['BLOCK', 'FLAG'],
          keywords: ['spam', 'promotion', 'advertisement'],
          patterns: ['click.*here', 'free.*money'],
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          violations: 120,
          falsePositives: 8,
          accuracy: 0.87,
        },
        {
          id: 'POL_003',
          name: 'Violence Detection',
          category: 'VIOLENCE',
          description: 'Detects violent or threatening content',
          severity: 'CRITICAL',
          enabled: true,
          actions: ['BLOCK', 'REPORT', 'REVIEW'],
          keywords: ['violence', 'threat', 'harm'],
          patterns: ['kill.*someone', 'threat.*violence'],
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          violations: 12,
          falsePositives: 1,
          accuracy: 0.96,
        },
      ];

      const mockViolations: ContentViolation[] = [
        {
          id: 'VIOL_001',
          content: 'This is a hateful comment that should be blocked',
          type: 'HATE_SPEECH',
          severity: 'HIGH',
          confidence: 0.92,
          detectedBy: 'AI_ANALYSIS',
          timestamp: new Date().toISOString(),
          status: 'PENDING',
          reviewer: '',
          reviewTimestamp: '',
          action: 'BLOCK',
          userId: 'user123',
          contentId: 'content456',
          context: 'User comment on social media post',
          metadata: {
            language: 'en',
            sentiment: 'negative',
            toxicity: 0.92,
          },
        },
        {
          id: 'VIOL_002',
          content: 'Click here for free money! Limited time offer!',
          type: 'SPAM',
          severity: 'MEDIUM',
          confidence: 0.78,
          detectedBy: 'RULE_BASED',
          timestamp: new Date(Date.now() - 3600000).toISOString(),
          status: 'REVIEWED',
          reviewer: 'moderator1',
          reviewTimestamp: new Date(Date.now() - 1800000).toISOString(),
          action: 'BLOCK',
          userId: 'user789',
          contentId: 'content123',
          context: 'User post in public forum',
          metadata: {
            language: 'en',
            sentiment: 'neutral',
            spamScore: 0.78,
          },
        },
      ];

      const mockStats: FilterStats = {
        totalContentProcessed: 125000,
        violationsDetected: 450,
        falsePositives: 25,
        accuracy: 0.94,
        averageProcessingTime: 150,
        blockedContent: 380,
        reviewedContent: 70,
        appealedContent: 15,
        violationTypes: {
          'HATE_SPEECH': 45,
          'SPAM': 120,
          'VIOLENCE': 12,
          'EXPLICIT_CONTENT': 8,
          'HARASSMENT': 25,
          'MISINFORMATION': 15,
        },
        severityDistribution: {
          'CRITICAL': 12,
          'HIGH': 45,
          'MEDIUM': 120,
          'LOW': 273,
        },
        recentActivity: 25,
      };

      setPolicies(mockPolicies);
      setViolations(mockViolations);
      setStats(mockStats);
      setError(null);
    } catch (err) {
      setError('Failed to load filter data');
      console.error('Error loading filter data:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSettingsChange = (field: keyof FilterSettings, value: any) => {
    const newSettings = { ...settings, [field]: value };
    setSettings(newSettings);
    onSettingsChange?.(newSettings);
  };

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  const toggleViolationExpansion = (violationId: string) => {
    const newExpanded = new Set(expandedViolations);
    if (newExpanded.has(violationId)) {
      newExpanded.delete(violationId);
    } else {
      newExpanded.add(violationId);
    }
    setExpandedViolations(newExpanded);
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

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'PENDING':
        return 'warning';
      case 'REVIEWED':
        return 'info';
      case 'APPROVED':
        return 'success';
      case 'REJECTED':
        return 'error';
      case 'APPEALED':
        return 'secondary';
      default:
        return 'default';
    }
  };

  const getActionColor = (action: string) => {
    switch (action) {
      case 'BLOCK':
        return 'error';
      case 'FLAG':
        return 'warning';
      case 'REVIEW':
        return 'info';
      case 'APPROVE':
        return 'success';
      default:
        return 'default';
    }
  };

  const renderStats = () => {
    if (!stats) return null;

    return (
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography color="textSecondary" gutterBottom>
                    Content Processed
                  </Typography>
                  <Typography variant="h4">
                    {stats.totalContentProcessed.toLocaleString()}
                  </Typography>
                </Box>
                <FilterIcon color="primary" sx={{ fontSize: 40 }} />
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
                    Violations Detected
                  </Typography>
                  <Typography variant="h4" color="error">
                    {stats.violationsDetected}
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
                    Accuracy
                  </Typography>
                  <Typography variant="h4" color="success.main">
                    {(stats.accuracy * 100).toFixed(1)}%
                  </Typography>
                  <LinearProgress
                    variant="determinate"
                    value={stats.accuracy * 100}
                    color="success"
                    sx={{ mt: 1 }}
                  />
                </Box>
                <CheckCircleIcon color="success" sx={{ fontSize: 40 }} />
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
                    Blocked Content
                  </Typography>
                  <Typography variant="h4">
                    {stats.blockedContent}
                  </Typography>
                </Box>
                <BlockIcon color="warning" sx={{ fontSize: 40 }} />
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    );
  };

  const renderSettings = () => (
    <Grid container spacing={3}>
      <Grid size={{ xs: 12, md: 6 }}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Detection Methods
            </Typography>
            <Box display="flex" flexDirection="column" gap={2}>
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.enableContentFiltering}
                    onChange={(e) => handleSettingsChange('enableContentFiltering', e.target.checked)}
                  />
                }
                label="Enable Content Filtering"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.enableHateSpeechDetection}
                    onChange={(e) => handleSettingsChange('enableHateSpeechDetection', e.target.checked)}
                  />
                }
                label="Hate Speech Detection"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.enableHarassmentDetection}
                    onChange={(e) => handleSettingsChange('enableHarassmentDetection', e.target.checked)}
                  />
                }
                label="Harassment Detection"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.enableViolenceDetection}
                    onChange={(e) => handleSettingsChange('enableViolenceDetection', e.target.checked)}
                  />
                }
                label="Violence Detection"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.enableExplicitContentDetection}
                    onChange={(e) => handleSettingsChange('enableExplicitContentDetection', e.target.checked)}
                  />
                }
                label="Explicit Content Detection"
              />
            </Box>
          </CardContent>
        </Card>
      </Grid>

      <Grid size={{ xs: 12, md: 6 }}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Additional Features
            </Typography>
            <Box display="flex" flexDirection="column" gap={2}>
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.enableSpamDetection}
                    onChange={(e) => handleSettingsChange('enableSpamDetection', e.target.checked)}
                  />
                }
                label="Spam Detection"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.enableMisinformationDetection}
                    onChange={(e) => handleSettingsChange('enableMisinformationDetection', e.target.checked)}
                  />
                }
                label="Misinformation Detection"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.enableAIAnalysis}
                    onChange={(e) => handleSettingsChange('enableAIAnalysis', e.target.checked)}
                  />
                }
                label="AI Analysis"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.enableRuleBasedFiltering}
                    onChange={(e) => handleSettingsChange('enableRuleBasedFiltering', e.target.checked)}
                  />
                }
                label="Rule-based Filtering"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.enableUserReporting}
                    onChange={(e) => handleSettingsChange('enableUserReporting', e.target.checked)}
                  />
                }
                label="User Reporting"
              />
            </Box>
          </CardContent>
        </Card>
      </Grid>

      <Grid size={{ xs: 12, md: 6 }}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Thresholds & Limits
            </Typography>
            <Box display="flex" flexDirection="column" gap={3}>
              <Box>
                <Typography variant="body2" gutterBottom>
                  Minimum Content Score: {settings.minContentScore}
                </Typography>
                <Slider
                  value={settings.minContentScore}
                  onChange={(_, value) => handleSettingsChange('minContentScore', value)}
                  min={0}
                  max={1}
                  step={0.1}
                  marks={[
                    { value: 0, label: '0' },
                    { value: 0.5, label: '0.5' },
                    { value: 1, label: '1' },
                  ]}
                />
              </Box>
              <Box>
                <Typography variant="body2" gutterBottom>
                  Violation Threshold: {settings.violationThreshold}
                </Typography>
                <Slider
                  value={settings.violationThreshold}
                  onChange={(_, value) => handleSettingsChange('violationThreshold', value)}
                  min={0}
                  max={1}
                  step={0.1}
                  marks={[
                    { value: 0, label: '0' },
                    { value: 0.7, label: '0.7' },
                    { value: 1, label: '1' },
                  ]}
                />
              </Box>
              <Box>
                <Typography variant="body2" gutterBottom>
                  Auto-block Threshold: {settings.autoBlockThreshold}
                </Typography>
                <Slider
                  value={settings.autoBlockThreshold}
                  onChange={(_, value) => handleSettingsChange('autoBlockThreshold', value)}
                  min={0}
                  max={1}
                  step={0.1}
                  marks={[
                    { value: 0, label: '0' },
                    { value: 0.9, label: '0.9' },
                    { value: 1, label: '1' },
                  ]}
                />
              </Box>
            </Box>
          </CardContent>
        </Card>
      </Grid>

      <Grid size={{ xs: 12, md: 6 }}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Moderation & Appeals
            </Typography>
            <Box display="flex" flexDirection="column" gap={2}>
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.enableAutomaticModeration}
                    onChange={(e) => handleSettingsChange('enableAutomaticModeration', e.target.checked)}
                  />
                }
                label="Automatic Moderation"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.enableAppealProcess}
                    onChange={(e) => handleSettingsChange('enableAppealProcess', e.target.checked)}
                  />
                }
                label="Appeal Process"
              />
              <TextField
                label="Max Violations"
                type="number"
                value={settings.maxViolations}
                onChange={(e) => handleSettingsChange('maxViolations', parseInt(e.target.value))}
                fullWidth
                size="small"
                inputProps={{ min: 1, max: 10 }}
              />
              <TextField
                label="Appeal Window (days)"
                type="number"
                value={settings.appealWindow}
                onChange={(e) => handleSettingsChange('appealWindow', parseInt(e.target.value))}
                fullWidth
                size="small"
                inputProps={{ min: 1, max: 30 }}
              />
            </Box>
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );

  const renderPolicies = () => (
    <Card>
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
          <Typography variant="h6">Content Policies</Typography>
          <Button variant="outlined" startIcon={<AddIcon />}>
            Add Policy
          </Button>
        </Box>
        <List>
          {policies.map((policy) => (
            <ListItem
              key={policy.id}
              sx={{
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 1,
                mb: 1,
              }}
            >
              <ListItemIcon>
                <SecurityIcon />
              </ListItemIcon>
              <ListItemText
                primary={
                  <Box display="flex" alignItems="center" gap={1}>
                    <Typography variant="subtitle1" fontWeight="bold">
                      {policy.name}
                    </Typography>
                    <Chip
                      label={policy.severity}
                      color={getSeverityColor(policy.severity) as any}
                      size="small"
                    />
                    <Chip
                      label={policy.enabled ? 'Enabled' : 'Disabled'}
                      color={policy.enabled ? 'success' : 'default'}
                      size="small"
                    />
                  </Box>
                }
                secondary={
                  <Box>
                    <Typography variant="body2" color="textSecondary">
                      {policy.description}
                    </Typography>
                    <Typography variant="caption" color="textSecondary">
                      Violations: {policy.violations} • False Positives: {policy.falsePositives} • 
                      Accuracy: {(policy.accuracy * 100).toFixed(1)}% • 
                      Updated: {new Date(policy.updatedAt).toLocaleDateString()}
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
          Content Violations
        </Typography>
        <List>
          {violations.map((violation) => (
            <ListItem
              key={violation.id}
              component="button"
              onClick={() => toggleViolationExpansion(violation.id)}
              sx={{
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 1,
                mb: 1,
              }}
            >
              <ListItemIcon>
                <FlagIcon />
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
                    <Chip
                      label={violation.action}
                      color={getActionColor(violation.action) as any}
                      size="small"
                    />
                  </Box>
                }
                secondary={
                  <Box>
                    <Typography variant="body2" color="textSecondary">
                      {violation.content}
                    </Typography>
                    <Typography variant="caption" color="textSecondary">
                      Confidence: {(violation.confidence * 100).toFixed(1)}% • 
                      Detected by: {violation.detectedBy} • 
                      {new Date(violation.timestamp).toLocaleString()}
                    </Typography>
                  </Box>
                }
              />
            </ListItem>
          ))}
        </List>

        {/* Expanded Violation Details */}
        {Array.from(expandedViolations).map((violationId) => {
          const violation = violations.find(v => v.id === violationId);
          if (!violation) return null;

          return (
            <Accordion key={`${violationId}-details`} expanded={expandedViolations.has(violationId)}>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Typography variant="subtitle1">
                  Detailed Information - {violation.id}
                </Typography>
              </AccordionSummary>
              <AccordionDetails>
                <Grid container spacing={3}>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <Typography variant="h6" gutterBottom>
                      Content Details
                    </Typography>
                    <Typography variant="body2" paragraph>
                      {violation.content}
                    </Typography>
                    <Typography variant="body2" color="textSecondary">
                      Context: {violation.context}
                    </Typography>
                  </Grid>
                  
                  <Grid size={{ xs: 12, md: 6 }}>
                    <Typography variant="h6" gutterBottom>
                      Detection Information
                    </Typography>
                    <List dense>
                      <ListItem>
                        <ListItemText
                          primary="Confidence Score"
                          secondary={`${(violation.confidence * 100).toFixed(1)}%`}
                        />
                      </ListItem>
                      <ListItem>
                        <ListItemText
                          primary="Detected By"
                          secondary={violation.detectedBy}
                        />
                      </ListItem>
                      <ListItem>
                        <ListItemText
                          primary="User ID"
                          secondary={violation.userId}
                        />
                      </ListItem>
                      <ListItem>
                        <ListItemText
                          primary="Content ID"
                          secondary={violation.contentId}
                        />
                      </ListItem>
                    </List>
                  </Grid>
                  
                  <Grid size={{ xs: 12 }}>
                    <Typography variant="h6" gutterBottom>
                      Metadata
                    </Typography>
                    <Box display="flex" flexWrap="wrap" gap={1}>
                      {Object.entries(violation.metadata).map(([key, value]) => (
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
  );

  if (loading && !policies.length) {
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
          Content Filter
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

      {renderStats()}

      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs value={activeTab} onChange={handleTabChange}>
          <Tab label="Settings" />
          <Tab label="Policies" />
          <Tab label="Violations" />
        </Tabs>
      </Box>

      {activeTab === 0 && renderSettings()}
      {activeTab === 1 && renderPolicies()}
      {activeTab === 2 && renderViolations()}
    </Box>
  );
};

export default ContentFilter;