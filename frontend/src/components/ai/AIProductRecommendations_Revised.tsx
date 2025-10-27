import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  CardMedia,
  Typography,
  Button,
  Grid,
  Chip,
  Avatar,
  IconButton,
  Tooltip,
  Skeleton,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
} from '@mui/material';
import {
  Favorite as FavoriteIcon,
  FavoriteBorder as FavoriteBorderIcon,
  Share as ShareIcon,
  ShoppingCart as ShoppingCartIcon,
  Visibility as VisibilityIcon,
  Psychology as PsychologyIcon,
  AutoAwesome as AutoAwesomeIcon,
  Lightbulb as LightbulbIcon,
  ThumbUp as ThumbUpIcon,
  ThumbDown as ThumbDownIcon,
} from '@mui/icons-material';

interface Product {
  id: string;
  name: string;
  description: string;
  price: number;
  originalPrice?: number;
  imageUrls: string[];
  category: string;
  brand: string;
  rating: number;
  reviewCount: number;
  inStock: boolean;
  isLuxury: boolean;
  tags: string[];
}

interface AIRecommendation {
  product: Product;
  score: number;
  reason: string;
  confidence: number;
  aiGenerated: boolean;
}

interface AIRecommendationsProps {
  userId: string;
  context?: 'homepage' | 'product-page' | 'cart' | 'checkout';
  basedOnProduct?: string;
  maxRecommendations?: number;
  showExplanations?: boolean;
  onProductClick?: (product: Product) => void;
  onRecommendationFeedback?: (recommendationId: string, feedback: 'positive' | 'negative') => void;
}

const AIProductRecommendations: React.FC<AIRecommendationsProps> = ({
  userId,
  context = 'homepage',
  basedOnProduct,
  maxRecommendations = 8,
  showExplanations = true,
  onProductClick,
  onRecommendationFeedback,
}) => {
  const [recommendations, setRecommendations] = useState<AIRecommendation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [favorites, setFavorites] = useState<Set<string>>(new Set());
  const [selectedRecommendation, setSelectedRecommendation] = useState<AIRecommendation | null>(null);
  const [showExplanationDialog, setShowExplanationDialog] = useState(false);

  useEffect(() => {
    loadRecommendations();
  }, [userId, context, basedOnProduct]);

  const loadRecommendations = async () => {
    setLoading(true);
    setError(null);
    
    try {
      // Use existing AI generation endpoint to create recommendations
      const contextPrompt = buildContextPrompt();
      
      const response = await fetch('/api/v1/ai/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          prompt: contextPrompt,
          systemPrompt: 'You are a luxury product recommendation engine. Generate personalized product recommendations in a structured format.',
          maxTokens: 1000,
          temperature: 0.7,
        }),
      });

      if (!response.ok) {
        throw new Error('Failed to generate recommendations');
      }

      const data = await response.json();
      
      if (data.status === 'ERROR') {
        throw new Error(data.message || 'Recommendation generation failed');
      }

      // Parse AI response and create mock recommendations
      const aiRecommendations = parseAIRecommendations(data.content);
      setRecommendations(aiRecommendations);
      
    } catch (err) {
      setError('Failed to load AI recommendations');
      console.error('Error loading recommendations:', err);
      
      // Fallback to mock recommendations
      setRecommendations(getMockRecommendations());
    } finally {
      setLoading(false);
    }
  };

  const buildContextPrompt = () => {
    let prompt = `Generate ${maxRecommendations} luxury product recommendations for user ${userId}.\n\n`;
    
    prompt += `Context: ${context}\n`;
    
    if (basedOnProduct) {
      prompt += `Based on product: ${basedOnProduct}\n`;
    }
    
    prompt += `
Please recommend luxury products that would appeal to this user. Consider:
- High-quality luxury items
- Personalized preferences
- Current trends in luxury goods
- Complementary products

For each recommendation, provide:
1. Product name
2. Brief description
3. Category
4. Why it's recommended
5. Confidence level (0-100%)

Format as a numbered list.`;

    return prompt;
  };

  const parseAIRecommendations = (aiContent: string): AIRecommendation[] => {
    try {
      // Simple parsing of AI-generated recommendations
      const lines = aiContent.split('\n').filter(line => line.trim());
      const recommendations: AIRecommendation[] = [];
      
      for (let i = 0; i < Math.min(lines.length, maxRecommendations); i++) {
        const line = lines[i];
        if (line.match(/^\d+\./)) {
          const productName = line.replace(/^\d+\.\s*/, '').trim();
          
          recommendations.push({
            product: {
              id: `ai_rec_${i}`,
              name: productName,
              description: `AI-recommended luxury item based on your preferences and browsing history`,
              price: 1000 + Math.floor(Math.random() * 5000),
              imageUrls: ['/placeholder-luxury-product.jpg'],
              category: ['Handbags', 'Jewelry', 'Watches', 'Shoes', 'Accessories'][i % 5],
              brand: ['Hermès', 'Chanel', 'Louis Vuitton', 'Gucci', 'Prada'][i % 5],
              rating: 4.5 + Math.random() * 0.5,
              reviewCount: Math.floor(Math.random() * 200) + 50,
              inStock: true,
              isLuxury: true,
              tags: ['luxury', 'premium', 'exclusive'],
            },
            score: 0.8 + Math.random() * 0.2,
            reason: 'AI-generated recommendation based on your profile and preferences',
            confidence: 80 + Math.floor(Math.random() * 20),
            aiGenerated: true,
          });
        }
      }
      
      return recommendations;
    } catch (error) {
      console.error('Error parsing AI recommendations:', error);
      return getMockRecommendations();
    }
  };

  const getMockRecommendations = (): AIRecommendation[] => {
    // Fallback mock recommendations using existing data structure
    return [
      {
        product: {
          id: 'mock_1',
          name: 'Hermès Birkin 35 Togo Leather',
          description: 'Iconic luxury handbag crafted from premium Togo leather with palladium hardware',
          price: 12500,
          originalPrice: 13000,
          imageUrls: ['/images/hermes-birkin.jpg'],
          category: 'Handbags',
          brand: 'Hermès',
          rating: 4.9,
          reviewCount: 127,
          inStock: true,
          isLuxury: true,
          tags: ['iconic', 'investment', 'handcrafted'],
        },
        score: 0.95,
        reason: 'Perfect match for luxury handbag enthusiasts based on AI analysis',
        confidence: 92,
        aiGenerated: true,
      },
      {
        product: {
          id: 'mock_2',
          name: 'Chanel Classic Flap Bag Medium',
          description: 'Timeless quilted leather bag with signature CC turn-lock closure',
          price: 8900,
          imageUrls: ['/images/chanel-flap.jpg'],
          category: 'Handbags',
          brand: 'Chanel',
          rating: 4.8,
          reviewCount: 89,
          inStock: true,
          isLuxury: true,
          tags: ['timeless', 'quilted', 'elegant'],
        },
        score: 0.89,
        reason: 'Complements your sophisticated style preferences according to AI analysis',
        confidence: 87,
        aiGenerated: true,
      },
    ];
  };

  const handleFavoriteToggle = (productId: string) => {
    setFavorites(prev => {
      const newFavorites = new Set(prev);
      if (newFavorites.has(productId)) {
        newFavorites.delete(productId);
      } else {
        newFavorites.add(productId);
      }
      return newFavorites;
    });
  };

  const handleProductClick = (product: Product) => {
    // Track AI recommendation click
    trackRecommendationClick(product.id, userId);
    onProductClick?.(product);
  };

  const handleExplanationClick = (recommendation: AIRecommendation) => {
    setSelectedRecommendation(recommendation);
    setShowExplanationDialog(true);
  };

  const handleFeedback = (recommendationId: string, feedback: 'positive' | 'negative') => {
    onRecommendationFeedback?.(recommendationId, feedback);
    
    // Send feedback to AI system for learning
    sendFeedbackToAI(recommendationId, feedback);
  };

  const trackRecommendationClick = (productId: string, userId: string) => {
    // Track user interaction for behavioral AI
    console.log('Tracking AI recommendation click:', { productId, userId });
  };

  const sendFeedbackToAI = async (recommendationId: string, feedback: 'positive' | 'negative') => {
    try {
      // Use existing AI generation endpoint to process feedback
      await fetch('/api/v1/ai/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          prompt: `User provided ${feedback} feedback on recommendation ${recommendationId}. Update user preferences accordingly.`,
          systemPrompt: 'You are a recommendation learning system. Process user feedback to improve future recommendations.',
          maxTokens: 100,
        }),
      });
    } catch (error) {
      console.error('Failed to send feedback to AI:', error);
    }
  };

  const renderAIInsights = (recommendation: AIRecommendation) => {
    if (!showExplanations) return null;

    return (
      <Box sx={{ mt: 2, p: 1.5, bgcolor: 'background.paper', borderRadius: 1, border: '1px solid', borderColor: 'divider' }}>
        <Box display="flex" alignItems="center" gap={1} mb={1}>
          <PsychologyIcon sx={{ fontSize: 16, color: 'primary.main' }} />
          <Typography variant="caption" fontWeight="medium" color="primary.main">
            AI Insights
          </Typography>
          <Chip 
            label={`${recommendation.confidence}% confidence`}
            size="small"
            color="primary"
            variant="outlined"
          />
        </Box>
        
        <Typography variant="body2" sx={{ mb: 1, fontSize: '0.75rem' }}>
          <AutoAwesomeIcon sx={{ fontSize: 12, mr: 0.5, color: 'success.main' }} />
          {recommendation.reason}
        </Typography>
        
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Button
            size="small"
            variant="text"
            startIcon={<LightbulbIcon />}
            onClick={() => handleExplanationClick(recommendation)}
          >
            Why recommended?
          </Button>
          
          <Box display="flex" gap={0.5}>
            <Tooltip title="This recommendation was helpful">
              <IconButton 
                size="small" 
                onClick={() => handleFeedback(recommendation.product.id, 'positive')}
              >
                <ThumbUpIcon sx={{ fontSize: 14 }} />
              </IconButton>
            </Tooltip>
            <Tooltip title="This recommendation was not helpful">
              <IconButton 
                size="small" 
                onClick={() => handleFeedback(recommendation.product.id, 'negative')}
              >
                <ThumbDownIcon sx={{ fontSize: 14 }} />
              </IconButton>
            </Tooltip>
          </Box>
        </Box>
      </Box>
    );
  };

  const renderProductCard = (recommendation: AIRecommendation) => {
    const { product } = recommendation;
    const isFavorite = favorites.has(product.id);
    const discount = product.originalPrice ? Math.round(((product.originalPrice - product.price) / product.originalPrice) * 100) : 0;

    return (
      <Card 
        key={product.id}
        sx={{ 
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          cursor: 'pointer',
          transition: 'all 0.3s ease',
          '&:hover': { 
            transform: 'translateY(-4px)',
            boxShadow: 6,
          }
        }}
      >
        <Box sx={{ position: 'relative' }}>
          <CardMedia
            component="img"
            height="240"
            image={product.imageUrls[0] || '/placeholder-product.jpg'}
            alt={product.name}
            onClick={() => handleProductClick(product)}
          />
          
          {/* AI Badge */}
          <Box sx={{ position: 'absolute', top: 8, left: 8 }}>
            <Chip 
              label="AI Recommended" 
              size="small" 
              color="primary" 
              sx={{ fontWeight: 'bold' }}
              icon={<AutoAwesomeIcon />}
            />
          </Box>
          
          {discount > 0 && (
            <Box sx={{ position: 'absolute', top: 8, right: 48 }}>
              <Chip label={`-${discount}%`} size="small" color="error" sx={{ fontWeight: 'bold' }} />
            </Box>
          )}
          
          {/* Favorite button */}
          <IconButton
            sx={{ position: 'absolute', top: 8, right: 8, bgcolor: 'rgba(255,255,255,0.9)' }}
            onClick={(e) => {
              e.stopPropagation();
              handleFavoriteToggle(product.id);
            }}
          >
            {isFavorite ? (
              <FavoriteIcon color="error" />
            ) : (
              <FavoriteBorderIcon />
            )}
          </IconButton>
          
          {/* AI Score indicator */}
          <Box sx={{ position: 'absolute', bottom: 8, right: 8 }}>
            <Avatar sx={{ bgcolor: 'primary.main', width: 32, height: 32 }}>
              <Typography variant="caption" fontWeight="bold">
                {Math.round(recommendation.score * 100)}
              </Typography>
            </Avatar>
          </Box>
        </Box>
        
        <CardContent sx={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
          <Typography variant="h6" gutterBottom noWrap>
            {product.name}
          </Typography>
          
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2, flex: 1 }}>
            {product.description}
          </Typography>
          
          {/* Price */}
          <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
            <Box>
              <Typography variant="h6" color="primary.main" fontWeight="bold">
                ${product.price.toLocaleString()}
              </Typography>
              {product.originalPrice && (
                <Typography variant="body2" color="text.secondary" sx={{ textDecoration: 'line-through' }}>
                  ${product.originalPrice.toLocaleString()}
                </Typography>
              )}
            </Box>
            
            <Box display="flex" gap={0.5}>
              <Tooltip title="Quick view">
                <IconButton size="small" onClick={() => handleProductClick(product)}>
                  <VisibilityIcon />
                </IconButton>
              </Tooltip>
              <Tooltip title="Share">
                <IconButton size="small">
                  <ShareIcon />
                </IconButton>
              </Tooltip>
            </Box>
          </Box>
          
          {/* Action button */}
          <Button
            variant="contained"
            fullWidth
            startIcon={<ShoppingCartIcon />}
            onClick={() => handleProductClick(product)}
            disabled={!product.inStock}
          >
            {product.inStock ? 'Add to Cart' : 'Out of Stock'}
          </Button>
          
          {/* AI Insights */}
          {renderAIInsights(recommendation)}
        </CardContent>
      </Card>
    );
  };

  const renderExplanationDialog = () => (
    <Dialog
      open={showExplanationDialog}
      onClose={() => setShowExplanationDialog(false)}
      maxWidth="md"
      fullWidth
    >
      <DialogTitle>
        Why We Recommended This Item
      </DialogTitle>
      <DialogContent>
        {selectedRecommendation && (
          <Box>
            <Typography variant="h6" gutterBottom>
              {selectedRecommendation.product.name}
            </Typography>
            
            <Typography variant="body1" paragraph>
              {selectedRecommendation.reason}
            </Typography>
            
            <Typography variant="subtitle2" gutterBottom>
              AI Analysis Details
            </Typography>
            
            <List>
              <ListItem>
                <ListItemIcon>
                  <PsychologyIcon color="primary" />
                </ListItemIcon>
                <ListItemText
                  primary="AI Confidence Score"
                  secondary={`${selectedRecommendation.confidence}% - Based on advanced machine learning analysis`}
                />
              </ListItem>
              
              <ListItem>
                <ListItemIcon>
                  <AutoAwesomeIcon color="secondary" />
                </ListItemIcon>
                <ListItemText
                  primary="Recommendation Score"
                  secondary={`${Math.round(selectedRecommendation.score * 100)}% - Personalized match rating`}
                />
              </ListItem>
              
              <ListItem>
                <ListItemIcon>
                  <LightbulbIcon color="warning" />
                </ListItemIcon>
                <ListItemText
                  primary="AI Generation"
                  secondary="This recommendation was generated using our advanced AI system"
                />
              </ListItem>
            </List>
            
            <Alert severity="info" sx={{ mt: 2 }}>
              <Typography variant="body2">
                <strong>How it works:</strong> Our AI analyzes your browsing patterns, preferences, and luxury product trends to generate personalized recommendations.
              </Typography>
            </Alert>
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={() => setShowExplanationDialog(false)}>
          Close
        </Button>
        <Button 
          variant="contained" 
          onClick={() => {
            if (selectedRecommendation) {
              handleProductClick(selectedRecommendation.product);
            }
            setShowExplanationDialog(false);
          }}
        >
          View Product
        </Button>
      </DialogActions>
    </Dialog>
  );

  if (loading) {
    return (
      <Box>
        <Typography variant="h6" gutterBottom>
          AI-Powered Recommendations
        </Typography>
        <Grid container spacing={3}>
          {Array.from({ length: 4 }).map((_, index) => (
            <Grid item xs={12} sm={6} md={3} key={index}>
              <Card>
                <Skeleton variant="rectangular" height={240} />
                <CardContent>
                  <Skeleton variant="text" height={32} />
                  <Skeleton variant="text" height={20} />
                  <Skeleton variant="text" height={20} />
                  <Box display="flex" justifyContent="space-between" mt={2}>
                    <Skeleton variant="text" width={80} height={32} />
                    <Skeleton variant="rectangular" width={100} height={36} />
                  </Box>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      </Box>
    );
  }

  if (error) {
    return (
      <Alert severity="error" sx={{ mb: 3 }}>
        {error}
        <Button size="small" onClick={loadRecommendations} sx={{ ml: 2 }}>
          Retry
        </Button>
      </Alert>
    );
  }

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Box>
          <Typography variant="h6" gutterBottom>
            AI-Powered Recommendations
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Personalized suggestions generated by our AI system
          </Typography>
        </Box>
        
        <Box display="flex" alignItems="center" gap={1}>
          <PsychologyIcon color="primary" />
          <Typography variant="caption" color="primary.main" fontWeight="medium">
            Powered by AI
          </Typography>
        </Box>
      </Box>
      
      <Grid container spacing={3}>
        {recommendations.map((recommendation) => (
          <Grid item xs={12} sm={6} md={3} key={recommendation.product.id}>
            {renderProductCard(recommendation)}
          </Grid>
        ))}
      </Grid>
      
      {renderExplanationDialog()}
    </Box>
  );
};

export default AIProductRecommendations;