package com.ai.infrastructure.rag;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.VectorRecord;
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
    public String storeVector(String entityType, String entityId, String content, 
                             List<Double> embedding, Map<String, Object> metadata) {
        try {
            log.debug("Storing vector in Lucene for entity {} of type {}", entityId, entityType);
            
            // Generate unique vector ID
            String vectorId = UUID.randomUUID().toString();
            
            Document doc = new Document();
            
            // Add basic fields
            doc.add(new StringField("vectorId", vectorId, Field.Store.YES));
            doc.add(new StringField("entityId", entityId, Field.Store.YES));
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
            long currentTime = System.currentTimeMillis();
            doc.add(new StringField("storedAt", String.valueOf(currentTime), Field.Store.YES));
            doc.add(new StringField("createdAt", String.valueOf(currentTime), Field.Store.YES));
            doc.add(new StringField("updatedAt", String.valueOf(currentTime), Field.Store.YES));
            
            // Index the document
            indexWriter.addDocument(doc);
            indexWriter.commit();
            
            // Refresh reader for immediate searchability
            refreshReader();
            
            log.debug("Successfully stored vector in Lucene for entity {} of type {} with vectorId {}", 
                     entityId, entityType, vectorId);
            
            return vectorId;
            
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
                    result.put("id", doc.get("entityId"));
                    result.put("vectorId", doc.get("vectorId"));
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
                .processingTimeMs(Long.valueOf(processingTime))
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
    public boolean removeVector(String entityType, String entityId) {
        try {
            log.debug("Removing vector from Lucene for entity {} of type {}", entityId, entityType);
            
            // Delete by entityId and entityType
            Term term = new Term("entityId", entityId);
            long deletedCount = indexWriter.deleteDocuments(term);
            indexWriter.commit();
            
            // Refresh reader
            refreshReader();
            
            boolean removed = deletedCount > 0;
            log.debug("Successfully removed vector from Lucene for entity {} of type {}: {}", 
                     entityId, entityType, removed);
            
            return removed;
            
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
    public long clearVectors() {
        try {
            log.debug("Clearing all vectors from Lucene");
            
            long countBefore = indexReader != null ? indexReader.numDocs() : 0;
            indexWriter.deleteAll();
            indexWriter.commit();
            refreshReader();
            
            log.debug("Successfully cleared {} vectors from Lucene", countBefore);
            return countBefore;
            
        } catch (Exception e) {
            log.error("Error clearing vectors from Lucene", e);
            throw new AIServiceException("Failed to clear vectors from Lucene", e);
        }
    }
    
    @Override
    public boolean updateVector(String vectorId, String entityType, String entityId, 
                               String content, List<Double> embedding, Map<String, Object> metadata) {
        try {
            log.debug("Updating vector {} in Lucene for entity {} of type {}", vectorId, entityId, entityType);
            
            // First remove the existing vector
            Term term = new Term("vectorId", vectorId);
            long deletedCount = indexWriter.deleteDocuments(term);
            
            if (deletedCount > 0) {
                // Store the updated vector
                storeVector(entityType, entityId, content, embedding, metadata);
                log.debug("Successfully updated vector {} in Lucene", vectorId);
                return true;
            } else {
                log.warn("Vector {} not found for update", vectorId);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error updating vector in Lucene", e);
            throw new AIServiceException("Failed to update vector in Lucene", e);
        }
    }
    
    @Override
    public Optional<VectorRecord> getVector(String vectorId) {
        try {
            log.debug("Getting vector {} from Lucene", vectorId);
            
            Term term = new Term("vectorId", vectorId);
            Query query = new QueryParser("vectorId", analyzer).parse("vectorId:" + vectorId);
            
            TopDocs topDocs = indexSearcher.search(query, 1);
            if (topDocs.totalHits.value > 0) {
                Document doc = indexSearcher.doc(topDocs.scoreDocs[0].doc);
                return Optional.of(convertDocumentToVectorRecord(doc));
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Error getting vector from Lucene", e);
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<VectorRecord> getVectorByEntity(String entityType, String entityId) {
        try {
            log.debug("Getting vector from Lucene for entity {} of type {}", entityId, entityType);
            
            String queryString = "entityType:" + entityType + " AND entityId:" + entityId;
            Query query = new QueryParser("entityType", analyzer).parse(queryString);
            
            TopDocs topDocs = indexSearcher.search(query, 1);
            if (topDocs.totalHits.value > 0) {
                Document doc = indexSearcher.doc(topDocs.scoreDocs[0].doc);
                return Optional.of(convertDocumentToVectorRecord(doc));
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Error getting vector by entity from Lucene", e);
            return Optional.empty();
        }
    }
    
    @Override
    public AISearchResponse searchByEntityType(List<Double> queryVector, String entityType, 
                                              int limit, double threshold) {
        AISearchRequest request = AISearchRequest.builder()
            .query("")
            .entityType(entityType)
            .limit(limit)
            .threshold(threshold)
            .build();
        
        return search(queryVector, request);
    }
    
    @Override
    public boolean removeVectorById(String vectorId) {
        try {
            log.debug("Removing vector {} from Lucene", vectorId);
            
            Term term = new Term("vectorId", vectorId);
            long deletedCount = indexWriter.deleteDocuments(term);
            indexWriter.commit();
            
            // Refresh reader
            refreshReader();
            
            boolean removed = deletedCount > 0;
            log.debug("Successfully removed vector {} from Lucene: {}", vectorId, removed);
            
            return removed;
            
        } catch (Exception e) {
            log.error("Error removing vector by ID from Lucene", e);
            throw new AIServiceException("Failed to remove vector by ID from Lucene", e);
        }
    }
    
    @Override
    public List<String> batchStoreVectors(List<VectorRecord> vectors) {
        try {
            log.debug("Batch storing {} vectors in Lucene", vectors.size());
            
            List<String> vectorIds = new ArrayList<>();
            
            for (VectorRecord vector : vectors) {
                String vectorId = storeVector(
                    vector.getEntityType(),
                    vector.getEntityId(),
                    vector.getContent(),
                    vector.getEmbedding(),
                    vector.getMetadata()
                );
                vectorIds.add(vectorId);
            }
            
            log.debug("Successfully batch stored {} vectors in Lucene", vectorIds.size());
            return vectorIds;
            
        } catch (Exception e) {
            log.error("Error batch storing vectors in Lucene", e);
            throw new AIServiceException("Failed to batch store vectors in Lucene", e);
        }
    }
    
    @Override
    public int batchUpdateVectors(List<VectorRecord> vectors) {
        try {
            log.debug("Batch updating {} vectors in Lucene", vectors.size());
            
            int updatedCount = 0;
            
            for (VectorRecord vector : vectors) {
                if (updateVector(
                    vector.getVectorId(),
                    vector.getEntityType(),
                    vector.getEntityId(),
                    vector.getContent(),
                    vector.getEmbedding(),
                    vector.getMetadata()
                )) {
                    updatedCount++;
                }
            }
            
            log.debug("Successfully batch updated {} vectors in Lucene", updatedCount);
            return updatedCount;
            
        } catch (Exception e) {
            log.error("Error batch updating vectors in Lucene", e);
            throw new AIServiceException("Failed to batch update vectors in Lucene", e);
        }
    }
    
    @Override
    public int batchRemoveVectors(List<String> vectorIds) {
        try {
            log.debug("Batch removing {} vectors from Lucene", vectorIds.size());
            
            int removedCount = 0;
            
            for (String vectorId : vectorIds) {
                if (removeVectorById(vectorId)) {
                    removedCount++;
                }
            }
            
            log.debug("Successfully batch removed {} vectors from Lucene", removedCount);
            return removedCount;
            
        } catch (Exception e) {
            log.error("Error batch removing vectors from Lucene", e);
            throw new AIServiceException("Failed to batch remove vectors from Lucene", e);
        }
    }
    
    @Override
    public List<VectorRecord> getVectorsByEntityType(String entityType) {
        try {
            log.debug("Getting all vectors for entity type {} from Lucene", entityType);
            
            String queryString = "entityType:" + entityType;
            Query query = new QueryParser("entityType", analyzer).parse(queryString);
            
            TopDocs topDocs = indexSearcher.search(query, Integer.MAX_VALUE);
            List<VectorRecord> vectors = new ArrayList<>();
            
            for (ScoreDoc hit : topDocs.scoreDocs) {
                Document doc = indexSearcher.doc(hit.doc);
                vectors.add(convertDocumentToVectorRecord(doc));
            }
            
            log.debug("Found {} vectors for entity type {} in Lucene", vectors.size(), entityType);
            return vectors;
            
        } catch (Exception e) {
            log.error("Error getting vectors by entity type from Lucene", e);
            throw new AIServiceException("Failed to get vectors by entity type from Lucene", e);
        }
    }
    
    @Override
    public long getVectorCountByEntityType(String entityType) {
        try {
            String queryString = "entityType:" + entityType;
            Query query = new QueryParser("entityType", analyzer).parse(queryString);
            
            TopDocs topDocs = indexSearcher.search(query, 0); // Only count, don't retrieve
            return topDocs.totalHits.value;
            
        } catch (Exception e) {
            log.error("Error getting vector count by entity type from Lucene", e);
            return 0;
        }
    }
    
    @Override
    public boolean vectorExists(String entityType, String entityId) {
        try {
            String queryString = "entityType:" + entityType + " AND entityId:" + entityId;
            Query query = new QueryParser("entityType", analyzer).parse(queryString);
            
            TopDocs topDocs = indexSearcher.search(query, 1);
            return topDocs.totalHits.value > 0;
            
        } catch (Exception e) {
            log.error("Error checking if vector exists in Lucene", e);
            return false;
        }
    }
    
    @Override
    public long clearVectorsByEntityType(String entityType) {
        try {
            log.debug("Clearing all vectors for entity type {} from Lucene", entityType);
            
            String queryString = "entityType:" + entityType;
            Query query = new QueryParser("entityType", analyzer).parse(queryString);
            
            long countBefore = indexSearcher.count(query);
            indexWriter.deleteDocuments(query);
            indexWriter.commit();
            refreshReader();
            
            log.debug("Successfully cleared {} vectors for entity type {} from Lucene", countBefore, entityType);
            return countBefore;
            
        } catch (Exception e) {
            log.error("Error clearing vectors by entity type from Lucene", e);
            throw new AIServiceException("Failed to clear vectors by entity type from Lucene", e);
        }
    }
    
    /**
     * Convert Lucene document to VectorRecord
     */
    private VectorRecord convertDocumentToVectorRecord(Document doc) {
        try {
            String embeddingText = doc.get("embedding");
            List<Double> embedding = null;
            if (embeddingText != null && !embeddingText.trim().isEmpty()) {
                embedding = Arrays.stream(embeddingText.split(","))
                    .map(String::trim)
                    .map(Double::parseDouble)
                    .collect(Collectors.toList());
            }
            
            Map<String, Object> metadata = new HashMap<>();
            String metadataJson = doc.get("metadata");
            if (metadataJson != null && !metadataJson.trim().isEmpty()) {
                // Simple JSON parsing - in production, use Jackson or Gson
                // This is a simplified implementation
                metadata.put("raw", metadataJson);
            }
            
            return VectorRecord.builder()
                .vectorId(doc.get("vectorId"))
                .entityType(doc.get("entityType"))
                .entityId(doc.get("entityId"))
                .content(doc.get("content"))
                .embedding(embedding)
                .metadata(metadata)
                .createdAt(parseTimestamp(doc.get("createdAt")))
                .updatedAt(parseTimestamp(doc.get("updatedAt")))
                .active(true)
                .version(1)
                .build();
                
        } catch (Exception e) {
            log.error("Error converting document to VectorRecord", e);
            return null;
        }
    }
    
    /**
     * Parse timestamp from string
     */
    private java.time.LocalDateTime parseTimestamp(String timestampStr) {
        try {
            if (timestampStr != null) {
                long timestamp = Long.parseLong(timestampStr);
                return java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(timestamp),
                    java.time.ZoneId.systemDefault()
                );
            }
        } catch (Exception e) {
            log.warn("Error parsing timestamp: {}", timestampStr, e);
        }
        return java.time.LocalDateTime.now();
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
            
            // Check if index exists before trying to open it
            if (DirectoryReader.indexExists(directory)) {
                indexReader = DirectoryReader.open(directory);
            } else {
                // Index doesn't exist yet - commit writer to create empty index, then open reader
                if (indexWriter != null) {
                    indexWriter.commit();
                    indexReader = DirectoryReader.open(directory);
                } else {
                    // Create empty reader - open will fail for empty index, so we commit first
                    indexReader = DirectoryReader.open(directory);
                }
            }
            indexSearcher = new IndexSearcher(indexReader);
            
        } catch (Exception e) {
            log.error("Error refreshing Lucene reader", e);
            throw new AIServiceException("Failed to refresh Lucene reader", e);
        }
    }
}