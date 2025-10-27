import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  Box,
  TextField,
  Button,
  Card,
  CardContent,
  Typography,
  Chip,
  Grid,
  Avatar,
  IconButton,
  Tooltip,
  Tabs,
  Tab,
  Paper,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  Divider,
  CircularProgress,
  Alert,
  Autocomplete,
  Popper,
  Fade,
  ClickAwayListener,
} from '@mui/material';
import {
  Search as SearchIcon,
  Mic as MicIcon,
  PhotoCamera as PhotoCameraIcon,
  History as HistoryIcon,
  TrendingUp as TrendingUpIcon,
  FilterList as FilterListIcon,
  Clear as ClearIcon,
  AutoAwesome as AutoAwesomeIcon,
  Psychology as PsychologyIcon,
  Speed as SpeedIcon,
  Tune as TuneIcon,
  Bookmark as BookmarkIcon,
  Share as ShareIcon,
} from '@mui/icons-material';

interface SearchResult {
  id: string;
  title: string;
  description: string;
  imageUrl: string;
  price: number;
  category: string;
  brand: string;
  relevanceScore: number;
  aiInsights?: {
    matchReason: string;
    confidence: number;
    tags: string[];
  };
}

interface SearchSuggestion {
  query: string;
  type: 'history' | 'trending' | 'ai-suggested' | 'category';
  count?: number;
  confidence?: number;
}

interface SmartSearchInterfaceProps {
  onSearch?: (query: string, filters?: SearchFilters) => void;
  onResultClick?: (result: SearchResult) => void;
  placeholder?: string;
  showAdvancedOptions?: boolean;
  enableVoiceSearch?: boolean;
  enableVisualSearch?: boolean;
  maxSuggestions?: number;
}

interface SearchFilters {
  category?: string;
  priceRange?: [number, number];
  brand?: string;
  sortBy?: 'relevance' | 'price' | 'rating' | 'newest';
  inStockOnly?: boolean;
}

const SmartSearchInterface: React.FC<SmartSearchInterfaceProps> = ({
  onSearch,
  onResultClick,
  placeholder = "Search for luxury items...",
  showAdvancedOptions = true,
  enableVoiceSearch = true,
  enableVisualSearch = true,
  maxSuggestions = 8,
}) => {
  const [query, setQuery] = useState('');
  const [searchMode, setSearchMode] = useState<'text' | 'voice' | 'visual'>('text');
  const [isSearching, setIsSearching] = useState(false);
  const [isListening, setIsListening] = useState(false);
  const [results, setResults] = useState<SearchResult[]>([]);
  const [suggestions, setSuggestions] = useState<SearchSuggestion[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [searchHistory, setSearchHistory] = useState<string[]>([]);
  const [filters, setFilters] = useState<SearchFilters>({});
  const [showFilters, setShowFilters] = useState(false);
  const [searchStats, setSearchStats] = useState({
    totalResults: 0,
    searchTime: 0,
    aiConfidence: 0,
  });

  const searchInputRef = useRef<HTMLInputElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const suggestionTimeoutRef = useRef<NodeJS.Timeout>();

  useEffect(() => {
    loadSearchHistory();
    loadTrendingSuggestions();
  }, []);

  useEffect(() => {
    if (query.length > 2) {
      // Debounce suggestion loading
      if (suggestionTimeoutRef.current) {
        clearTimeout(suggestionTimeoutRef.current);
      }
      
      suggestionTimeoutRef.current = setTimeout(() => {
        loadSuggestions(query);
      }, 300);
    } else {
      setSuggestions([]);
      setShowSuggestions(false);
    }

    return () => {
      if (suggestionTimeoutRef.current) {
        clearTimeout(suggestionTimeoutRef.current);
      }
    };
  }, [query]);

  const loadSearchHistory = () => {
    // Mock search history - in real app, load from localStorage or API
    const history = [
      'luxury handbags',
      'designer jewelry',
      'italian leather shoes',
      'swiss watches',
      'silk scarves',
    ];
    setSearchHistory(history);
  };

  const loadTrendingSuggestions = () => {
    // Mock trending searches
    const trending: SearchSuggestion[] = [
      { query: 'sustainable luxury', type: 'trending', count: 1247 },
      { query: 'vintage designer bags', type: 'trending', count: 892 },
      { query: 'minimalist jewelry', type: 'trending', count: 634 },
    ];
    setSuggestions(trending);
  };

  const loadSuggestions = async (searchQuery: string) => {
    try {
      // Mock AI-powered suggestions
      const aiSuggestions: SearchSuggestion[] = [
        { 
          query: `${searchQuery} luxury collection`, 
          type: 'ai-suggested', 
          confidence: 0.92 
        },
        { 
          query: `premium ${searchQuery}`, 
          type: 'ai-suggested', 
          confidence: 0.87 
        },
        { 
          query: `designer ${searchQuery} 2024`, 
          type: 'ai-suggested', 
          confidence: 0.81 
        },
      ];

      // Add search history matches
      const historyMatches = searchHistory
        .filter(h => h.toLowerCase().includes(searchQuery.toLowerCase()))
        .map(h => ({ query: h, type: 'history' as const }));

      const allSuggestions = [...aiSuggestions, ...historyMatches].slice(0, maxSuggestions);
      setSuggestions(allSuggestions);
      setShowSuggestions(true);
    } catch (error) {
      console.error('Failed to load suggestions:', error);
    }
  };

  const performSearch = async (searchQuery: string = query) => {
    if (!searchQuery.trim()) return;

    setIsSearching(true);
    const startTime = Date.now();

    try {
      // Mock AI-powered search
      await new Promise(resolve => setTimeout(resolve, 800));

      const mockResults: SearchResult[] = [
        {
          id: '1',
          title: 'Hermès Birkin 35 Togo Leather Bag',
          description: 'Iconic luxury handbag in premium Togo leather with palladium hardware',
          imageUrl: '/images/hermes-birkin.jpg',
          price: 12500,
          category: 'Handbags',
          brand: 'Hermès',
          relevanceScore: 0.95,
          aiInsights: {
            matchReason: 'Perfect match for luxury handbag search with premium materials',
            confidence: 0.92,
            tags: ['iconic', 'investment piece', 'handcrafted'],
          },
        },
        {
          id: '2',
          title: 'Chanel Classic Flap Bag Medium',
          description: 'Timeless quilted leather bag with signature CC turn-lock closure',
          imageUrl: '/images/chanel-flap.jpg',
          price: 8900,
          category: 'Handbags',
          brand: 'Chanel',
          relevanceScore: 0.89,
          aiInsights: {
            matchReason: 'High relevance for luxury bag enthusiasts seeking timeless design',
            confidence: 0.87,
            tags: ['timeless', 'quilted', 'elegant'],
          },
        },
        {
          id: '3',
          title: 'Louis Vuitton Neverfull MM Monogram',
          description: 'Versatile tote bag in iconic Monogram canvas with leather trim',
          imageUrl: '/images/lv-neverfull.jpg',
          price: 1760,
          category: 'Handbags',
          brand: 'Louis Vuitton',
          relevanceScore: 0.82,
          aiInsights: {
            matchReason: 'Popular choice for everyday luxury with practical design',
            confidence: 0.79,
            tags: ['versatile', 'monogram', 'everyday'],
          },
        },
      ];

      const searchTime = Date.now() - startTime;
      
      setResults(mockResults);
      setSearchStats({
        totalResults: mockResults.length,
        searchTime,
        aiConfidence: 0.89,
      });

      // Add to search history
      if (!searchHistory.includes(searchQuery)) {
        setSearchHistory(prev => [searchQuery, ...prev.slice(0, 9)]);
      }

      setShowSuggestions(false);
      onSearch?.(searchQuery, filters);
    } catch (error) {
      console.error('Search failed:', error);
    } finally {
      setIsSearching(false);
    }
  };

  const handleVoiceSearch = useCallback(async () => {
    if (!enableVoiceSearch) return;

    setIsListening(true);
    setSearchMode('voice');

    try {
      // Check for speech recognition support
      if (!('webkitSpeechRecognition' in window) && !('SpeechRecognition' in window)) {
        throw new Error('Speech recognition not supported');
      }

      const SpeechRecognition = (window as any).webkitSpeechRecognition || (window as any).SpeechRecognition;
      const recognition = new SpeechRecognition();

      recognition.continuous = false;
      recognition.interimResults = false;
      recognition.lang = 'en-US';

      recognition.onresult = (event: any) => {
        const transcript = event.results[0][0].transcript;
        setQuery(transcript);
        performSearch(transcript);
      };

      recognition.onerror = (event: any) => {
        console.error('Speech recognition error:', event.error);
        setIsListening(false);
        setSearchMode('text');
      };

      recognition.onend = () => {
        setIsListening(false);
        setSearchMode('text');
      };

      recognition.start();
    } catch (error) {
      console.error('Voice search failed:', error);
      setIsListening(false);
      setSearchMode('text');
    }
  }, [enableVoiceSearch, filters]);

  const handleVisualSearch = useCallback(async (file: File) => {
    if (!enableVisualSearch) return;

    setIsSearching(true);
    setSearchMode('visual');

    try {
      const formData = new FormData();
      formData.append('image', file);

      // Mock visual search API call
      await new Promise(resolve => setTimeout(resolve, 2000));

      const visualSearchQuery = 'luxury handbag similar to uploaded image';
      setQuery(visualSearchQuery);
      await performSearch(visualSearchQuery);
    } catch (error) {
      console.error('Visual search failed:', error);
    } finally {
      setSearchMode('text');
    }
  }, [enableVisualSearch]);

  const handleSuggestionClick = (suggestion: SearchSuggestion) => {
    setQuery(suggestion.query);
    performSearch(suggestion.query);
  };

  const renderSearchInput = () => (
    <Box sx={{ position: 'relative' }}>
      <TextField
        ref={searchInputRef}
        fullWidth
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        onKeyPress={(e) => e.key === 'Enter' && performSearch()}
        onFocus={() => query.length > 2 && setShowSuggestions(true)}
        placeholder={placeholder}
        variant="outlined"
        size="large"
        InputProps={{
          startAdornment: (
            <Box display="flex" alignItems="center" mr={1}>
              <PsychologyIcon color="primary" sx={{ mr: 1 }} />
              <Typography variant="caption" color="primary.main" fontWeight="medium">
                AI
              </Typography>
            </Box>
          ),
          endAdornment: (
            <Box display="flex" alignItems="center" gap={1}>
              {query && (
                <IconButton size="small" onClick={() => setQuery('')}>
                  <ClearIcon />
                </IconButton>
              )}
              
              {enableVoiceSearch && (
                <Tooltip title="Voice search">
                  <IconButton
                    onClick={handleVoiceSearch}
                    disabled={isListening}
                    color={isListening ? 'error' : 'default'}
                  >
                    <MicIcon />
                  </IconButton>
                </Tooltip>
              )}
              
              {enableVisualSearch && (
                <Tooltip title="Visual search">
                  <IconButton onClick={() => fileInputRef.current?.click()}>
                    <PhotoCameraIcon />
                  </IconButton>
                </Tooltip>
              )}
              
              <Button
                variant="contained"
                onClick={() => performSearch()}
                disabled={isSearching || !query.trim()}
                startIcon={isSearching ? <CircularProgress size={16} /> : <SearchIcon />}
              >
                {isSearching ? 'Searching...' : 'Search'}
              </Button>
            </Box>
          ),
        }}
        sx={{
          '& .MuiOutlinedInput-root': {
            borderRadius: 3,
            bgcolor: 'background.paper',
          },
        }}
      />

      {/* Hidden file input for visual search */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        style={{ display: 'none' }}
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) handleVisualSearch(file);
        }}
      />

      {/* Search suggestions dropdown */}
      {showSuggestions && suggestions.length > 0 && (
        <ClickAwayListener onClickAway={() => setShowSuggestions(false)}>
          <Paper
            sx={{
              position: 'absolute',
              top: '100%',
              left: 0,
              right: 0,
              zIndex: 1300,
              mt: 1,
              maxHeight: 400,
              overflow: 'auto',
              border: '1px solid',
              borderColor: 'divider',
            }}
          >
            <List dense>
              {suggestions.map((suggestion, index) => (
                <ListItem
                  key={index}
                  button
                  onClick={() => handleSuggestionClick(suggestion)}
                  sx={{
                    '&:hover': {
                      bgcolor: 'action.hover',
                    },
                  }}
                >
                  <ListItemIcon>
                    {suggestion.type === 'history' && <HistoryIcon />}
                    {suggestion.type === 'trending' && <TrendingUpIcon />}
                    {suggestion.type === 'ai-suggested' && <AutoAwesomeIcon />}
                    {suggestion.type === 'category' && <FilterListIcon />}
                  </ListItemIcon>
                  <ListItemText
                    primary={suggestion.query}
                    secondary={
                      suggestion.type === 'trending' && suggestion.count
                        ? `${suggestion.count} searches`
                        : suggestion.type === 'ai-suggested' && suggestion.confidence
                        ? `${Math.round(suggestion.confidence * 100)}% confidence`
                        : undefined
                    }
                  />
                  {suggestion.type === 'ai-suggested' && (
                    <Chip label="AI" size="small" color="primary" />
                  )}
                </ListItem>
              ))}
            </List>
          </Paper>
        </ClickAwayListener>
      )}
    </Box>
  );

  const renderSearchStats = () => {
    if (!results.length) return null;

    return (
      <Box display="flex" alignItems="center" gap={3} mb={2}>
        <Typography variant="body2" color="text.secondary">
          {searchStats.totalResults} results found in {searchStats.searchTime}ms
        </Typography>
        <Box display="flex" alignItems="center" gap={1}>
          <SpeedIcon sx={{ fontSize: 16, color: 'success.main' }} />
          <Typography variant="body2" color="success.main">
            {Math.round(searchStats.aiConfidence * 100)}% AI confidence
          </Typography>
        </Box>
      </Box>
    );
  };

  const renderSearchResult = (result: SearchResult) => (
    <Card
      key={result.id}
      sx={{
        cursor: 'pointer',
        transition: 'all 0.3s ease',
        '&:hover': {
          transform: 'translateY(-2px)',
          boxShadow: 4,
        },
      }}
      onClick={() => onResultClick?.(result)}
    >
      <CardContent>
        <Grid container spacing={2}>
          <Grid item xs={3}>
            <Box
              component="img"
              src={result.imageUrl || '/placeholder-product.jpg'}
              alt={result.title}
              sx={{
                width: '100%',
                height: 120,
                objectFit: 'cover',
                borderRadius: 1,
              }}
            />
          </Grid>
          <Grid item xs={9}>
            <Box display="flex" justifyContent="space-between" alignItems="flex-start" mb={1}>
              <Typography variant="h6" noWrap>
                {result.title}
              </Typography>
              <Box display="flex" alignItems="center" gap={1}>
                <Chip
                  label={`${Math.round(result.relevanceScore * 100)}% match`}
                  size="small"
                  color="primary"
                />
                <Avatar sx={{ bgcolor: 'success.main', width: 24, height: 24 }}>
                  <Typography variant="caption" fontWeight="bold">
                    AI
                  </Typography>
                </Avatar>
              </Box>
            </Box>
            
            <Typography variant="body2" color="text.secondary" paragraph>
              {result.description}
            </Typography>
            
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
              <Typography variant="h6" color="primary.main">
                ${result.price.toLocaleString()}
              </Typography>
              <Box display="flex" gap={1}>
                <Chip label={result.brand} size="small" variant="outlined" />
                <Chip label={result.category} size="small" variant="outlined" />
              </Box>
            </Box>
            
            {result.aiInsights && (
              <Box sx={{ p: 1.5, bgcolor: 'background.default', borderRadius: 1 }}>
                <Box display="flex" alignItems="center" gap={1} mb={1}>
                  <AutoAwesomeIcon sx={{ fontSize: 16, color: 'primary.main' }} />
                  <Typography variant="caption" fontWeight="medium" color="primary.main">
                    AI Insights
                  </Typography>
                </Box>
                <Typography variant="body2" sx={{ mb: 1 }}>
                  {result.aiInsights.matchReason}
                </Typography>
                <Box display="flex" flexWrap="wrap" gap={0.5}>
                  {result.aiInsights.tags.map((tag) => (
                    <Chip key={tag} label={tag} size="small" variant="outlined" />
                  ))}
                </Box>
              </Box>
            )}
          </Grid>
        </Grid>
      </CardContent>
    </Card>
  );

  return (
    <Box>
      {/* Search Input */}
      <Box mb={3}>
        {renderSearchInput()}
      </Box>

      {/* Voice Search Feedback */}
      {isListening && (
        <Alert severity="info" sx={{ mb: 2 }}>
          <Box display="flex" alignItems="center" gap={1}>
            <MicIcon color="error" />
            <Typography>
              Listening... Speak your search query now
            </Typography>
          </Box>
        </Alert>
      )}

      {/* Search Mode Indicator */}
      {searchMode !== 'text' && (
        <Box mb={2}>
          <Chip
            label={
              searchMode === 'voice' ? 'Voice Search Active' :
              searchMode === 'visual' ? 'Visual Search Processing' : ''
            }
            color="primary"
            icon={searchMode === 'voice' ? <MicIcon /> : <PhotoCameraIcon />}
          />
        </Box>
      )}

      {/* Search Stats */}
      {renderSearchStats()}

      {/* Search Results */}
      {results.length > 0 && (
        <Box display="flex" flexDirection="column" gap={2}>
          {results.map(renderSearchResult)}
        </Box>
      )}

      {/* No Results */}
      {!isSearching && query && results.length === 0 && (
        <Box textAlign="center" py={4}>
          <Typography variant="h6" color="text.secondary" gutterBottom>
            No results found
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Try adjusting your search terms or use voice/visual search
          </Typography>
        </Box>
      )}
    </Box>
  );
};

export default SmartSearchInterface;