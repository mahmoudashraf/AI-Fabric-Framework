import { useState, useEffect, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { 
  SearchRequest, 
  SearchResponse, 
  Document, 
  QueryExpansionRequest,
  QueryExpansionResponse,
  RAGConfiguration,
  RAGMetrics
} from '../types/ai';

interface UseAdvancedRAGOptions {
  refreshInterval?: number;
  autoRefresh?: boolean;
  onSearchCompleted?: (response: SearchResponse) => void;
  onDocumentRetrieved?: (document: Document) => void;
}

interface UseAdvancedRAGReturn {
  // Data
  searchHistory: SearchRequest[];
  documents: Document[];
  configuration: RAGConfiguration | null;
  metrics: RAGMetrics | null;
  
  // Loading states
  isLoading: boolean;
  isSearching: boolean;
  isDocumentsLoading: boolean;
  isConfigurationLoading: boolean;
  isMetricsLoading: boolean;
  
  // Error states
  error: string | null;
  searchError: string | null;
  documentsError: string | null;
  configurationError: string | null;
  metricsError: string | null;
  
  // Actions
  search: (request: SearchRequest) => Promise<SearchResponse>;
  expandQuery: (request: QueryExpansionRequest) => Promise<QueryExpansionResponse>;
  retrieveDocument: (documentId: string) => Promise<Document>;
  updateConfiguration: (config: Partial<RAGConfiguration>) => Promise<RAGConfiguration>;
  clearSearchHistory: () => void;
  saveSearch: (request: SearchRequest, response: SearchResponse) => void;
  
  // Utilities
  refresh: () => void;
  refreshDocuments: () => void;
  refreshConfiguration: () => void;
  refreshMetrics: () => void;
  
  // Computed values
  recentSearches: SearchRequest[];
  savedSearches: SearchRequest[];
  documentCount: number;
  averageResponseTime: number;
  searchAccuracy: number;
  queryExpansionRate: number;
  documentRetrievalRate: number;
  searchTrend: 'increasing' | 'decreasing' | 'stable';
}

const useAdvancedRAG = (options: UseAdvancedRAGOptions = {}): UseAdvancedRAGReturn => {
  const {
    refreshInterval = 30000,
    autoRefresh = true,
    onSearchCompleted,
    onDocumentRetrieved,
  } = options;

  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [searchHistory, setSearchHistory] = useState<SearchRequest[]>([]);
  const [savedSearches, setSavedSearches] = useState<SearchRequest[]>([]);

  // Query keys
  const queryKeys = {
    documents: ['ai-rag', 'documents'],
    configuration: ['ai-rag', 'configuration'],
    metrics: ['ai-rag', 'metrics'],
  };

  // Fetch documents
  const {
    data: documents = [],
    isLoading: isDocumentsLoading,
    error: documentsError,
    refetch: refetchDocuments,
  } = useQuery({
    queryKey: queryKeys.documents,
    queryFn: async (): Promise<Document[]> => {
      const response = await fetch('/api/ai/rag/documents');
      if (!response.ok) {
        throw new Error('Failed to fetch documents');
      }
      return response.json();
    },
    refetchInterval: autoRefresh ? refreshInterval : false,
    refetchIntervalInBackground: false,
  });

  // Fetch configuration
  const {
    data: configuration,
    isLoading: isConfigurationLoading,
    error: configurationError,
    refetch: refetchConfiguration,
  } = useQuery({
    queryKey: queryKeys.configuration,
    queryFn: async (): Promise<RAGConfiguration> => {
      const response = await fetch('/api/ai/rag/configuration');
      if (!response.ok) {
        throw new Error('Failed to fetch RAG configuration');
      }
      return response.json();
    },
  });

  // Fetch metrics
  const {
    data: metrics,
    isLoading: isMetricsLoading,
    error: metricsError,
    refetch: refetchMetrics,
  } = useQuery({
    queryKey: queryKeys.metrics,
    queryFn: async (): Promise<RAGMetrics> => {
      const response = await fetch('/api/ai/rag/metrics');
      if (!response.ok) {
        throw new Error('Failed to fetch RAG metrics');
      }
      return response.json();
    },
    refetchInterval: autoRefresh ? refreshInterval : false,
    refetchIntervalInBackground: false,
  });

  // Search mutation
  const searchMutation = useMutation({
    mutationFn: async (request: SearchRequest): Promise<SearchResponse> => {
      const response = await fetch('/api/ai/rag/search', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
      });
      
      if (!response.ok) {
        throw new Error('Failed to perform search');
      }
      
      const result = await response.json();
      
      // Add to search history
      setSearchHistory(prev => [request, ...prev.slice(0, 49)]); // Keep last 50 searches
      
      // Trigger callback
      onSearchCompleted?.(result);
      
      // Trigger document callbacks
      if (result.documents && result.documents.length > 0) {
        result.documents.forEach((doc: Document) => {
          onDocumentRetrieved?.(doc);
        });
      }
      
      return result;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.metrics });
    },
  });

  // Query expansion mutation
  const expandQueryMutation = useMutation({
    mutationFn: async (request: QueryExpansionRequest): Promise<QueryExpansionResponse> => {
      const response = await fetch('/api/ai/rag/expand-query', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
      });
      
      if (!response.ok) {
        throw new Error('Failed to expand query');
      }
      
      return response.json();
    },
  });

  // Retrieve document mutation
  const retrieveDocumentMutation = useMutation({
    mutationFn: async (documentId: string): Promise<Document> => {
      const response = await fetch(`/api/ai/rag/documents/${documentId}`);
      
      if (!response.ok) {
        throw new Error('Failed to retrieve document');
      }
      
      const result = await response.json();
      onDocumentRetrieved?.(result);
      return result;
    },
  });

  // Update configuration mutation
  const updateConfigurationMutation = useMutation({
    mutationFn: async (config: Partial<RAGConfiguration>): Promise<RAGConfiguration> => {
      const response = await fetch('/api/ai/rag/configuration', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(config),
      });
      
      if (!response.ok) {
        throw new Error('Failed to update RAG configuration');
      }
      
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.configuration });
    },
  });

  // Action functions
  const search = useCallback(
    (request: SearchRequest) => searchMutation.mutateAsync(request),
    [searchMutation]
  );

  const expandQuery = useCallback(
    (request: QueryExpansionRequest) => expandQueryMutation.mutateAsync(request),
    [expandQueryMutation]
  );

  const retrieveDocument = useCallback(
    (documentId: string) => retrieveDocumentMutation.mutateAsync(documentId),
    [retrieveDocumentMutation]
  );

  const updateConfiguration = useCallback(
    (config: Partial<RAGConfiguration>) => 
      updateConfigurationMutation.mutateAsync(config),
    [updateConfigurationMutation]
  );

  const clearSearchHistory = useCallback(() => {
    setSearchHistory([]);
  }, []);

  const saveSearch = useCallback((request: SearchRequest, response: SearchResponse) => {
    setSavedSearches(prev => {
      const exists = prev.some(saved => 
        saved.query === request.query && 
        saved.filters === request.filters
      );
      
      if (!exists) {
        return [request, ...prev.slice(0, 19)]; // Keep last 20 saved searches
      }
      
      return prev;
    });
  }, []);

  // Refresh functions
  const refresh = useCallback(() => {
    refetchDocuments();
    refetchConfiguration();
    refetchMetrics();
  }, [refetchDocuments, refetchConfiguration, refetchMetrics]);

  const refreshDocuments = useCallback(() => refetchDocuments(), [refetchDocuments]);
  const refreshConfiguration = useCallback(() => refetchConfiguration(), [refetchConfiguration]);
  const refreshMetrics = useCallback(() => refetchMetrics(), [refetchMetrics]);

  // Computed values
  const recentSearches = searchHistory.slice(0, 10);
  const documentCount = documents.length;
  const averageResponseTime = metrics?.averageResponseTime || 0;
  const searchAccuracy = metrics?.searchAccuracy || 0;
  const queryExpansionRate = metrics?.queryExpansionRate || 0;
  const documentRetrievalRate = metrics?.documentRetrievalRate || 0;
  const searchTrend = metrics?.searchTrend || 'stable';

  // Overall loading state
  const isLoading = isDocumentsLoading || isConfigurationLoading || isMetricsLoading;
  const isSearching = searchMutation.isPending || expandQueryMutation.isPending || retrieveDocumentMutation.isPending;

  // Overall error state
  const overallError = error || searchMutation.error || expandQueryMutation.error || retrieveDocumentMutation.error || documentsError || configurationError || metricsError;

  return {
    // Data
    searchHistory,
    documents,
    configuration,
    metrics,
    
    // Loading states
    isLoading,
    isSearching,
    isDocumentsLoading,
    isConfigurationLoading,
    isMetricsLoading,
    
    // Error states
    error: overallError,
    searchError: searchMutation.error?.message || null,
    documentsError,
    configurationError,
    metricsError,
    
    // Actions
    search,
    expandQuery,
    retrieveDocument,
    updateConfiguration,
    clearSearchHistory,
    saveSearch,
    
    // Utilities
    refresh,
    refreshDocuments,
    refreshConfiguration,
    refreshMetrics,
    
    // Computed values
    recentSearches,
    savedSearches,
    documentCount,
    averageResponseTime,
    searchAccuracy,
    queryExpansionRate,
    documentRetrievalRate,
    searchTrend,
  };
};

export default useAdvancedRAG;