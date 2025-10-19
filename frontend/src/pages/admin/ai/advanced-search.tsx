import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  TextField,
  Button,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Chip,
  Alert,
  CircularProgress,
  Tabs,
  Tab,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  Divider,
  Paper,
  LinearProgress,
} from '@mui/material';
import {
  Search as SearchIcon,
  ExpandMore as ExpandMoreIcon,
  ContentCopy as ContentCopyIcon,
  ThumbUp as ThumbUpIcon,
  ThumbDown as ThumbDownIcon,
  Refresh as RefreshIcon,
  Settings as SettingsIcon,
  Lightbulb as LightbulbIcon,
  TrendingUp as TrendingUpIcon,
} from '@mui/icons-material';
import { useAdvancedRAG } from '../../../hooks/useAdvancedRAG';

interface AdvancedRAGRequest {
  query: string;
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

interface AdvancedRAGResponse {
  query: string;
  expandedQueries: string[];
  response: string;
  context: string;
  documents: RAGDocument[];
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

interface RAGDocument {
  id: string;
  content: string;
  title: string;
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

const AdvancedSearchPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState(0);
  const [request, setRequest] = useState<AdvancedRAGRequest>({
    query: '',
    maxResults: 10,
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
  const [response, setResponse] = useState<AdvancedRAGResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { performAdvancedRAG } = useAdvancedRAG();

  const handleInputChange = (field: keyof AdvancedRAGRequest, value: any) => {
    setRequest(prev => ({ ...prev, [field]: value }));
  };

  const handleSearch = async () => {
    if (!request.query.trim()) {
      setError('Please enter a search query');
      return;
    }

    setLoading(true);
    setError(null);
    
    try {
      const result = await performAdvancedRAG(request);
      setResponse(result);
    } catch (err) {
      setError('Failed to perform advanced search');
      console.error('Error performing advanced search:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
  };

  const renderSearchForm = () => (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Advanced RAG Search
        </Typography>
        
        <Grid container spacing={3}>
          <Grid item xs={12}>
            <TextField
              label="Search Query"
              value={request.query}
              onChange={(e) => handleInputChange('query', e.target.value)}
              fullWidth
              multiline
              rows={3}
              placeholder="Enter your search query here..."
            />
          </Grid>
          
          <Grid item xs={12} sm={6}>
            <TextField
              label="Context"
              value={request.context}
              onChange={(e) => handleInputChange('context', e.target.value)}
              fullWidth
              multiline
              rows={2}
              placeholder="Additional context for the search..."
            />
          </Grid>
          
          <Grid item xs={12} sm={6}>
            <TextField
              label="Domain"
              value={request.domain}
              onChange={(e) => handleInputChange('domain', e.target.value)}
              fullWidth
              placeholder="e.g., technology, healthcare, finance"
            />
          </Grid>
          
          <Grid item xs={12} sm={4}>
            <FormControl fullWidth>
              <InputLabel>Re-ranking Strategy</InputLabel>
              <Select
                value={request.rerankingStrategy}
                onChange={(e) => handleInputChange('rerankingStrategy', e.target.value)}
              >
                <MenuItem value="semantic">Semantic</MenuItem>
                <MenuItem value="hybrid">Hybrid</MenuItem>
                <MenuItem value="diversity">Diversity</MenuItem>
                <MenuItem value="score">Score</MenuItem>
              </Select>
            </FormControl>
          </Grid>
          
          <Grid item xs={12} sm={4}>
            <FormControl fullWidth>
              <InputLabel>Context Optimization</InputLabel>
              <Select
                value={request.contextOptimizationLevel}
                onChange={(e) => handleInputChange('contextOptimizationLevel', e.target.value)}
              >
                <MenuItem value="high">High</MenuItem>
                <MenuItem value="medium">Medium</MenuItem>
                <MenuItem value="low">Low</MenuItem>
              </Select>
            </FormControl>
          </Grid>
          
          <Grid item xs={12} sm={4}>
            <TextField
              label="Expansion Level"
              type="number"
              value={request.expansionLevel}
              onChange={(e) => handleInputChange('expansionLevel', parseInt(e.target.value))}
              fullWidth
              inputProps={{ min: 1, max: 5 }}
            />
          </Grid>
          
          <Grid item xs={12} sm={4}>
            <TextField
              label="Max Results"
              type="number"
              value={request.maxResults}
              onChange={(e) => handleInputChange('maxResults', parseInt(e.target.value))}
              fullWidth
              inputProps={{ min: 1, max: 100 }}
            />
          </Grid>
          
          <Grid item xs={12} sm={4}>
            <TextField
              label="Max Documents"
              type="number"
              value={request.maxDocuments}
              onChange={(e) => handleInputChange('maxDocuments', parseInt(e.target.value))}
              fullWidth
              inputProps={{ min: 1, max: 20 }}
            />
          </Grid>
          
          <Grid item xs={12} sm={4}>
            <TextField
              label="Min Confidence Score"
              type="number"
              value={request.minConfidenceScore}
              onChange={(e) => handleInputChange('minConfidenceScore', parseFloat(e.target.value))}
              fullWidth
              inputProps={{ min: 0, max: 1, step: 0.1 }}
            />
          </Grid>
          
          <Grid item xs={12}>
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
          </Grid>
        </Grid>
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
                <Button
                  variant="outlined"
                  startIcon={<ContentCopyIcon />}
                  onClick={() => copyToClipboard(response.response)}
                  size="small"
                >
                  Copy Response
                </Button>
                <Button
                  variant="outlined"
                  startIcon={<RefreshIcon />}
                  onClick={handleSearch}
                  disabled={loading}
                  size="small"
                >
                  Refresh
                </Button>
              </Box>
            </Box>
            
            <Box mb={2}>
              <Typography variant="body1" paragraph>
                {response.highlightedResponse || response.response}
              </Typography>
            </Box>
            
            <Grid container spacing={2}>
              <Grid item xs={12} sm={6} md={3}>
                <Typography variant="body2" color="textSecondary">
                  Processing Time: {response.processingTimeMs}ms
                </Typography>
              </Grid>
              <Grid item xs={12} sm={6} md={3}>
                <Typography variant="body2" color="textSecondary">
                  Confidence Score: {(response.confidenceScore * 100).toFixed(1)}%
                </Typography>
              </Grid>
              <Grid item xs={12} sm={6} md={3}>
                <Typography variant="body2" color="textSecondary">
                  Documents Used: {response.usedDocuments}/{response.totalDocuments}
                </Typography>
              </Grid>
              <Grid item xs={12} sm={6} md={3}>
                <Typography variant="body2" color="textSecondary">
                  Strategy: {response.rerankingStrategy}
                </Typography>
              </Grid>
            </Grid>
            
            <LinearProgress
              variant="determinate"
              value={response.confidenceScore * 100}
              sx={{ mt: 2 }}
            />
          </CardContent>
        </Card>

        <Grid container spacing={3}>
          <Grid item xs={12} md={6}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Query Expansion
                </Typography>
                <List dense>
                  {response.expandedQueries.map((query, index) => (
                    <ListItem key={index}>
                      <ListItemIcon>
                        <SearchIcon />
                      </ListItemIcon>
                      <ListItemText primary={query} />
                    </ListItem>
                  ))}
                </List>
              </CardContent>
            </Card>
          </Grid>
          
          <Grid item xs={12} md={6}>
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
                      onClick={() => handleInputChange('query', suggestion)}
                      clickable
                    />
                  ))}
                </Box>
              </CardContent>
            </Card>
          </Grid>
          
          <Grid item xs={12} md={6}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Related Topics
                </Typography>
                <Box display="flex" flexWrap="wrap" gap={1}>
                  {response.relatedTopics.map((topic, index) => (
                    <Chip
                      key={index}
                      label={topic}
                      color="primary"
                      variant="outlined"
                    />
                  ))}
                </Box>
              </CardContent>
            </Card>
          </Grid>
          
          <Grid item xs={12} md={6}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Document Summaries
                </Typography>
                <List dense>
                  {response.documentSummaries.map((summary, index) => (
                    <ListItem key={index}>
                      <ListItemIcon>
                        <ContentCopyIcon />
                      </ListItemIcon>
                      <ListItemText 
                        primary={summary}
                        secondary={`Document ${index + 1}`}
                      />
                    </ListItem>
                  ))}
                </List>
              </CardContent>
            </Card>
          </Grid>
        </Grid>

        <Card sx={{ mt: 3 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Retrieved Documents
            </Typography>
            {response.documents.map((doc, index) => (
              <Accordion key={doc.id}>
                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                  <Box display="flex" alignItems="center" width="100%">
                    <Typography variant="subtitle1" sx={{ flexGrow: 1 }}>
                      {doc.title || `Document ${index + 1}`}
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
                  <Grid container spacing={2}>
                    <Grid item xs={12} sm={6}>
                      <Typography variant="caption" color="textSecondary">
                        <strong>Type:</strong> {doc.type}
                      </Typography>
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <Typography variant="caption" color="textSecondary">
                        <strong>Source:</strong> {doc.source}
                      </Typography>
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <Typography variant="caption" color="textSecondary">
                        <strong>Author:</strong> {doc.author}
                      </Typography>
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <Typography variant="caption" color="textSecondary">
                        <strong>Word Count:</strong> {doc.wordCount}
                      </Typography>
                    </Grid>
                    <Grid item xs={12}>
                      <Typography variant="caption" color="textSecondary">
                        <strong>Tags:</strong> {doc.tags.join(', ')}
                      </Typography>
                    </Grid>
                  </Grid>
                </AccordionDetails>
              </Accordion>
            ))}
          </CardContent>
        </Card>
      </Box>
    );
  };

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
        Advanced RAG Search
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
          <Tab label="Settings" />
        </Tabs>
      </Box>

      {activeTab === 0 && renderSearchForm()}
      {activeTab === 1 && renderResults()}
      {activeTab === 2 && (
        <Card>
          <CardContent>
            <Typography variant="h6">Search Settings</Typography>
            <Typography color="textSecondary">
              Advanced search configuration options will be available here.
            </Typography>
          </CardContent>
        </Card>
      )}
    </Box>
  );
};

export default AdvancedSearchPage;