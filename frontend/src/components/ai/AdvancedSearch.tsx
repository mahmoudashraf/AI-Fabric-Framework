import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  TextField,
  Button,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Chip,
  Alert,
  CircularProgress,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  Divider,
  IconButton,
  Tooltip,
  Tabs,
  Tab,
} from '@mui/material';
import {
  Search as SearchIcon,
  ExpandMore as ExpandMoreIcon,
  ContentCopy as ContentCopyIcon,
  Bookmark as BookmarkIcon,
  Share as ShareIcon,
  Download as DownloadIcon,
} from '@mui/icons-material';

interface AdvancedSearchProps {
  onSearch?: (query: string, options: SearchOptions) => void;
  onResultClick?: (result: SearchResult) => void;
  initialQuery?: string;
  showAdvancedOptions?: boolean;
  maxResults?: number;
}

interface SearchOptions {
  maxResults: number;
  maxDocuments: number;
  expansionLevel: number;
  rerankingStrategy: string;
  contextOptimizationLevel: string;
  enableHybridSearch: boolean;
  enableContextualSearch: boolean;
  categories: string[];
  context: string;
  language: string;
  domain: string;
  timeRange: string;
  minConfidenceScore: number;
  enableExplanation: boolean;
  enableHighlighting: boolean;
}

interface SearchResult {
  id: string;
  title: string;
  content: string;
  type: string;
  score: number;
  similarity: number;
  metadata: Record<string, any>;
  source: string;
  createdAt: string;
  author: string;
  tags: string[];
  category: string;
  wordCount: number;
  language: string;
}

interface SearchResponse {
  query: string;
  expandedQueries: string[];
  response: string;
  context: string;
  documents: SearchResult[];
  totalDocuments: number;
  usedDocuments: number;
  relevanceScores: number[];
  confidenceScore: number;
  processingTimeMs: number;
  success: boolean;
  errorMessage?: string;
  rerankingStrategy: string;
  expansionLevel: number;
  contextOptimizationLevel: string;
  explanation: string;
  highlightedResponse: string;
  querySuggestions: string[];
  relatedTopics: string[];
  documentSummaries: string[];
}

const AdvancedSearchSimple: React.FC<AdvancedSearchProps> = ({
  onSearch,
  initialQuery = '',
  showAdvancedOptions = true,
  maxResults = 10,
}) => {
  const [activeTab, setActiveTab] = useState(0);
  const [query, setQuery] = useState(initialQuery);
  const [options, setOptions] = useState<SearchOptions>({
    maxResults: maxResults,
    maxDocuments: 5,
    expansionLevel: 2,
    rerankingStrategy: 'hybrid',
    contextOptimizationLevel: 'medium',
    enableHybridSearch: true,
    enableContextualSearch: true,
    categories: [],
    context: '',
    language: 'en',
    domain: '',
    timeRange: '',
    minConfidenceScore: 0.5,
    enableExplanation: true,
    enableHighlighting: true,
  });
  const [response, setResponse] = useState<SearchResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [searchHistory, setSearchHistory] = useState<string[]>([]);
  const [savedSearches, setSavedSearches] = useState<string[]>([]);

  useEffect(() => {
    loadSearchHistory();
    loadSavedSearches();
  }, []);

  const loadSearchHistory = () => {
    const history = [
      'machine learning algorithms',
      'data privacy compliance',
      'AI security best practices',
      'advanced RAG implementation',
      'vector database optimization',
    ];
    setSearchHistory(history);
  };

  const loadSavedSearches = () => {
    const saved = [
      'AI infrastructure monitoring',
      'compliance audit procedures',
      'security threat detection',
    ];
    setSavedSearches(saved);
  };

  const handleSearch = async () => {
    if (!query.trim()) {
      setError('Please enter a search query');
      return;
    }

    setLoading(true);
    setError(null);
    
    try {
      // Mock search response
      const mockResponse: SearchResponse = {
        query,
        expandedQueries: [
          query,
          `${query} best practices`,
          `${query} implementation`,
          `${query} optimization`,
        ],
        response: `Based on the search results, here's a comprehensive answer about "${query}". The information shows that this topic involves multiple aspects including technical implementation, best practices, and real-world applications.`,
        context: `Context information about ${query} including relevant background, technical details, and practical applications.`,
        documents: Array.from({ length: options.maxResults }, (_, i) => ({
          id: `DOC_${i + 1}`,
          title: `${query} - Document ${i + 1}`,
          content: `This is the content of document ${i + 1} related to ${query}. It contains detailed information about the topic including technical specifications, implementation details, and practical examples.`,
          type: (['TECHNICAL', 'GUIDE', 'REFERENCE', 'TUTORIAL'][i % 4]) as string,
          score: Math.random(),
          similarity: Math.random(),
          metadata: {
            category: (['AI', 'Security', 'Compliance', 'Development'][i % 4]) as string,
            difficulty: (['Beginner', 'Intermediate', 'Advanced'][i % 3]) as string,
            lastUpdated: new Date().toISOString(),
          },
          source: `Source ${i + 1}`,
          createdAt: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString(),
          author: `Author ${i + 1}`,
          tags: [`tag${i + 1}`, `category${i + 1}`, 'ai', 'search'],
          category: (['AI', 'Security', 'Compliance', 'Development'][i % 4]) as string,
          wordCount: Math.floor(Math.random() * 2000) + 500,
          language: 'en',
        })),
        totalDocuments: Math.floor(Math.random() * 100) + 20,
        usedDocuments: options.maxDocuments,
        relevanceScores: Array.from({ length: options.maxResults }, () => Math.random()),
        confidenceScore: Math.random(),
        processingTimeMs: Math.floor(Math.random() * 1000) + 200,
        success: true,
        rerankingStrategy: options.rerankingStrategy,
        expansionLevel: options.expansionLevel,
        contextOptimizationLevel: options.contextOptimizationLevel,
        explanation: `The search used ${options.rerankingStrategy} re-ranking strategy with expansion level ${options.expansionLevel}.`,
        highlightedResponse: `Based on the search results, here's a comprehensive answer about "${query}".`,
        querySuggestions: [
          `${query} tutorial`,
          `${query} examples`,
          `${query} documentation`,
          `${query} best practices`,
        ],
        relatedTopics: [
          'Machine Learning',
          'Data Science',
          'Artificial Intelligence',
          'Natural Language Processing',
        ],
        documentSummaries: Array.from({ length: 3 }, (_, i) => 
          `Summary ${i + 1}: Key insights about ${query} including main concepts, implementation approaches, and practical applications.`
        ),
      };
      
      setResponse(mockResponse);
      addToSearchHistory(query);
      onSearch?.(query, options);
    } catch (err) {
      setError('Failed to perform search');
      console.error('Error performing search:', err);
    } finally {
      setLoading(false);
    }
  };

  const addToSearchHistory = (searchQuery: string) => {
    setSearchHistory(prev => {
      const newHistory = [searchQuery, ...prev.filter(q => q !== searchQuery)];
      return newHistory.slice(0, 10);
    });
  };

  const handleOptionChange = (field: keyof SearchOptions, value: any) => {
    setOptions(prev => ({ ...prev, [field]: value }));
  };

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
  };

  const saveSearch = (searchQuery: string) => {
    setSavedSearches(prev => [...prev, searchQuery]);
  };

  const renderSearchForm = () => (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Advanced Search
        </Typography>
        
        <Box display="flex" flexDirection="column" gap={3}>
          <TextField
            label="Search Query"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            fullWidth
            multiline
            rows={3}
            placeholder="Enter your search query here..."
            InputProps={{
              endAdornment: (
                <IconButton onClick={handleSearch} disabled={loading}>
                  <SearchIcon />
                </IconButton>
              ),
            }}
          />
          
          {showAdvancedOptions && (
            <Box display="flex" flexDirection="column" gap={3}>
              <Box display="flex" gap={2}>
                <TextField
                  label="Context"
                  value={options.context}
                  onChange={(e) => handleOptionChange('context', e.target.value)}
                  fullWidth
                  multiline
                  rows={2}
                  placeholder="Additional context for the search..."
                />
                
                <TextField
                  label="Domain"
                  value={options.domain}
                  onChange={(e) => handleOptionChange('domain', e.target.value)}
                  fullWidth
                  placeholder="e.g., technology, healthcare, finance"
                />
              </Box>
              
              <Box display="flex" gap={2}>
                <FormControl fullWidth>
                  <InputLabel>Re-ranking Strategy</InputLabel>
                  <Select
                    value={options.rerankingStrategy}
                    onChange={(e) => handleOptionChange('rerankingStrategy', e.target.value)}
                  >
                    <MenuItem value="semantic">Semantic</MenuItem>
                    <MenuItem value="hybrid">Hybrid</MenuItem>
                    <MenuItem value="diversity">Diversity</MenuItem>
                    <MenuItem value="score">Score</MenuItem>
                  </Select>
                </FormControl>
                
                <FormControl fullWidth>
                  <InputLabel>Context Optimization</InputLabel>
                  <Select
                    value={options.contextOptimizationLevel}
                    onChange={(e) => handleOptionChange('contextOptimizationLevel', e.target.value)}
                  >
                    <MenuItem value="high">High</MenuItem>
                    <MenuItem value="medium">Medium</MenuItem>
                    <MenuItem value="low">Low</MenuItem>
                  </Select>
                </FormControl>
                
                <TextField
                  label="Expansion Level"
                  type="number"
                  value={options.expansionLevel}
                  onChange={(e) => handleOptionChange('expansionLevel', parseInt(e.target.value))}
                  fullWidth
                  inputProps={{ min: 1, max: 5 }}
                />
              </Box>
            </Box>
          )}
          
          <Button
            variant="contained"
            startIcon={<SearchIcon />}
            onClick={handleSearch}
            disabled={loading}
            fullWidth
            size="large"
          >
            {loading ? 'Searching...' : 'Search'}
          </Button>
        </Box>
      </CardContent>
    </Card>
  );

  const renderResults = () => {
    if (!response) {
      return (
        <Card>
          <CardContent>
            <Typography variant="h6" color="textSecondary" align="center">
              No search results yet. Perform a search to see results here.
            </Typography>
          </CardContent>
        </Card>
      );
    }

    if (!response.success) {
      return (
        <Alert severity="error">
          {response.errorMessage || 'Search failed'}
        </Alert>
      );
    }

    return (
      <Box>
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
              <Typography variant="h6">Search Results</Typography>
              <Box display="flex" gap={1}>
                <Tooltip title="Copy Response">
                  <IconButton onClick={() => copyToClipboard(response.response)}>
                    <ContentCopyIcon />
                  </IconButton>
                </Tooltip>
                <Tooltip title="Save Search">
                  <IconButton onClick={() => saveSearch(response.query)}>
                    <BookmarkIcon />
                  </IconButton>
                </Tooltip>
                <Tooltip title="Share">
                  <IconButton>
                    <ShareIcon />
                  </IconButton>
                </Tooltip>
                <Tooltip title="Export">
                  <IconButton>
                    <DownloadIcon />
                  </IconButton>
                </Tooltip>
              </Box>
            </Box>
            
            <Box mb={2}>
              <Typography variant="body1" paragraph>
                {response.highlightedResponse || response.response}
              </Typography>
            </Box>
            
            <Box display="flex" flexWrap="wrap" gap={2}>
              <Typography variant="body2" color="textSecondary">
                Processing Time: {response.processingTimeMs}ms
              </Typography>
              <Typography variant="body2" color="textSecondary">
                Confidence Score: {(response.confidenceScore * 100).toFixed(1)}%
              </Typography>
              <Typography variant="body2" color="textSecondary">
                Documents Used: {response.usedDocuments}/{response.totalDocuments}
              </Typography>
              <Typography variant="body2" color="textSecondary">
                Strategy: {response.rerankingStrategy}
              </Typography>
            </Box>
          </CardContent>
        </Card>

        <Box display="flex" flexDirection="column" gap={3}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Query Expansion
              </Typography>
              <List dense>
                {response.expandedQueries.map((expandedQuery, index) => (
                  <ListItem key={index}>
                    <ListItemIcon>
                      <SearchIcon />
                    </ListItemIcon>
                    <ListItemText 
                      primary={expandedQuery}
                      secondary={index === 0 ? 'Original Query' : 'Expanded Query'}
                    />
                  </ListItem>
                ))}
              </List>
            </CardContent>
          </Card>
          
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Query Suggestions
              </Typography>
              <Box display="flex" flexWrap="wrap" gap={1}>
                {response.querySuggestions.map((suggestion, index) => (
                  <Chip
                    key={index}
                    label={suggestion}
                    onClick={() => setQuery(suggestion)}
                    clickable
                    variant="outlined"
                  />
                ))}
              </Box>
            </CardContent>
          </Card>
        </Box>

        <Card sx={{ mt: 3 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Retrieved Documents ({response.documents.length})
            </Typography>
            {response.documents.map((doc) => (
              <Accordion key={doc.id}>
                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                  <Box display="flex" alignItems="center" width="100%">
                    <Typography variant="subtitle1" sx={{ flexGrow: 1 }}>
                      {doc.title}
                    </Typography>
                    <Box display="flex" gap={1} mr={2}>
                      <Chip
                        label={`Score: ${doc.score.toFixed(2)}`}
                        size="small"
                        color="primary"
                      />
                      <Chip
                        label={`Similarity: ${(doc.similarity * 100).toFixed(1)}%`}
                        size="small"
                        color="secondary"
                      />
                    </Box>
                  </Box>
                </AccordionSummary>
                <AccordionDetails>
                  <Typography variant="body2" paragraph>
                    {doc.content}
                  </Typography>
                  <Divider sx={{ my: 1 }} />
                  <Box display="flex" flexWrap="wrap" gap={2}>
                    <Typography variant="caption" color="textSecondary">
                      <strong>Type:</strong> {doc.type}
                    </Typography>
                    <Typography variant="caption" color="textSecondary">
                      <strong>Source:</strong> {doc.source}
                    </Typography>
                    <Typography variant="caption" color="textSecondary">
                      <strong>Author:</strong> {doc.author}
                    </Typography>
                    <Typography variant="caption" color="textSecondary">
                      <strong>Word Count:</strong> {doc.wordCount}
                    </Typography>
                  </Box>
                </AccordionDetails>
              </Accordion>
            ))}
          </CardContent>
        </Card>
      </Box>
    );
  };

  const renderHistory = () => (
    <Box display="flex" flexDirection="column" gap={3}>
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Search History
          </Typography>
          <List dense>
            {searchHistory.map((search, index) => (
              <ListItem
                key={index}
                onClick={() => setQuery(search)}
                sx={{
                  border: '1px solid',
                  borderColor: 'divider',
                  borderRadius: 1,
                  mb: 1,
                  cursor: 'pointer',
                  '&:hover': {
                    backgroundColor: 'action.hover',
                  },
                }}
              >
                <ListItemIcon>
                  <SearchIcon />
                </ListItemIcon>
                <ListItemText primary={search} />
              </ListItem>
            ))}
          </List>
        </CardContent>
      </Card>
      
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Saved Searches
          </Typography>
          <List dense>
            {savedSearches.map((search, index) => (
              <ListItem
                key={index}
                onClick={() => setQuery(search)}
                sx={{
                  border: '1px solid',
                  borderColor: 'divider',
                  borderRadius: 1,
                  mb: 1,
                  cursor: 'pointer',
                  '&:hover': {
                    backgroundColor: 'action.hover',
                  },
                }}
              >
                <ListItemIcon>
                  <BookmarkIcon />
                </ListItemIcon>
                <ListItemText primary={search} />
              </ListItem>
            ))}
          </List>
        </CardContent>
      </Card>
    </Box>
  );

  if (loading && !response) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Advanced Search
      </Typography>
      
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs value={activeTab} onChange={handleTabChange}>
          <Tab label="Search" />
          <Tab label="Results" />
          <Tab label="History" />
        </Tabs>
      </Box>

      {activeTab === 0 && renderSearchForm()}
      {activeTab === 1 && renderResults()}
      {activeTab === 2 && renderHistory()}
    </Box>
  );
};

export default AdvancedSearchSimple;