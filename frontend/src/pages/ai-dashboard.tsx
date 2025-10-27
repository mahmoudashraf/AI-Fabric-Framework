import React, { useState, useEffect } from 'react';
import {
  Box,
  Grid,
  Card,
  CardContent,
  Typography,
  Button,
  Tabs,
  Tab,
  Paper,
  Chip,
  LinearProgress,
  Avatar,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  Divider,
  Alert,
  IconButton,
  Tooltip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
} from '@mui/material';
import {
  Dashboard as DashboardIcon,
  Search as SearchIcon,
  Recommend as RecommendIcon,
  Chat as ChatIcon,
  Analytics as AnalyticsIcon,
  Settings as SettingsIcon,
  TrendingUp as TrendingUpIcon,
  People as PeopleIcon,
  ShoppingCart as ShoppingCartIcon,
  Visibility as VisibilityIcon,
  SmartToy as SmartToyIcon,
  Speed as SpeedIcon,
  Security as SecurityIcon,
  Psychology as PsychologyIcon,
  AutoAwesome as AutoAwesomeIcon,
  Lightbulb as LightbulbIcon,
  Timeline as TimelineIcon,
  Assessment as AssessmentIcon,
} from '@mui/icons-material';

interface AIMetric {
  title: string;
  value: string | number;
  change: number;
  icon: React.ReactNode;
  color: 'primary' | 'secondary' | 'success' | 'warning' | 'error';
}

interface AIInsight {
  id: string;
  title: string;
  description: string;
  type: 'opportunity' | 'warning' | 'success' | 'info';
  priority: 'high' | 'medium' | 'low';
  actionRequired: boolean;
}

const AIDashboard: React.FC = () => {
  const [activeTab, setActiveTab] = useState(0);
  const [timeRange, setTimeRange] = useState('7d');
  const [selectedMetric, setSelectedMetric] = useState<string | null>(null);
  const [showInsightDetails, setShowInsightDetails] = useState(false);
  const [selectedInsight, setSelectedInsight] = useState<AIInsight | null>(null);

  // Mock data - in real implementation, this would come from AI analytics API
  const aiMetrics: AIMetric[] = [
    {
      title: 'AI-Powered Searches',
      value: '12,847',
      change: 23.5,
      icon: <SearchIcon />,
      color: 'primary',
    },
    {
      title: 'AI Recommendations Served',
      value: '45,392',
      change: 18.2,
      icon: <RecommendIcon />,
      color: 'secondary',
    },
    {
      title: 'Conversational AI Sessions',
      value: '3,254',
      change: 45.7,
      icon: <ChatIcon />,
      color: 'success',
    },
    {
      title: 'AI-Driven Conversions',
      value: '1,847',
      change: 32.1,
      icon: <ShoppingCartIcon />,
      color: 'warning',
    },
  ];

  const performanceMetrics = [
    { label: 'Search Accuracy', value: 94.2, target: 90 },
    { label: 'Recommendation CTR', value: 28.7, target: 25 },
    { label: 'AI Response Time', value: 187, target: 200, unit: 'ms', inverse: true },
    { label: 'User Satisfaction', value: 4.6, target: 4.0, max: 5 },
  ];

  const aiInsights: AIInsight[] = [
    {
      id: '1',
      title: 'Recommendation Engine Optimization Opportunity',
      description: 'AI analysis shows 15% improvement potential in luxury handbag recommendations by incorporating seasonal trends.',
      type: 'opportunity',
      priority: 'high',
      actionRequired: true,
    },
    {
      id: '2',
      title: 'Search Query Pattern Detected',
      description: 'Users are increasingly searching for "sustainable luxury" items. Consider enhancing sustainability filters.',
      type: 'info',
      priority: 'medium',
      actionRequired: false,
    },
    {
      id: '3',
      title: 'Exceptional AI Performance',
      description: 'Conversational AI achieved 96% user satisfaction this week, exceeding targets by 20%.',
      type: 'success',
      priority: 'low',
      actionRequired: false,
    },
    {
      id: '4',
      title: 'Model Drift Warning',
      description: 'Product categorization model showing slight accuracy decline. Recommend retraining with recent data.',
      type: 'warning',
      priority: 'high',
      actionRequired: true,
    },
  ];

  const aiFeatures = [
    {
      name: 'Semantic Search',
      status: 'active',
      usage: 89,
      performance: 94.2,
      description: 'Natural language product search with vector embeddings',
    },
    {
      name: 'Personalized Recommendations',
      status: 'active',
      usage: 76,
      performance: 87.5,
      description: 'Multi-strategy recommendation engine with behavioral AI',
    },
    {
      name: 'Conversational AI',
      status: 'active',
      usage: 45,
      performance: 92.1,
      description: 'Chat interface with context awareness and voice support',
    },
    {
      name: 'Visual Search',
      status: 'beta',
      usage: 23,
      performance: 78.3,
      description: 'Image-based product discovery using computer vision',
    },
    {
      name: 'Behavioral UI Adaptation',
      status: 'active',
      usage: 67,
      performance: 85.7,
      description: 'Dynamic interface adaptation based on user behavior',
    },
    {
      name: 'Predictive Analytics',
      status: 'development',
      usage: 0,
      performance: 0,
      description: 'Forecasting and trend analysis for business intelligence',
    },
  ];

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  const handleInsightClick = (insight: AIInsight) => {
    setSelectedInsight(insight);
    setShowInsightDetails(true);
  };

  const renderMetricCard = (metric: AIMetric) => (
    <Card 
      key={metric.title}
      sx={{ 
        cursor: 'pointer',
        transition: 'all 0.3s ease',
        '&:hover': { 
          transform: 'translateY(-4px)',
          boxShadow: 4,
        }
      }}
      onClick={() => setSelectedMetric(metric.title)}
    >
      <CardContent>
        <Box display="flex" alignItems="center" justifyContent="space-between">
          <Box>
            <Typography variant="h4" color={`${metric.color}.main`} fontWeight="bold">
              {metric.value}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {metric.title}
            </Typography>
            <Box display="flex" alignItems="center" mt={1}>
              <TrendingUpIcon 
                sx={{ 
                  fontSize: 16, 
                  mr: 0.5, 
                  color: metric.change > 0 ? 'success.main' : 'error.main' 
                }} 
              />
              <Typography 
                variant="caption" 
                color={metric.change > 0 ? 'success.main' : 'error.main'}
              >
                {metric.change > 0 ? '+' : ''}{metric.change}% vs last period
              </Typography>
            </Box>
          </Box>
          <Avatar sx={{ bgcolor: `${metric.color}.main`, width: 56, height: 56 }}>
            {metric.icon}
          </Avatar>
        </Box>
      </CardContent>
    </Card>
  );

  const renderPerformanceMetrics = () => (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          AI Performance Metrics
        </Typography>
        <Box mt={3}>
          {performanceMetrics.map((metric) => {
            const percentage = metric.max 
              ? (metric.value / metric.max) * 100
              : metric.inverse
                ? Math.max(0, 100 - ((metric.value - metric.target) / metric.target) * 100)
                : (metric.value / metric.target) * 100;
            
            const isGood = metric.inverse ? metric.value <= metric.target : metric.value >= metric.target;
            
            return (
              <Box key={metric.label} mb={3}>
                <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                  <Typography variant="body2" fontWeight="medium">
                    {metric.label}
                  </Typography>
                  <Typography variant="body2" color={isGood ? 'success.main' : 'warning.main'}>
                    {metric.value}{metric.unit || (metric.max ? `/${metric.max}` : '%')}
                  </Typography>
                </Box>
                <LinearProgress
                  variant="determinate"
                  value={Math.min(100, percentage)}
                  color={isGood ? 'success' : 'warning'}
                  sx={{ height: 8, borderRadius: 4 }}
                />
                <Typography variant="caption" color="text.secondary">
                  Target: {metric.target}{metric.unit || (metric.max ? `/${metric.max}` : '%')}
                </Typography>
              </Box>
            );
          })}
        </Box>
      </CardContent>
    </Card>
  );

  const renderAIInsights = () => (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          AI Insights & Recommendations
        </Typography>
        <List>
          {aiInsights.map((insight, index) => (
            <React.Fragment key={insight.id}>
              <ListItem
                button
                onClick={() => handleInsightClick(insight)}
                sx={{
                  borderRadius: 1,
                  mb: 1,
                  border: '1px solid',
                  borderColor: 'divider',
                }}
              >
                <ListItemIcon>
                  {insight.type === 'opportunity' && <LightbulbIcon color="warning" />}
                  {insight.type === 'warning' && <SecurityIcon color="error" />}
                  {insight.type === 'success' && <AutoAwesomeIcon color="success" />}
                  {insight.type === 'info' && <PsychologyIcon color="info" />}
                </ListItemIcon>
                <ListItemText
                  primary={
                    <Box display="flex" alignItems="center" gap={1}>
                      <Typography variant="subtitle2">
                        {insight.title}
                      </Typography>
                      <Chip
                        label={insight.priority}
                        size="small"
                        color={
                          insight.priority === 'high' ? 'error' :
                          insight.priority === 'medium' ? 'warning' : 'default'
                        }
                      />
                      {insight.actionRequired && (
                        <Chip label="Action Required" size="small" color="secondary" />
                      )}
                    </Box>
                  }
                  secondary={insight.description}
                />
              </ListItem>
              {index < aiInsights.length - 1 && <Divider />}
            </React.Fragment>
          ))}
        </List>
      </CardContent>
    </Card>
  );

  const renderAIFeatures = () => (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          AI Features Status
        </Typography>
        <Grid container spacing={2}>
          {aiFeatures.map((feature) => (
            <Grid item xs={12} md={6} key={feature.name}>
              <Paper sx={{ p: 2, height: '100%' }}>
                <Box display="flex" justifyContent="space-between" alignItems="flex-start" mb={2}>
                  <Typography variant="subtitle1" fontWeight="medium">
                    {feature.name}
                  </Typography>
                  <Chip
                    label={feature.status}
                    size="small"
                    color={
                      feature.status === 'active' ? 'success' :
                      feature.status === 'beta' ? 'warning' : 'default'
                    }
                  />
                </Box>
                
                <Typography variant="body2" color="text.secondary" mb={2}>
                  {feature.description}
                </Typography>
                
                <Box mb={2}>
                  <Typography variant="caption" color="text.secondary">
                    Usage Rate: {feature.usage}%
                  </Typography>
                  <LinearProgress
                    variant="determinate"
                    value={feature.usage}
                    sx={{ height: 6, borderRadius: 3, mt: 0.5 }}
                  />
                </Box>
                
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    Performance Score: {feature.performance}%
                  </Typography>
                  <LinearProgress
                    variant="determinate"
                    value={feature.performance}
                    color={feature.performance > 85 ? 'success' : feature.performance > 70 ? 'warning' : 'error'}
                    sx={{ height: 6, borderRadius: 3, mt: 0.5 }}
                  />
                </Box>
              </Paper>
            </Grid>
          ))}
        </Grid>
      </CardContent>
    </Card>
  );

  const renderOverview = () => (
    <Box>
      {/* Key Metrics */}
      <Grid container spacing={3} mb={4}>
        {aiMetrics.map(renderMetricCard)}
      </Grid>

      {/* Performance and Insights Row */}
      <Grid container spacing={3} mb={4}>
        <Grid item xs={12} md={6}>
          {renderPerformanceMetrics()}
        </Grid>
        <Grid item xs={12} md={6}>
          {renderAIInsights()}
        </Grid>
      </Grid>

      {/* AI Features Status */}
      {renderAIFeatures()}
    </Box>
  );

  const renderAnalytics = () => (
    <Box>
      <Alert severity="info" sx={{ mb: 3 }}>
        <Typography variant="body2">
          Detailed AI analytics including search patterns, recommendation performance, 
          and user behavior insights. Real-time data processing with ML-powered insights.
        </Typography>
      </Alert>
      
      <Grid container spacing={3}>
        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Search Analytics
              </Typography>
              <Box display="flex" alignItems="center" mb={2}>
                <SearchIcon sx={{ mr: 1, color: 'primary.main' }} />
                <Typography variant="h4" color="primary.main">
                  94.2%
                </Typography>
              </Box>
              <Typography variant="body2" color="text.secondary">
                Semantic search accuracy with 187ms average response time
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        
        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Recommendation Engine
              </Typography>
              <Box display="flex" alignItems="center" mb={2}>
                <RecommendIcon sx={{ mr: 1, color: 'secondary.main' }} />
                <Typography variant="h4" color="secondary.main">
                  28.7%
                </Typography>
              </Box>
              <Typography variant="body2" color="text.secondary">
                Click-through rate on AI-powered recommendations
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        
        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Conversational AI
              </Typography>
              <Box display="flex" alignItems="center" mb={2}>
                <ChatIcon sx={{ mr: 1, color: 'success.main' }} />
                <Typography variant="h4" color="success.main">
                  4.6/5
                </Typography>
              </Box>
              <Typography variant="body2" color="text.secondary">
                User satisfaction score for AI chat interactions
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );

  const renderSettings = () => (
    <Box>
      <Alert severity="warning" sx={{ mb: 3 }}>
        <Typography variant="body2">
          AI configuration and model management. Adjust AI behavior, retrain models, 
          and configure feature flags for optimal performance.
        </Typography>
      </Alert>
      
      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Model Configuration
              </Typography>
              <List>
                <ListItem>
                  <ListItemText
                    primary="Search Model"
                    secondary="text-embedding-3-large • Last updated: 2 days ago"
                  />
                  <Button size="small" variant="outlined">
                    Update
                  </Button>
                </ListItem>
                <ListItem>
                  <ListItemText
                    primary="Recommendation Model"
                    secondary="Hybrid collaborative filtering • Accuracy: 87.5%"
                  />
                  <Button size="small" variant="outlined">
                    Retrain
                  </Button>
                </ListItem>
                <ListItem>
                  <ListItemText
                    primary="Chat Model"
                    secondary="GPT-4 • Response quality: 96%"
                  />
                  <Button size="small" variant="outlined">
                    Configure
                  </Button>
                </ListItem>
              </List>
            </CardContent>
          </Card>
        </Grid>
        
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Feature Flags
              </Typography>
              <List>
                <ListItem>
                  <ListItemText
                    primary="Visual Search"
                    secondary="Beta feature for image-based product discovery"
                  />
                  <Chip label="Beta" color="warning" size="small" />
                </ListItem>
                <ListItem>
                  <ListItemText
                    primary="Voice Commerce"
                    secondary="Voice-activated shopping and search"
                  />
                  <Chip label="Development" color="default" size="small" />
                </ListItem>
                <ListItem>
                  <ListItemText
                    primary="Predictive Inventory"
                    secondary="AI-powered inventory management"
                  />
                  <Chip label="Planning" color="info" size="small" />
                </ListItem>
              </List>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );

  return (
    <Box sx={{ p: 3 }}>
      {/* Header */}
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={4}>
        <Box>
          <Typography variant="h4" gutterBottom>
            AI Dashboard
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Monitor and manage your AI-powered luxury platform
          </Typography>
        </Box>
        
        <Box display="flex" gap={2}>
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <InputLabel>Time Range</InputLabel>
            <Select
              value={timeRange}
              onChange={(e) => setTimeRange(e.target.value)}
              label="Time Range"
            >
              <MenuItem value="1d">Last 24 Hours</MenuItem>
              <MenuItem value="7d">Last 7 Days</MenuItem>
              <MenuItem value="30d">Last 30 Days</MenuItem>
              <MenuItem value="90d">Last 90 Days</MenuItem>
            </Select>
          </FormControl>
          
          <Button
            variant="contained"
            startIcon={<SettingsIcon />}
            onClick={() => setActiveTab(2)}
          >
            AI Settings
          </Button>
        </Box>
      </Box>

      {/* Navigation Tabs */}
      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs value={activeTab} onChange={handleTabChange}>
          <Tab 
            icon={<DashboardIcon />} 
            label="Overview" 
            iconPosition="start"
          />
          <Tab 
            icon={<AnalyticsIcon />} 
            label="Analytics" 
            iconPosition="start"
          />
          <Tab 
            icon={<SettingsIcon />} 
            label="Settings" 
            iconPosition="start"
          />
        </Tabs>
      </Box>

      {/* Tab Content */}
      {activeTab === 0 && renderOverview()}
      {activeTab === 1 && renderAnalytics()}
      {activeTab === 2 && renderSettings()}

      {/* Insight Details Dialog */}
      <Dialog
        open={showInsightDetails}
        onClose={() => setShowInsightDetails(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>
          AI Insight Details
        </DialogTitle>
        <DialogContent>
          {selectedInsight && (
            <Box>
              <Typography variant="h6" gutterBottom>
                {selectedInsight.title}
              </Typography>
              <Typography variant="body1" paragraph>
                {selectedInsight.description}
              </Typography>
              
              <Box display="flex" gap={1} mb={2}>
                <Chip
                  label={selectedInsight.type}
                  color={
                    selectedInsight.type === 'opportunity' ? 'warning' :
                    selectedInsight.type === 'warning' ? 'error' :
                    selectedInsight.type === 'success' ? 'success' : 'info'
                  }
                />
                <Chip
                  label={`${selectedInsight.priority} priority`}
                  color={
                    selectedInsight.priority === 'high' ? 'error' :
                    selectedInsight.priority === 'medium' ? 'warning' : 'default'
                  }
                />
                {selectedInsight.actionRequired && (
                  <Chip label="Action Required" color="secondary" />
                )}
              </Box>
              
              {selectedInsight.actionRequired && (
                <Alert severity="info">
                  <Typography variant="body2">
                    This insight requires immediate attention. Consider implementing 
                    the suggested changes to optimize AI performance.
                  </Typography>
                </Alert>
              )}
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowInsightDetails(false)}>
            Close
          </Button>
          {selectedInsight?.actionRequired && (
            <Button variant="contained" color="primary">
              Take Action
            </Button>
          )}
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default AIDashboard;