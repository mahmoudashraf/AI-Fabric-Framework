import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Avatar,
  LinearProgress,
  Chip,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  Divider,
  Alert,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Tooltip,
  IconButton,
  Paper,
  CircularProgress,
} from '@mui/material';
import {
  Psychology as PsychologyIcon,
  TrendingUp as TrendingUpIcon,
  Visibility as VisibilityIcon,
  ShoppingCart as ShoppingCartIcon,
  Favorite as FavoriteIcon,
  Search as SearchIcon,
  Timeline as TimelineIcon,
  Insights as InsightsIcon,
  PersonPin as PersonPinIcon,
  Style as StyleIcon,
  LocalOffer as LocalOfferIcon,
  Schedule as ScheduleIcon,
  DeviceHub as DeviceHubIcon,
  Assessment as AssessmentIcon,
  Lightbulb as LightbulbIcon,
  AutoAwesome as AutoAwesomeIcon,
  Info as InfoIcon,
} from '@mui/icons-material';

interface UserBehaviorProfile {
  userId: string;
  profileCompleteness: number;
  behaviorScore: number;
  engagementLevel: 'low' | 'medium' | 'high';
  preferredCategories: string[];
  shoppingPatterns: {
    timeOfDay: string;
    dayOfWeek: string;
    seasonality: string;
    frequency: string;
  };
  interactionPatterns: {
    searchBehavior: string;
    browsingStyle: string;
    decisionSpeed: string;
    pricesensitivity: string;
  };
  personalityTraits: {
    luxuryAffinity: number;
    brandLoyalty: number;
    trendFollowing: number;
    qualityFocus: number;
  };
}

interface BehaviorInsight {
  id: string;
  type: 'opportunity' | 'pattern' | 'recommendation' | 'alert';
  title: string;
  description: string;
  confidence: number;
  impact: 'high' | 'medium' | 'low';
  actionable: boolean;
  suggestedActions?: string[];
  metrics?: {
    label: string;
    value: string | number;
    trend?: 'up' | 'down' | 'stable';
  }[];
}

interface BehavioralInsightsProps {
  userId: string;
  showDetailedAnalysis?: boolean;
  onInsightAction?: (insight: BehaviorInsight, action: string) => void;
  refreshInterval?: number;
}

const BehavioralInsights: React.FC<BehavioralInsightsProps> = ({
  userId,
  showDetailedAnalysis = true,
  onInsightAction,
  refreshInterval = 30000, // 30 seconds
}) => {
  const [profile, setProfile] = useState<UserBehaviorProfile | null>(null);
  const [insights, setInsights] = useState<BehaviorInsight[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedInsight, setSelectedInsight] = useState<BehaviorInsight | null>(null);
  const [showInsightDialog, setShowInsightDialog] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadBehavioralData();
    
    // Set up refresh interval
    const interval = setInterval(loadBehavioralData, refreshInterval);
    return () => clearInterval(interval);
  }, [userId, refreshInterval]);

  const loadBehavioralData = async () => {
    try {
      setError(null);
      
      // Mock API calls - replace with actual behavioral AI service
      await new Promise(resolve => setTimeout(resolve, 1000));
      
      const mockProfile: UserBehaviorProfile = {
        userId,
        profileCompleteness: 87,
        behaviorScore: 0.82,
        engagementLevel: 'high',
        preferredCategories: ['Handbags', 'Jewelry', 'Shoes', 'Accessories'],
        shoppingPatterns: {
          timeOfDay: 'Evening (7-9 PM)',
          dayOfWeek: 'Weekend',
          seasonality: 'Spring/Fall preference',
          frequency: 'Monthly luxury purchases',
        },
        interactionPatterns: {
          searchBehavior: 'Research-driven',
          browsingStyle: 'Detailed explorer',
          decisionSpeed: 'Considered purchaser',
          pricesensitivity: 'Quality over price',
        },
        personalityTraits: {
          luxuryAffinity: 0.91,
          brandLoyalty: 0.76,
          trendFollowing: 0.68,
          qualityFocus: 0.94,
        },
      };
      
      const mockInsights: BehaviorInsight[] = [
        {
          id: '1',
          type: 'opportunity',
          title: 'High Conversion Potential Window',
          description: 'User shows 85% higher engagement on weekend evenings. Personalized recommendations during this time could increase conversion by 40%.',
          confidence: 0.92,
          impact: 'high',
          actionable: true,
          suggestedActions: [
            'Send personalized weekend evening notifications',
            'Highlight limited-time offers during peak engagement',
            'Curate weekend-specific product collections',
          ],
          metrics: [
            { label: 'Engagement Increase', value: '85%', trend: 'up' },
            { label: 'Conversion Potential', value: '40%', trend: 'up' },
            { label: 'Optimal Time', value: 'Sat-Sun 7-9 PM' },
          ],
        },
        {
          id: '2',
          type: 'pattern',
          title: 'Quality-Focused Luxury Buyer',
          description: 'Strong preference for premium quality over trendy items. Responds well to craftsmanship stories and heritage brand narratives.',
          confidence: 0.89,
          impact: 'high',
          actionable: true,
          suggestedActions: [
            'Highlight craftsmanship details in product descriptions',
            'Show brand heritage and artisan stories',
            'Emphasize quality certifications and materials',
          ],
          metrics: [
            { label: 'Quality Focus Score', value: '94%' },
            { label: 'Brand Story Engagement', value: '78%', trend: 'up' },
            { label: 'Premium Product Preference', value: '91%' },
          ],
        },
        {
          id: '3',
          type: 'recommendation',
          title: 'Seasonal Shopping Pattern Detected',
          description: 'User shows strong seasonal buying patterns with 60% of purchases in Spring/Fall. Inventory and marketing should align with these preferences.',
          confidence: 0.76,
          impact: 'medium',
          actionable: true,
          suggestedActions: [
            'Create seasonal collections aligned with preferences',
            'Send pre-season notifications for new arrivals',
            'Offer seasonal styling advice and lookbooks',
          ],
          metrics: [
            { label: 'Seasonal Purchase Rate', value: '60%' },
            { label: 'Spring/Fall Preference', value: '85%' },
            { label: 'Off-Season Engagement', value: '32%', trend: 'down' },
          ],
        },
        {
          id: '4',
          type: 'alert',
          title: 'Engagement Dip Detected',
          description: 'User engagement has decreased by 23% over the past week. Consider re-engagement strategies to maintain relationship.',
          confidence: 0.84,
          impact: 'medium',
          actionable: true,
          suggestedActions: [
            'Send personalized product recommendations',
            'Offer exclusive preview of new collections',
            'Provide styling consultation or personal shopper service',
          ],
          metrics: [
            { label: 'Engagement Change', value: '-23%', trend: 'down' },
            { label: 'Days Since Last Visit', value: '5' },
            { label: 'Risk Level', value: 'Medium' },
          ],
        },
      ];
      
      setProfile(mockProfile);
      setInsights(mockInsights);
    } catch (err) {
      setError('Failed to load behavioral insights');
      console.error('Error loading behavioral data:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleInsightClick = (insight: BehaviorInsight) => {
    setSelectedInsight(insight);
    setShowInsightDialog(true);
  };

  const handleInsightAction = (action: string) => {
    if (selectedInsight) {
      onInsightAction?.(selectedInsight, action);
      setShowInsightDialog(false);
    }
  };

  const getEngagementColor = (level: string) => {
    switch (level) {
      case 'high': return 'success';
      case 'medium': return 'warning';
      case 'low': return 'error';
      default: return 'default';
    }
  };

  const getInsightIcon = (type: string) => {
    switch (type) {
      case 'opportunity': return <LightbulbIcon />;
      case 'pattern': return <TimelineIcon />;
      case 'recommendation': return <AutoAwesomeIcon />;
      case 'alert': return <InfoIcon />;
      default: return <InsightsIcon />;
    }
  };

  const getInsightColor = (type: string) => {
    switch (type) {
      case 'opportunity': return 'warning';
      case 'pattern': return 'info';
      case 'recommendation': return 'success';
      case 'alert': return 'error';
      default: return 'default';
    }
  };

  const renderProfileOverview = () => {
    if (!profile) return null;

    return (
      <Card>
        <CardContent>
          <Box display="flex" alignItems="center" gap={2} mb={3}>
            <Avatar sx={{ bgcolor: 'primary.main', width: 56, height: 56 }}>
              <PsychologyIcon />
            </Avatar>
            <Box>
              <Typography variant="h6">
                Behavioral Profile
              </Typography>
              <Typography variant="body2" color="text.secondary">
                AI-powered user behavior analysis
              </Typography>
            </Box>
          </Box>

          <Grid container spacing={3}>
            <Grid item xs={12} md={6}>
              <Box mb={2}>
                <Typography variant="subtitle2" gutterBottom>
                  Profile Completeness
                </Typography>
                <LinearProgress
                  variant="determinate"
                  value={profile.profileCompleteness}
                  sx={{ height: 8, borderRadius: 4 }}
                />
                <Typography variant="caption" color="text.secondary">
                  {profile.profileCompleteness}% complete
                </Typography>
              </Box>

              <Box mb={2}>
                <Typography variant="subtitle2" gutterBottom>
                  Behavior Score
                </Typography>
                <LinearProgress
                  variant="determinate"
                  value={profile.behaviorScore * 100}
                  color="secondary"
                  sx={{ height: 8, borderRadius: 4 }}
                />
                <Typography variant="caption" color="text.secondary">
                  {Math.round(profile.behaviorScore * 100)}% behavioral understanding
                </Typography>
              </Box>

              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  Engagement Level
                </Typography>
                <Chip
                  label={profile.engagementLevel.toUpperCase()}
                  color={getEngagementColor(profile.engagementLevel) as any}
                  size="small"
                />
              </Box>
            </Grid>

            <Grid item xs={12} md={6}>
              <Typography variant="subtitle2" gutterBottom>
                Preferred Categories
              </Typography>
              <Box display="flex" flexWrap="wrap" gap={1} mb={2}>
                {profile.preferredCategories.map((category) => (
                  <Chip
                    key={category}
                    label={category}
                    size="small"
                    variant="outlined"
                  />
                ))}
              </Box>

              <Typography variant="subtitle2" gutterBottom>
                Shopping Pattern
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {profile.shoppingPatterns.frequency} • {profile.shoppingPatterns.timeOfDay}
              </Typography>
            </Grid>
          </Grid>
        </CardContent>
      </Card>
    );
  };

  const renderPersonalityTraits = () => {
    if (!profile) return null;

    const traits = [
      { label: 'Luxury Affinity', value: profile.personalityTraits.luxuryAffinity, icon: <StyleIcon /> },
      { label: 'Brand Loyalty', value: profile.personalityTraits.brandLoyalty, icon: <FavoriteIcon /> },
      { label: 'Trend Following', value: profile.personalityTraits.trendFollowing, icon: <TrendingUpIcon /> },
      { label: 'Quality Focus', value: profile.personalityTraits.qualityFocus, icon: <AssessmentIcon /> },
    ];

    return (
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Personality Traits
          </Typography>
          <Grid container spacing={2}>
            {traits.map((trait) => (
              <Grid item xs={12} sm={6} key={trait.label}>
                <Box display="flex" alignItems="center" gap={2} mb={1}>
                  <Avatar sx={{ bgcolor: 'secondary.main', width: 32, height: 32 }}>
                    {trait.icon}
                  </Avatar>
                  <Typography variant="body2" fontWeight="medium">
                    {trait.label}
                  </Typography>
                </Box>
                <LinearProgress
                  variant="determinate"
                  value={trait.value * 100}
                  sx={{ height: 6, borderRadius: 3, mb: 0.5 }}
                />
                <Typography variant="caption" color="text.secondary">
                  {Math.round(trait.value * 100)}% strength
                </Typography>
              </Grid>
            ))}
          </Grid>
        </CardContent>
      </Card>
    );
  };

  const renderBehaviorInsights = () => (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          AI Behavioral Insights
        </Typography>
        <List>
          {insights.map((insight, index) => (
            <React.Fragment key={insight.id}>
              <ListItem
                button
                onClick={() => handleInsightClick(insight)}
                sx={{
                  borderRadius: 1,
                  mb: 1,
                  border: '1px solid',
                  borderColor: 'divider',
                  '&:hover': {
                    backgroundColor: 'action.hover',
                  },
                }}
              >
                <ListItemIcon>
                  <Avatar sx={{ bgcolor: `${getInsightColor(insight.type)}.main`, width: 32, height: 32 }}>
                    {getInsightIcon(insight.type)}
                  </Avatar>
                </ListItemIcon>
                <ListItemText
                  primary={
                    <Box display="flex" alignItems="center" gap={1}>
                      <Typography variant="subtitle2">
                        {insight.title}
                      </Typography>
                      <Chip
                        label={`${Math.round(insight.confidence * 100)}% confidence`}
                        size="small"
                        color="primary"
                        variant="outlined"
                      />
                      <Chip
                        label={insight.impact}
                        size="small"
                        color={insight.impact === 'high' ? 'error' : insight.impact === 'medium' ? 'warning' : 'default'}
                      />
                    </Box>
                  }
                  secondary={
                    <Typography variant="body2" color="text.secondary">
                      {insight.description}
                    </Typography>
                  }
                />
                {insight.actionable && (
                  <Chip label="Actionable" size="small" color="success" />
                )}
              </ListItem>
              {index < insights.length - 1 && <Divider />}
            </React.Fragment>
          ))}
        </List>
      </CardContent>
    </Card>
  );

  const renderInsightDialog = () => (
    <Dialog
      open={showInsightDialog}
      onClose={() => setShowInsightDialog(false)}
      maxWidth="md"
      fullWidth
    >
      <DialogTitle>
        Behavioral Insight Details
      </DialogTitle>
      <DialogContent>
        {selectedInsight && (
          <Box>
            <Box display="flex" alignItems="center" gap={2} mb={3}>
              <Avatar sx={{ bgcolor: `${getInsightColor(selectedInsight.type)}.main` }}>
                {getInsightIcon(selectedInsight.type)}
              </Avatar>
              <Box>
                <Typography variant="h6">
                  {selectedInsight.title}
                </Typography>
                <Box display="flex" gap={1} mt={1}>
                  <Chip
                    label={selectedInsight.type}
                    size="small"
                    color={getInsightColor(selectedInsight.type) as any}
                  />
                  <Chip
                    label={`${Math.round(selectedInsight.confidence * 100)}% confidence`}
                    size="small"
                    color="primary"
                  />
                  <Chip
                    label={`${selectedInsight.impact} impact`}
                    size="small"
                    color={selectedInsight.impact === 'high' ? 'error' : selectedInsight.impact === 'medium' ? 'warning' : 'default'}
                  />
                </Box>
              </Box>
            </Box>

            <Typography variant="body1" paragraph>
              {selectedInsight.description}
            </Typography>

            {selectedInsight.metrics && (
              <Box mb={3}>
                <Typography variant="subtitle2" gutterBottom>
                  Key Metrics
                </Typography>
                <Grid container spacing={2}>
                  {selectedInsight.metrics.map((metric, index) => (
                    <Grid item xs={12} sm={4} key={index}>
                      <Paper sx={{ p: 2, textAlign: 'center' }}>
                        <Typography variant="h6" color="primary.main">
                          {metric.value}
                          {metric.trend && (
                            <TrendingUpIcon
                              sx={{
                                ml: 0.5,
                                fontSize: 16,
                                color: metric.trend === 'up' ? 'success.main' : 
                                       metric.trend === 'down' ? 'error.main' : 'text.secondary',
                                transform: metric.trend === 'down' ? 'rotate(180deg)' : 'none',
                              }}
                            />
                          )}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {metric.label}
                        </Typography>
                      </Paper>
                    </Grid>
                  ))}
                </Grid>
              </Box>
            )}

            {selectedInsight.suggestedActions && (
              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  Suggested Actions
                </Typography>
                <List dense>
                  {selectedInsight.suggestedActions.map((action, index) => (
                    <ListItem key={index}>
                      <ListItemIcon>
                        <AutoAwesomeIcon color="primary" />
                      </ListItemIcon>
                      <ListItemText primary={action} />
                      <Button
                        size="small"
                        variant="outlined"
                        onClick={() => handleInsightAction(action)}
                      >
                        Implement
                      </Button>
                    </ListItem>
                  ))}
                </List>
              </Box>
            )}
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={() => setShowInsightDialog(false)}>
          Close
        </Button>
        {selectedInsight?.actionable && (
          <Button variant="contained" color="primary">
            Take Action
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress size={60} />
      </Box>
    );
  }

  if (error) {
    return (
      <Alert severity="error" sx={{ mb: 3 }}>
        {error}
        <Button size="small" onClick={loadBehavioralData} sx={{ ml: 2 }}>
          Retry
        </Button>
      </Alert>
    );
  }

  return (
    <Box>
      <Box display="flex" justifyContent="between" alignItems="center" mb={3}>
        <Typography variant="h5" gutterBottom>
          Behavioral Insights
        </Typography>
        <Tooltip title="Refresh insights">
          <IconButton onClick={loadBehavioralData}>
            <PsychologyIcon />
          </IconButton>
        </Tooltip>
      </Box>

      <Grid container spacing={3}>
        <Grid item xs={12}>
          {renderProfileOverview()}
        </Grid>

        {showDetailedAnalysis && (
          <Grid item xs={12} md={6}>
            {renderPersonalityTraits()}
          </Grid>
        )}

        <Grid item xs={12} md={showDetailedAnalysis ? 6 : 12}>
          {renderBehaviorInsights()}
        </Grid>
      </Grid>

      {renderInsightDialog()}
    </Box>
  );
};

export default BehavioralInsights;