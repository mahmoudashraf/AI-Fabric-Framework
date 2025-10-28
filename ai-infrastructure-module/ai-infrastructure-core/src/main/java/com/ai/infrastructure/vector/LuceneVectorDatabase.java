package com.ai.infrastructure.vector;

import com.ai.infrastructure.config.VectorDatabaseConfig;
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
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Apache Lucene Vector Database Implementation
 * 
 * File-based vector database using Apache Lucene for indexing and search.
 * Provides persistent storage with good performance for medium-scale applications.
 * 
 * Features:
 * - File-based persistence (survives restarts)
 * - Text search + vector similarity
 * - Self-hosted (no external dependencies)
 * - Good performance (100K+ vectors)
 * - Metadata filtering support
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "lucene")
public class LuceneVectorDatabase implements VectorDatabase {
    
    private final VectorDatabaseConfig config;
    
    private Directory directory;
    private IndexWriter indexWriter;
    private IndexReader indexReader;
    private IndexSearcher indexSearcher;
    private StandardAnalyzer analyzer;
    
    private final AtomicLong searchCount = new AtomicLong(0);
    private final AtomicLong totalSearchTime = new AtomicLong(0);
    
    @PostConstruct
    public void initialize() {
        try {
            String indexPath = System.getProperty("ai.lucene.index.path", "./data/lucene-vector-index");
            log.info("Initializing Lucene Vector Database at: {}", indexPath);
            
            // Create index directory if it doesn't exist
            Path indexDir = Paths.get(indexPath);
            if (!Files.exists(indexDir)) {
                Files.createDirectories(indexDir);
            }
            
            // Initialize Lucene components
            directory = FSDirectory.open(indexDir);
            analyzer = new StandardAnalyzer();
            
            IndexWriterConfig writerConfig = new IndexWriterConfig(analyzer);
            writerConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            indexWriter = new IndexWriter(directory, writerConfig);
            
            // Initialize reader and searcher
            refreshReader();
            
            log.info("Lucene Vector Database initialized successfully");
            
        } catch (Exception e) {
            log.error("Failed to initialize Lucene Vector Database", e);
            throw new RuntimeException("Failed to initialize Lucene Vector Database", e);
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
    public void store(String id, List<Double> vector, Map<String, Object> metadata) {
        try {
            log.debug("Storing vector in Lucene: {}", id);
            
            Document doc = new Document();
            
            // Add basic fields
            doc.add(new StringField("id", id, Field.Store.YES));
            doc.add(new TextField("content", getMetadataString(metadata, "content", ""), Field.Store.YES));
            
            // Store vector as comma-separated values
            String vectorText = vector.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
            doc.add(new TextField("vector", vectorText, Field.Store.YES));
            
            // Add metadata fields
            if (metadata != null) {
                for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue() != null ? entry.getValue().toString() : "";
                    doc.add(new TextField("meta_" + key, value, Field.Store.YES));
                }
                
                // Store full metadata as JSON
                String metadataJson = metadata.entrySet().stream()
                    .map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"")
                    .collect(Collectors.joining(",", "{", "}"));
                doc.add(new TextField("metadata", metadataJson, Field.Store.YES));
            }
            
            // Add timestamp
            doc.add(new StringField("timestamp", String.valueOf(System.currentTimeMillis()), Field.Store.YES));
            
            // Index the document (replace if exists)
            indexWriter.updateDocument(new Term("id", id), doc);
            indexWriter.commit();
            
            // Refresh reader for immediate searchability
            refreshReader();
            
            log.debug("Successfully stored vector in Lucene: {}", id);
            
        } catch (Exception e) {
            log.error("Error storing vector in Lucene: {}", id, e);
            throw new RuntimeException("Failed to store vector in Lucene", e);
        }
    }
    
    @Override
    public void batchStore(List<VectorRecord> vectors) {
        try {
            log.debug("Batch storing {} vectors in Lucene", vectors.size());
            
            for (VectorRecord record : vectors) {
                if (record.isValid()) {
                    store(record.getId(), record.getVector(), record.getMetadata());
                } else {
                    log.warn("Skipping invalid vector record: {}", record.getId());
                }
            }
            
            log.debug("Batch stored {} vectors in Lucene", vectors.size());
            
        } catch (Exception e) {
            log.error("Error batch storing vectors in Lucene", e);
            throw new RuntimeException("Failed to batch store vectors in Lucene", e);
        }
    }
    
    @Override
    public List<VectorSearchResult> search(List<Double> queryVector, int limit, double threshold) {
        return searchWithFilter(queryVector, null, limit, threshold);
    }
    
    @Override
    public List<VectorSearchResult> searchWithFilter(List<Double> queryVector, 
                                                   Map<String, Object> filter, 
                                                   int limit, 
                                                   double threshold) {
        try {
            long startTime = System.currentTimeMillis();
            searchCount.incrementAndGet();
            
            // Build Lucene query
            String queryString = buildLuceneQuery(filter);
            Query query = new QueryParser("content", analyzer).parse(queryString);
            
            // Perform search
            TopDocs topDocs = indexSearcher.search(query, Math.max(limit * 10, 1000)); // Get more for filtering
            ScoreDoc[] hits = topDocs.scoreDocs;
            
            // Process results and calculate vector similarity
            List<VectorSearchResult> results = new ArrayList<>();
            for (ScoreDoc hit : hits) {
                Document doc = indexSearcher.doc(hit.doc);
                
                // Parse stored vector
                List<Double> docVector = parseVector(doc.get("vector"));
                if (docVector == null) continue;
                
                // Calculate similarity
                double similarity = calculateCosineSimilarity(queryVector, docVector);
                
                if (similarity >= threshold) {
                    VectorRecord record = VectorRecord.builder()
                        .id(doc.get("id"))
                        .vector(docVector)
                        .metadata(parseMetadata(doc))
                        .build();
                    
                    VectorSearchResult result = VectorSearchResult.builder()
                        .record(record)
                        .similarity(similarity)
                        .distance(1.0 - similarity)
                        .searchMetadata(Map.of("luceneScore", hit.score))
                        .build();
                    
                    results.add(result);
                }
                
                if (results.size() >= limit) break;
            }
            
            // Sort by similarity
            results.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
            
            long processingTime = System.currentTimeMillis() - startTime;
            totalSearchTime.addAndGet(processingTime);
            
            log.debug("Lucene search completed: {} results in {}ms", results.size(), processingTime);
            
            return results;
            
        } catch (Exception e) {
            log.error("Error searching vectors in Lucene", e);
            throw new RuntimeException("Failed to search vectors in Lucene", e);
        }
    }
    
    @Override
    public Optional<VectorRecord> get(String id) {
        try {
            Query query = new QueryParser("id", analyzer).parse("id:" + id);
            TopDocs topDocs = indexSearcher.search(query, 1);
            
            if (topDocs.scoreDocs.length > 0) {
                Document doc = indexSearcher.doc(topDocs.scoreDocs[0].doc);
                
                VectorRecord record = VectorRecord.builder()
                    .id(doc.get("id"))
                    .vector(parseVector(doc.get("vector")))
                    .metadata(parseMetadata(doc))
                    .build();
                
                return Optional.of(record);
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Error getting vector from Lucene: {}", id, e);
            return Optional.empty();
        }
    }
    
    @Override
    public boolean delete(String id) {
        try {
            log.debug("Deleting vector from Lucene: {}", id);
            
            indexWriter.deleteDocuments(new Term("id", id));
            indexWriter.commit();
            refreshReader();
            
            log.debug("Successfully deleted vector from Lucene: {}", id);
            return true;
            
        } catch (Exception e) {
            log.error("Error deleting vector from Lucene: {}", id, e);
            return false;
        }
    }
    
    @Override
    public int batchDelete(List<String> ids) {
        try {
            int deletedCount = 0;
            for (String id : ids) {
                if (delete(id)) {
                    deletedCount++;
                }
            }
            return deletedCount;
            
        } catch (Exception e) {
            log.error("Error batch deleting vectors from Lucene", e);
            return 0;
        }
    }
    
    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            stats.put("type", getType());
            stats.put("vectorCount", indexReader != null ? indexReader.numDocs() : 0);
            stats.put("searchCount", searchCount.get());
            stats.put("totalSearchTimeMs", totalSearchTime.get());
            
            if (searchCount.get() > 0) {
                stats.put("averageSearchTimeMs", totalSearchTime.get() / (double) searchCount.get());
            } else {
                stats.put("averageSearchTimeMs", 0.0);
            }
            
            stats.put("indexPath", directory.toString());
            
        } catch (Exception e) {
            log.error("Error getting Lucene statistics", e);
            stats.put("error", "Failed to get statistics");
        }
        
        return stats;
    }
    
    @Override
    public boolean isHealthy() {
        try {
            return indexReader != null && indexWriter != null && indexSearcher != null;
        } catch (Exception e) {
            log.error("Lucene health check failed", e);
            return false;
        }
    }
    
    @Override
    public void clear() {
        try {
            log.info("Clearing all vectors from Lucene");
            
            indexWriter.deleteAll();
            indexWriter.commit();
            refreshReader();
            
            searchCount.set(0);
            totalSearchTime.set(0);
            
            log.info("Successfully cleared all vectors from Lucene");
            
        } catch (Exception e) {
            log.error("Error clearing vectors from Lucene", e);
            throw new RuntimeException("Failed to clear vectors from Lucene", e);
        }
    }
    
    @Override
    public String getType() {
        return "lucene";
    }
    
    private void refreshReader() throws IOException {
        if (indexReader != null) {
            indexReader.close();
        }
        
        indexReader = DirectoryReader.open(directory);
        indexSearcher = new IndexSearcher(indexReader);
    }
    
    private String buildLuceneQuery(Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return "*:*"; // Match all documents
        }
        
        List<String> conditions = new ArrayList<>();
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().toString();
            conditions.add("meta_" + key + ":" + value);
        }
        
        return String.join(" AND ", conditions);
    }
    
    private List<Double> parseVector(String vectorText) {
        if (vectorText == null || vectorText.trim().isEmpty()) {
            return null;
        }
        
        try {
            return Arrays.stream(vectorText.split(","))
                .map(String::trim)
                .map(Double::parseDouble)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Error parsing vector: {}", vectorText, e);
            return null;
        }
    }
    
    private Map<String, Object> parseMetadata(Document doc) {
        Map<String, Object> metadata = new HashMap<>();
        
        // Extract metadata fields
        for (String fieldName : Arrays.asList("content", "entityType", "entityId")) {
            String value = doc.get("meta_" + fieldName);
            if (value != null) {
                metadata.put(fieldName, value);
            }
        }
        
        return metadata;
    }
    
    private String getMetadataString(Map<String, Object> metadata, String key, String defaultValue) {
        if (metadata == null) return defaultValue;
        Object value = metadata.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    private double calculateCosineSimilarity(List<Double> vectorA, List<Double> vectorB) {
        if (vectorA.size() != vectorB.size()) {
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
}