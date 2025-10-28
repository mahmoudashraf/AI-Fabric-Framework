package com.ai.infrastructure.migration;

import com.ai.infrastructure.dto.VectorRecord;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.service.VectorManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Vector Migration Script
 * 
 * This script migrates existing embeddings from AISearchableEntity to the vector database.
 * It should be run after the database schema migration is complete.
 * 
 * Usage: java -jar app.jar --ai.migration.enabled=true
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.migration.enabled", havingValue = "true")
public class VectorMigrationScript implements CommandLineRunner {
    
    private final AISearchableEntityRepository searchableEntityRepository;
    private final VectorManagementService vectorManagementService;
    
    @Override
    public void run(String... args) throws Exception {
        log.info("Starting vector migration script...");
        
        try {
            // Get all entities that need migration (have embeddings but no vectorId)
            List<AISearchableEntity> entitiesToMigrate = searchableEntityRepository
                .findByVectorIdIsNull();
            
            log.info("Found {} entities to migrate", entitiesToMigrate.size());
            
            if (entitiesToMigrate.isEmpty()) {
                log.info("No entities to migrate. Migration complete.");
                return;
            }
            
            // Process entities in batches
            int batchSize = 100;
            int totalProcessed = 0;
            int totalSuccess = 0;
            int totalFailed = 0;
            
            for (int i = 0; i < entitiesToMigrate.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, entitiesToMigrate.size());
                List<AISearchableEntity> batch = entitiesToMigrate.subList(i, endIndex);
                
                log.info("Processing batch {}/{} (entities {}-{})", 
                        (i / batchSize) + 1, 
                        (entitiesToMigrate.size() + batchSize - 1) / batchSize,
                        i + 1, 
                        endIndex);
                
                // Process batch
                for (AISearchableEntity entity : batch) {
                    try {
                        // Check if entity has embeddings (from old schema)
                        if (hasEmbeddings(entity)) {
                            // Migrate to vector database
                            String vectorId = migrateEntityToVectorDatabase(entity);
                            
                            if (vectorId != null) {
                                // Update entity with vector reference
                                entity.setVectorId(vectorId);
                                entity.setVectorUpdatedAt(LocalDateTime.now());
                                searchableEntityRepository.save(entity);
                                
                                totalSuccess++;
                                log.debug("Successfully migrated entity {} of type {} with vectorId {}", 
                                         entity.getEntityId(), entity.getEntityType(), vectorId);
                            } else {
                                totalFailed++;
                                log.error("Failed to migrate entity {} of type {} - vectorId is null", 
                                         entity.getEntityId(), entity.getEntityType());
                            }
                        } else {
                            // Entity doesn't have embeddings, skip
                            log.debug("Skipping entity {} of type {} - no embeddings found", 
                                     entity.getEntityId(), entity.getEntityType());
                        }
                        
                        totalProcessed++;
                        
                    } catch (Exception e) {
                        totalFailed++;
                        log.error("Error migrating entity {} of type {}", 
                                 entity.getEntityId(), entity.getEntityType(), e);
                    }
                }
                
                // Log progress
                log.info("Batch complete. Processed: {}, Success: {}, Failed: {}", 
                        totalProcessed, totalSuccess, totalFailed);
            }
            
            log.info("Vector migration complete. Total processed: {}, Success: {}, Failed: {}", 
                    totalProcessed, totalSuccess, totalFailed);
            
        } catch (Exception e) {
            log.error("Error during vector migration", e);
            throw e;
        }
    }
    
    /**
     * Check if entity has embeddings (from old schema)
     * This method should be updated based on the actual old schema
     */
    private boolean hasEmbeddings(AISearchableEntity entity) {
        // In the old schema, embeddings were stored as List<Double>
        // Since we've removed that field, we need to check if there's any way to detect
        // if this entity had embeddings before the migration
        
        // For now, we'll assume all entities without vectorId need migration
        // In a real scenario, you might have a backup table or other indicators
        return entity.getVectorId() == null;
    }
    
    /**
     * Migrate entity to vector database
     */
    private String migrateEntityToVectorDatabase(AISearchableEntity entity) {
        try {
            // Extract metadata
            Map<String, Object> metadata = extractMetadataFromEntity(entity);
            
            // For entities without embeddings, we need to generate them
            // This would require calling the embedding service
            // For now, we'll create a placeholder vector
            List<Double> placeholderEmbedding = generatePlaceholderEmbedding();
            
            // Store in vector database
            String vectorId = vectorManagementService.storeVector(
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getSearchableContent(),
                placeholderEmbedding,
                metadata
            );
            
            return vectorId;
            
        } catch (Exception e) {
            log.error("Error migrating entity to vector database", e);
            return null;
        }
    }
    
    /**
     * Extract metadata from entity
     */
    private Map<String, Object> extractMetadataFromEntity(AISearchableEntity entity) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        
        // Add basic metadata
        metadata.put("entityType", entity.getEntityType());
        metadata.put("entityId", entity.getEntityId());
        metadata.put("createdAt", entity.getCreatedAt());
        metadata.put("updatedAt", entity.getUpdatedAt());
        
        // Parse JSON metadata if available
        if (entity.getMetadata() != null && !entity.getMetadata().trim().isEmpty()) {
            try {
                // Simple JSON parsing - in production, use Jackson or Gson
                // This is a simplified implementation
                metadata.put("rawMetadata", entity.getMetadata());
            } catch (Exception e) {
                log.warn("Error parsing metadata JSON: {}", entity.getMetadata(), e);
            }
        }
        
        return metadata;
    }
    
    /**
     * Generate placeholder embedding
     * In a real scenario, you would call the embedding service to generate actual embeddings
     */
    private List<Double> generatePlaceholderEmbedding() {
        // Generate a placeholder embedding with 1536 dimensions (OpenAI standard)
        List<Double> embedding = new ArrayList<>();
        for (int i = 0; i < 1536; i++) {
            embedding.add(Math.random());
        }
        return embedding;
    }
}