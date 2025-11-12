package com.ai.infrastructure.relationship;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Relationship Schema Provider
 * 
 * Provides information about entity relationships in the system.
 * Can be extended to read from database schema, configuration files, or entity metadata.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class RelationshipSchemaProvider {
    
    private final AIEntityConfigurationLoader configurationLoader;
    
    // Cache of relationship schemas
    private final Map<String, EntityRelationshipSchema> schemaCache = new HashMap<>();
    
    public RelationshipSchemaProvider(AIEntityConfigurationLoader configurationLoader) {
        this.configurationLoader = configurationLoader;
    }
    
    /**
     * Get schema description for LLM
     */
    public String getSchemaDescription(List<String> entityTypes) {
        StringBuilder description = new StringBuilder();
        
        description.append("Entity Types:\n");
        for (String entityType : entityTypes) {
            description.append("- ").append(entityType).append("\n");
        }
        
        description.append("\nCommon Relationship Patterns:\n");
        description.append("- User -> Documents (hasDocuments, createdBy)\n");
        description.append("- Document -> User (createdBy, owner)\n");
        description.append("- Project -> Documents (contains, hasDocuments)\n");
        description.append("- Document -> Project (belongsTo, partOf)\n");
        description.append("- User -> Projects (owns, manages)\n");
        description.append("- Document -> Tags (taggedWith, hasTags)\n");
        description.append("- User -> Teams (memberOf, belongsTo)\n");
        
        // Add entity-specific relationships from configuration
        if (configurationLoader != null) {
            for (String entityType : entityTypes) {
                try {
                    var config = configurationLoader.getEntityConfig(entityType);
                    if (config != null && config.getMetadataFields() != null) {
                        description.append("\n").append(entityType).append(" metadata fields:\n");
                        config.getMetadataFields().forEach(field -> {
                            description.append("- ").append(field.getName())
                                .append(" (type: ").append(field.getType()).append(")\n");
                        });
                    }
                } catch (Exception e) {
                    log.debug("Could not load config for entity type: {}", entityType);
                }
            }
        }
        
        return description.toString();
    }
    
    /**
     * Get relationship schema for an entity type
     */
    public EntityRelationshipSchema getSchema(String entityType) {
        return schemaCache.computeIfAbsent(entityType, this::buildSchema);
    }
    
    private EntityRelationshipSchema buildSchema(String entityType) {
        // This can be enhanced to read from:
        // 1. Database schema introspection
        // 2. Configuration files
        // 3. Entity annotations
        // 4. Customer-provided relationship mappings
        
        EntityRelationshipSchema schema = new EntityRelationshipSchema();
        schema.setEntityType(entityType);
        schema.setRelationships(new ArrayList<>());
        
        // Example: Build relationships based on common patterns
        if ("document".equalsIgnoreCase(entityType)) {
            schema.getRelationships().add(new Relationship("createdBy", "user", Relationship.Direction.REVERSE));
            schema.getRelationships().add(new Relationship("belongsTo", "project", Relationship.Direction.FORWARD));
            schema.getRelationships().add(new Relationship("taggedWith", "tag", Relationship.Direction.BIDIRECTIONAL));
        } else if ("user".equalsIgnoreCase(entityType)) {
            schema.getRelationships().add(new Relationship("hasDocuments", "document", Relationship.Direction.FORWARD));
            schema.getRelationships().add(new Relationship("ownsProjects", "project", Relationship.Direction.FORWARD));
            schema.getRelationships().add(new Relationship("memberOf", "team", Relationship.Direction.FORWARD));
        } else if ("project".equalsIgnoreCase(entityType)) {
            schema.getRelationships().add(new Relationship("hasDocuments", "document", Relationship.Direction.FORWARD));
            schema.getRelationships().add(new Relationship("ownedBy", "user", Relationship.Direction.REVERSE));
        }
        
        return schema;
    }
    
    /**
     * Entity Relationship Schema
     */
    public static class EntityRelationshipSchema {
        private String entityType;
        private List<Relationship> relationships;
        
        public String getEntityType() {
            return entityType;
        }
        
        public void setEntityType(String entityType) {
            this.entityType = entityType;
        }
        
        public List<Relationship> getRelationships() {
            return relationships;
        }
        
        public void setRelationships(List<Relationship> relationships) {
            this.relationships = relationships;
        }
    }
    
    /**
     * Relationship
     */
    public static class Relationship {
        private String name;
        private String targetEntityType;
        private Direction direction;
        
        public Relationship(String name, String targetEntityType, Direction direction) {
            this.name = name;
            this.targetEntityType = targetEntityType;
            this.direction = direction;
        }
        
        public String getName() {
            return name;
        }
        
        public String getTargetEntityType() {
            return targetEntityType;
        }
        
        public Direction getDirection() {
            return direction;
        }
        
        public enum Direction {
            FORWARD, REVERSE, BIDIRECTIONAL
        }
    }
}
