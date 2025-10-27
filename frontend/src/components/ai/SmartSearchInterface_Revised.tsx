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
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  Divider,
  CircularProgress,
  Alert,
  Paper,
  ClickAwayListener,
} from '@mui/material';
import {
  Search as SearchIcon,
  History as HistoryIcon,
  TrendingUp as TrendingUpIcon,
  Clear as ClearIcon,
  AutoAwesome as AutoAwesomeIcon,
  Psychology as PsychologyIcon,
  Speed as SpeedIcon,
} from '@mui/icons-material';

interface SearchResult {
  id: string;
  title: string;
  description: string;
  score: number;
  metadata: Record<string, any>;
  entityType: string;
  entityId: string;
}

interface SearchResponse {
  results: SearchResult[];
  status: string;
  message?: string;
  processingTimeMs?: number;
  totalResults?: number;
}

interface SmartSearchInterfaceProps {
  onSearch?: (query: string) => void;
  onResultClick?: (result: SearchResult) => void;
  placeholder?: string;
  entityType?: string;
  maxResults?: number;
}

const SmartSearchInterface: React.FC<SmartSearchInterfaceProps> = ({
  onSearch,
  onResultClick,
  placeholder = "Search for luxury items...",
  entityType = "product",
  maxResults = 10,
}) => {
  const [query, setQuery] = useState('');
  const [isSearching, setIsSearching] = useState(false);
  const [results, setResults] = useState<SearchResult[]>([]);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [searchHistory, setSearchHistory] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [searchStats, setSearchStats] = useState({
    totalResults: 0,
    searchTime: 0,
  });

  const searchInputRef = useRef<HTMLInputElement>(null);
  const suggestionTimeoutRef = useRef<NodeJS.Timeout>();

  useEffect(() => {
    loadSearchHistory();
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
    // Load from localStorage or use mock data
    const history = JSON.parse(localStorage.getItem('ai-search-history') || '[]');
    if (history.length === 0) {
      // Mock search history
      setSearchHistory([
        'luxury handbags',
        'designer jewelry',
        'italian leather shoes',
        'swiss watches',
        'silk scarves',
      ]);
    } else {
      setSearchHistory(history);
    }
  };

  const loadSuggestions = async (searchQuery: string) => {
    try {
      // Generate AI-powered suggestions using existing AI generation endpoint
      const response = await fetch('/api/v1/ai/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          prompt: `Generate 5 search suggestions related to "${searchQuery}" for luxury products. Return only the suggestions, one per line.`,
          systemPrompt: 'You are a luxury product search assistant. Generate relevant search suggestions.',
          maxTokens: 200,
          temperature: 0.7,
        }),
      });

      if (response.ok) {
        const data = await response.json();
        const aiSuggestions = data.content
          .split('\n')
          .filter((line: string) => line.trim())
          .slice(0, 5);

        // Combine with search history matches
        const historyMatches = searchHistory
          .filter(h => h.toLowerCase().includes(searchQuery.toLowerCase()))
          .slice(0, 3);

        const allSuggestions = [...aiSuggestions, ...historyMatches];
        setSuggestions(allSuggestions.slice(0, 8));
        setShowSuggestions(true);
      }
    } catch (error) {
      console.error('Failed to load suggestions:', error);
      // Fallback to search history only
      const historyMatches = searchHistory
        .filter(h => h.toLowerCase().includes(searchQuery.toLowerCase()))
        .slice(0, 5);
      setSuggestions(historyMatches);
      setShowSuggestions(historyMatches.length > 0);
    }
  };

  const performSearch = async (searchQuery: string = query) => {
    if (!searchQuery.trim()) return;

    setIsSearching(true);
    setError(null);
    const startTime = Date.now();

    try {
      // Use existing AI search endpoint
      const response = await fetch('/api/v1/ai/search', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          query: searchQuery,
          entityType: entityType,
          limit: maxResults,
        }),
      });

      if (!response.ok) {
        throw new Error('Search request failed');
      }

      const data: SearchResponse = await response.json();
      
      if (data.status === 'ERROR') {
        throw new Error(data.message || 'Search failed');
      }

      const searchTime = Date.now() - startTime;
      
      setResults(data.results || []);
      setSearchStats({
        totalResults: data.totalResults || data.results?.length || 0,
        searchTime: data.processingTimeMs || searchTime,
      });

      // Add to search history
      addToSearchHistory(searchQuery);
      setShowSuggestions(false);
      onSearch?.(searchQuery);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Search failed';
      setError(errorMessage);
      setResults([]);
    } finally {
      setIsSearching(false);
    }
  };

  const addToSearchHistory = (searchQuery: string) => {
    const newHistory = [searchQuery, ...searchHistory.filter(q => q !== searchQuery)].slice(0, 10);
    setSearchHistory(newHistory);
    localStorage.setItem('ai-search-history', JSON.stringify(newHistory));
  };

  const handleSuggestionClick = (suggestion: string) => {
    setQuery(suggestion);
    performSearch(suggestion);
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
                    {searchHistory.includes(suggestion) ? <HistoryIcon /> : <AutoAwesomeIcon />}
                  </ListItemIcon>
                  <ListItemText
                    primary={suggestion}
                    secondary={
                      searchHistory.includes(suggestion) ? 'From history' : 'AI suggestion'
                    }
                  />
                  {!searchHistory.includes(suggestion) && (
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
            AI-powered semantic search
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
          <Grid item xs={9}>
            <Box display="flex" justifyContent="space-between" alignItems="flex-start" mb={1}>
              <Typography variant="h6" noWrap>
                {result.title}
              </Typography>
              <Box display="flex" alignItems="center" gap={1}>
                <Chip
                  label={`${Math.round(result.score * 100)}% match`}
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
            
            {result.metadata && (
              <Box display="flex" flexWrap="wrap" gap={1} mb={1}>
                {Object.entries(result.metadata).slice(0, 3).map(([key, value]) => (
                  <Chip
                    key={key}
                    label={`${key}: ${value}`}
                    size="small"
                    variant="outlined"
                  />
                ))}
              </Box>
            )}
          </Grid>
          
          <Grid item xs={3}>
            <Box sx={{ p: 1.5, bgcolor: 'background.default', borderRadius: 1 }}>
              <Box display="flex" alignItems="center" gap={1} mb={1}>
                <AutoAwesomeIcon sx={{ fontSize: 16, color: 'primary.main' }} />
                <Typography variant="caption" fontWeight="medium" color="primary.main">
                  AI Analysis
                </Typography>
              </Box>
              <Typography variant="body2" sx={{ fontSize: '0.75rem' }}>
                Semantic match based on content analysis and user intent
              </Typography>
            </Box>
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

      {/* Error Display */}
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
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
      {!isSearching && query && results.length === 0 && !error && (
        <Box textAlign="center" py={4}>
          <Typography variant="h6" color="text.secondary" gutterBottom>
            No results found
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Try different search terms or check the spelling
          </Typography>
        </Box>
      )}

      {/* Search History */}
      {!query && searchHistory.length > 0 && (
        <Card sx={{ mt: 2 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Recent Searches
            </Typography>
            <Box display="flex" flexWrap="wrap" gap={1}>
              {searchHistory.slice(0, 5).map((search, index) => (
                <Chip
                  key={index}
                  label={search}
                  onClick={() => {
                    setQuery(search);
                    performSearch(search);
                  }}
                  clickable
                  variant="outlined"
                  icon={<HistoryIcon />}
                />
              ))}
            </Box>
          </CardContent>
        </Card>
      )}
    </Box>
  );
};

export default SmartSearchInterface;