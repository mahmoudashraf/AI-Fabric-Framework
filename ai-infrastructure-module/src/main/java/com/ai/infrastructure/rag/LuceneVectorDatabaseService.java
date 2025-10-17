package com.ai.infrastructure.rag;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.exception.AIServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lucene Vector Database Service
 * 
 * This service provides vector database operations using Apache Lucene with k-NN search.
 * It's designed for development and testing environments where external vector databases
 * are not available or needed.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "lucene", matchIfMissing = true)
public class LuceneVectorDatabaseService implements VectorDatabaseService {
    
    private final AIProviderConfig config;
    
    @Value("${ai.vector-db.lucene.index-path:./data/lucene-vector-index}")
    private String indexPath;
    
    @Value("${ai.vector-db.lucene.similarity-threshold:0.7}")
    private double similarityThreshold;
    
    @Value("${ai.vector-db.lucene.max-results:100}")
    private int maxResults;
    
    private Directory directory;
    private IndexWriter indexWriter;
    private IndexReader indexReader;
    private IndexSearcher indexSearcher;
    private StandardAnalyzer analyzer;
    
    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing Lucene Vector Database at: {}", indexPath);
            
            // Create index directory if it doesn't exist
            Path indexDir = Paths.get(indexPath);
            if (!Files.exists(indexDir)) {
                Files.createDirectories(indexDir);
            }
            
            // Initialize Lucene components
            directory = FSDirectory.open(indexDir);
            analyzer = new StandardAnalyzer();
            
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            indexWriter = new IndexWriter(directory, config);
            
            // Initialize reader and searcher
            refreshReader();
            
            log.info("Lucene Vector Database initialized successfully");
            
        } catch (Exception e) {
            log.error("Failed to initialize Lucene Vector Database", e);
            throw new AIServiceException("Failed to initialize Lucene Vector Database", e);
        }
    }
    
    @PreDestroy
    public void cleanup() {
        try {
            log.debug("Closing Lucene Vector Database");
            
            if (indexWriter != null) {
                indexWriter.close();
            }
            if (indexReader != null) {
                indexReader.close();
            }
            if (directory != null) {
                directory.close();
            }
            if (analyzer != null) {
                analyzer.close();
            }
            
            log.debug("Lucene Vector Database closed successfully");
            
        } catch (Exception e) {
            log.error("Error closing Lucene Vector Database", e);
        }
    }
    
    @Override
    public void storeVector(String entityType, String entityId, String content, 
                           List<Double> embedding, Map<String, Object> metadata) {
        try {
            log.debug("Storing vector in Lucene for entity {} of type {}", entityId, entityType);
            
            Document doc = new Document();
            
            // Add basic fields
            doc.add(new StringField("id", entityId, Field.Store.YES));
            doc.add(new StringField("entityType", entityType, Field.Store.YES));
            doc.add(new TextField("content", content, Field.Store.YES));
            
            // Add embedding as a searchable text field (comma-separated values)
            String embeddingText = embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
            doc.add(new TextField("embedding", embeddingText, Field.Store.YES));
            
            // Add metadata as JSON string
            if (metadata != null && !metadata.isEmpty()) {
                String metadataJson = metadata.entrySet().stream()
                    .map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"")
                    .collect(Collectors.joining(",", "{", "}"));
                doc.add(new TextField("metadata", metadataJson, Field.Store.YES));
            }
            
            // Add timestamp
            doc.add(new StringField("storedAt", String.valueOf(System.currentTimeMillis()), Field.Store.YES));
            
            // Index the document
            indexWriter.addDocument(doc);
            indexWriter.commit();
            
            // Refresh reader for immediate searchability
            refreshReader();
            
            log.debug("Successfully stored vector in Lucene for entity {} of type {}", entityId, entityType);
            
        } catch (Exception e) {
            log.error("Error storing vector in Lucene", e);
            throw new AIServiceException("Failed to store vector in Lucene", e);
        }
    }
    
    @Override
    public AISearchResponse search(List<Double> queryVector, AISearchRequest request) {
        try {
            log.debug("Searching vectors in Lucene for query: {}", request.getQuery());
            
            long startTime = System.currentTimeMillis();
            
            // Build search query
            String searchQuery = buildSearchQuery(request, queryVector);
            Query query = new QueryParser("content", analyzer).parse(searchQuery);
            
            // Perform search
            TopDocs topDocs = indexSearcher.search(query, Math.min(request.getLimit(), maxResults));
            ScoreDoc[] hits = topDocs.scoreDocs;
            
            // Process results
            List<Map<String, Object>> results = new ArrayList<>();
            for (ScoreDoc hit : hits) {
                Document doc = indexSearcher.doc(hit.doc);
                
                // Calculate similarity score
                double similarity = calculateSimilarity(queryVector, doc);
                
                if (similarity >= request.getThreshold()) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", doc.get("id"));
                    result.put("content", doc.get("content"));
                    result.put("entityType", doc.get("entityType"));
                    result.put("metadata", doc.get("metadata"));
                    result.put("score", similarity);
                    result.put("similarity", similarity);
                    result.put("luceneScore", hit.score);
                    
                    results.add(result);
                }
            }
            
            // Sort by similarity score
            results.sort((a, b) -> Double.compare((Double) b.get("similarity"), (Double) a.get("similarity")));
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            log.debug("Found {} results in Lucene in {}ms", results.size(), processingTime);
            
            return AISearchResponse.builder()
                .results(results)
                .totalResults(results.size())
                .maxScore(results.isEmpty() ? 0.0 : (Double) results.get(0).get("similarity"))
                .processingTimeMs(processingTime)
                .requestId(UUID.randomUUID().toString())
                .query(request.getQuery())
                .model(config.getOpenaiEmbeddingModel())
                .build();
                
        } catch (Exception e) {
            log.error("Error searching vectors in Lucene", e);
            throw new AIServiceException("Failed to search vectors in Lucene", e);
        }
    }
    
    @Override
    public void removeVector(String entityType, String entityId) {
        try {
            log.debug("Removing vector from Lucene for entity {} of type {}", entityId, entityType);
            
            // Delete by ID
            Term term = new Term("id", entityId);
            indexWriter.deleteDocuments(term);
            indexWriter.commit();
            
            // Refresh reader
            refreshReader();
            
            log.debug("Successfully removed vector from Lucene for entity {} of type {}", entityId, entityType);
            
        } catch (Exception e) {
            log.error("Error removing vector from Lucene", e);
            throw new AIServiceException("Failed to remove vector from Lucene", e);
        }
    }
    
    @Override
    public Map<String, Object> getStatistics() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            if (indexReader != null) {
                stats.put("totalVectors", indexReader.numDocs());
                stats.put("indexPath", indexPath);
                stats.put("similarityThreshold", similarityThreshold);
                stats.put("maxResults", maxResults);
                
                // Get entity type counts
                Map<String, Integer> entityTypeCounts = new HashMap<>();
                for (int i = 0; i < indexReader.numDocs(); i++) {
                    Document doc = indexReader.document(i);
                    String entityType = doc.get("entityType");
                    entityTypeCounts.merge(entityType, 1, Integer::sum);
                }
                stats.put("entityTypeCounts", entityTypeCounts);
                stats.put("entityTypes", entityTypeCounts.keySet());
            }
            
            return stats;
            
        } catch (Exception e) {
            log.error("Error getting Lucene statistics", e);
            return Map.of("error", "Failed to get statistics");
        }
    }
    
    @Override
    public void clearVectors() {
        try {
            log.debug("Clearing all vectors from Lucene");
            
            indexWriter.deleteAll();
            indexWriter.commit();
            refreshReader();
            
            log.debug("Successfully cleared all vectors from Lucene");
            
        } catch (Exception e) {
            log.error("Error clearing vectors from Lucene", e);
            throw new AIServiceException("Failed to clear vectors from Lucene", e);
        }
    }
    
    /**
     * Build search query for Lucene
     */
    private String buildSearchQuery(AISearchRequest request, List<Double> queryVector) {
        StringBuilder queryBuilder = new StringBuilder();
        
        // Add text search
        if (request.getQuery() != null && !request.getQuery().trim().isEmpty()) {
            queryBuilder.append("content:").append(request.getQuery());
        }
        
        // Add entity type filter
        if (request.getEntityType() != null && !request.getEntityType().trim().isEmpty()) {
            if (queryBuilder.length() > 0) {
                queryBuilder.append(" AND ");
            }
            queryBuilder.append("entityType:").append(request.getEntityType());
        }
        
        // If no text query, search for all documents
        if (queryBuilder.length() == 0) {
            queryBuilder.append("*:*");
        }
        
        return queryBuilder.toString();
    }
    
    /**
     * Calculate similarity between query vector and document
     */
    private double calculateSimilarity(List<Double> queryVector, Document doc) {
        try {
            String embeddingText = doc.get("embedding");
            if (embeddingText == null || embeddingText.trim().isEmpty()) {
                return 0.0;
            }
            
            // Parse embedding from stored text
            List<Double> docVector = Arrays.stream(embeddingText.split(","))
                .map(String::trim)
                .map(Double::parseDouble)
                .collect(Collectors.toList());
            
            return calculateCosineSimilarity(queryVector, docVector);
            
        } catch (Exception e) {
            log.warn("Error calculating similarity for document: {}", doc.get("id"), e);
            return 0.0;
        }
    }
    
    /**
     * Calculate cosine similarity between two vectors
     */
    private double calculateCosineSimilarity(List<Double> vectorA, List<Double> vectorB) {
        if (vectorA == null || vectorB == null || vectorA.size() != vectorB.size()) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < vectorA.size(); i++) {
            double a = vectorA.get(i);
            double b = vectorB.get(i);
            dotProduct += a * b;
            normA += a * a;
            normB += b * b;
        }
        
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    /**
     * Refresh the index reader and searcher
     */
    private void refreshReader() {
        try {
            if (indexReader != null) {
                indexReader.close();
            }
            
            indexReader = DirectoryReader.open(directory);
            indexSearcher = new IndexSearcher(indexReader);
            
        } catch (Exception e) {
            log.error("Error refreshing Lucene reader", e);
            throw new AIServiceException("Failed to refresh Lucene reader", e);
        }
    }
}