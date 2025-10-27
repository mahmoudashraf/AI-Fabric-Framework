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
  Divider,
  Rating,
  LinearProgress,
} from '@mui/material';
import {
  Favorite as FavoriteIcon,
  FavoriteBorder as FavoriteBorderIcon,
  Share as ShareIcon,
  ShoppingCart as ShoppingCartIcon,
  Visibility as VisibilityIcon,
  Psychology as PsychologyIcon,
  TrendingUp as TrendingUpIcon,
  Star as StarIcon,
  LocalOffer as LocalOfferIcon,
  Style as StyleIcon,
  AutoAwesome as AutoAwesomeIcon,
  Lightbulb as LightbulbIcon,
  Timeline as TimelineIcon,
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
  personalizedReason?: string;
  trendingScore?: number;
  styleMatch?: number;
  priceInsight?: string;
  behaviorMatch?: number;
  similarityScore?: number;
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
      // Mock API call - replace with actual AI recommendation service
      await new Promise(resolve => setTimeout(resolve, 1000));
      
      const mockRecommendations: AIRecommendation[] = [
        {
          product: {
            id: '1',
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
          reason: 'Perfect match for luxury handbag enthusiasts',
          confidence: 0.92,
          personalizedReason: 'Based on your preference for iconic luxury pieces and investment bags',
          trendingScore: 0.88,
          styleMatch: 94,
          priceInsight: 'Excellent investment piece - 15% annual appreciation',
          behaviorMatch: 0.91,
          similarityScore: 0.87,
        },
        {
          product: {
            id: '2',
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
          reason: 'Complements your sophisticated style preferences',
          confidence: 0.87,
          personalizedReason: 'Your browsing history shows strong interest in Chanel accessories',
          trendingScore: 0.75,
          styleMatch: 87,
          priceInsight: 'Price increased 8% this year - good time to purchase',
          behaviorMatch: 0.84,
          similarityScore: 0.82,
        },
        {
          product: {
            id: '3',
            name: 'Louis Vuitton Neverfull MM Monogram',
            description: 'Versatile tote bag in iconic Monogram canvas with leather trim',
            price: 1760,
            imageUrls: ['/images/lv-neverfull.jpg'],
            category: 'Handbags',
            brand: 'Louis Vuitton',
            rating: 4.7,
            reviewCount: 234,
            inStock: true,
            isLuxury: true,
            tags: ['versatile', 'monogram', 'everyday'],
          },
          score: 0.82,
          reason: 'Great everyday luxury option',
          confidence: 0.79,
          personalizedReason: 'Perfect for your active lifestyle based on your purchase history',
          trendingScore: 0.92,
          styleMatch: 78,
          priceInsight: 'Most popular item in this category',
          behaviorMatch: 0.76,
          similarityScore: 0.71,
        },
        {
          product: {
            id: '4',
            name: 'Bottega Veneta Jodie Mini Intrecciato',
            description: 'Contemporary hobo bag featuring signature intrecciato weave',
            price: 2890,
            imageUrls: ['/images/bottega-jodie.jpg'],
            category: 'Handbags',
            brand: 'Bottega Veneta',
            rating: 4.6,
            reviewCount: 56,
            inStock: true,
            isLuxury: true,
            tags: ['contemporary', 'intrecciato', 'minimalist'],
          },
          score: 0.76,
          reason: 'Trending contemporary luxury design',
          confidence: 0.73,
          personalizedReason: 'Matches your interest in minimalist luxury aesthetics',
          trendingScore: 0.95,
          styleMatch: 71,
          priceInsight: 'Rising popularity - 25% increase in searches',
          behaviorMatch: 0.68,
          similarityScore: 0.64,
        },
      ];
      
      setRecommendations(mockRecommendations.slice(0, maxRecommendations));
    } catch (err) {
      setError('Failed to load AI recommendations');
      console.error('Error loading recommendations:', err);
    } finally {
      setLoading(false);
    }
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
    // Update local state or show confirmation
  };

  const trackRecommendationClick = (productId: string, userId: string) => {
    // Track user interaction for behavioral AI
    console.log('Tracking recommendation click:', { productId, userId });
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
            label={`${Math.round(recommendation.confidence * 100)}% confidence`}
            size="small"
            color="primary"
            variant="outlined"
          />
        </Box>
        
        {recommendation.personalizedReason && (
          <Typography variant="body2" sx={{ mb: 1, fontSize: '0.75rem' }}>
            <AutoAwesomeIcon sx={{ fontSize: 12, mr: 0.5, color: 'success.main' }} />
            {recommendation.personalizedReason}
          </Typography>
        )}
        
        <Box display="flex" flexWrap="wrap" gap={1} mb={1}>
          {recommendation.styleMatch && (
            <Chip
              label={`${recommendation.styleMatch}% style match`}
              size="small"
              color="secondary"
              variant="outlined"
              icon={<StyleIcon />}
            />
          )}
          
          {recommendation.trendingScore && recommendation.trendingScore > 0.7 && (
            <Chip
              label="Trending"
              size="small"
              color="warning"
              variant="outlined"
              icon={<TrendingUpIcon />}
            />
          )}
          
          {recommendation.priceInsight && (
            <Tooltip title={recommendation.priceInsight}>
              <Chip
                label="Price insight"
                size="small"
                color="info"
                variant="outlined"
                icon={<LocalOfferIcon />}
              />
            </Tooltip>
          )}
        </Box>
        
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
          
          {/* Overlay badges */}
          <Box sx={{ position: 'absolute', top: 8, left: 8, display: 'flex', flexDirection: 'column', gap: 1 }}>
            {product.isLuxury && (
              <Chip label="Luxury" size="small" color="warning" sx={{ fontWeight: 'bold' }} />
            )}
            {discount > 0 && (
              <Chip label={`-${discount}%`} size="small" color="error" sx={{ fontWeight: 'bold' }} />
            )}
            {recommendation.trendingScore && recommendation.trendingScore > 0.8 && (
              <Chip label="Trending" size="small" color="info" sx={{ fontWeight: 'bold' }} />
            )}
          </Box>
          
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
          
          {/* Rating */}
          <Box display="flex" alignItems="center" gap={1} mb={2}>
            <Rating value={product.rating} precision={0.1} size="small" readOnly />
            <Typography variant="caption" color="text.secondary">
              ({product.reviewCount})
            </Typography>
          </Box>
          
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
                  primary="Personalization Score"
                  secondary={`${Math.round((selectedRecommendation.behaviorMatch || 0) * 100)}% - Based on your browsing and purchase history`}
                />
              </ListItem>
              
              <ListItem>
                <ListItemIcon>
                  <StyleIcon color="secondary" />
                </ListItemIcon>
                <ListItemText
                  primary="Style Match"
                  secondary={`${selectedRecommendation.styleMatch}% - Aligns with your aesthetic preferences`}
                />
              </ListItem>
              
              <ListItem>
                <ListItemIcon>
                  <TrendingUpIcon color="warning" />
                </ListItemIcon>
                <ListItemText
                  primary="Trending Score"
                  secondary={`${Math.round((selectedRecommendation.trendingScore || 0) * 100)}% - Current popularity and demand`}
                />
              </ListItem>
              
              <ListItem>
                <ListItemIcon>
                  <StarIcon color="success" />
                </ListItemIcon>
                <ListItemText
                  primary="Overall Confidence"
                  secondary={`${Math.round(selectedRecommendation.confidence * 100)}% - AI model confidence in this recommendation`}
                />
              </ListItem>
            </List>
            
            {selectedRecommendation.priceInsight && (
              <Alert severity="info" sx={{ mt: 2 }}>
                <Typography variant="body2">
                  <strong>Price Insight:</strong> {selectedRecommendation.priceInsight}
                </Typography>
              </Alert>
            )}
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
            Personalized suggestions based on your preferences and behavior
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