package com.ai.infrastructure.relationship.service;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.relationship.dto.RelationshipDirection;
import com.ai.infrastructure.relationship.model.schema.EntityRelationshipSchema;
import com.ai.infrastructure.relationship.model.schema.EntitySchema;
import com.ai.infrastructure.relationship.model.schema.FieldInfo;
import com.ai.infrastructure.relationship.model.schema.RelationshipInfo;
import com.ai.infrastructure.relationship.config.RelationshipQueryProperties;
import com.ai.infrastructure.relationship.service.EntityRelationshipMapper;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Discovers AI-capable entities and their relationships via the JPA Metamodel.
 */
@Slf4j
public class RelationshipSchemaProvider {

    private final EntityManager entityManager;
    private final AIEntityConfigurationLoader configurationLoader;
    private final RelationshipQueryProperties properties;
    private final EntityRelationshipMapper relationshipMapper;
    private volatile EntityRelationshipSchema cachedSchema = EntityRelationshipSchema.empty();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public RelationshipSchemaProvider(EntityManager entityManager,
                                      AIEntityConfigurationLoader configurationLoader,
                                      RelationshipQueryProperties properties,
                                      EntityRelationshipMapper relationshipMapper) {
        this.entityManager = Objects.requireNonNull(entityManager);
        this.configurationLoader = configurationLoader;
        this.properties = Objects.requireNonNull(properties);
        this.relationshipMapper = Objects.requireNonNull(relationshipMapper);
    }

    @PostConstruct
    public void initialize() {
        if (properties.getSchema().isAutoDiscover() && initialized.compareAndSet(false, true)) {
            refreshSchema();
        }
    }

    public synchronized void refreshSchema() {
        if (!properties.getSchema().isAutoDiscover()) {
            cachedSchema = EntityRelationshipSchema.empty();
            return;
        }

        Map<String, EntitySchema> discovered = discoverSchemas();
        cachedSchema = EntityRelationshipSchema.builder()
            .entities(discovered)
            .refreshedAt(Instant.now())
            .build();

        if (properties.getSchema().isLogSchema()) {
            log.info("Relationship schema discovered for {} entity types", discovered.size());
        }
    }

    public EntityRelationshipSchema getSchema() {
        if (!initialized.get() && properties.getSchema().isAutoDiscover()) {
            refreshSchema();
            initialized.set(true);
        }
        return cachedSchema;
    }

    public Optional<EntitySchema> getEntitySchema(String entityType) {
        return getSchema().find(entityType);
    }

    public String getSchemaDescription(List<String> entityTypes) {
        EntityRelationshipSchema schema = getSchema();
        if (schema.entityCount() == 0) {
            return "No AI-capable entities are registered.";
        }

        List<String> requestedTypes = entityTypes == null || entityTypes.isEmpty()
            ? new ArrayList<>(schema.entities().keySet())
            : entityTypes;

        StringBuilder description = new StringBuilder("Available AI-Capable Entities:\n\n");
        for (String type : requestedTypes) {
            schema.find(type).ifPresent(entitySchema -> appendEntity(description, entitySchema));
        }
        return description.toString();
    }

    private Map<String, EntitySchema> discoverSchemas() {
        Metamodel metamodel = entityManager.getMetamodel();
        Set<EntityType<?>> entityTypes = metamodel.getEntities();
        Map<String, EntitySchema> schemas = new LinkedHashMap<>();
        Set<String> configuredTypes = configurationLoader != null
            ? configurationLoader.getSupportedEntityTypes()
            : Set.of();

        for (EntityType<?> entityType : entityTypes) {
            Class<?> javaType = entityType.getJavaType();
            if (javaType == null || !javaType.isAnnotationPresent(AICapable.class)) {
                continue;
            }
            String normalizedType = relationshipMapper.registerEntityType(javaType).entityType();
            if (!CollectionUtils.isEmpty(configuredTypes) && !configuredTypes.contains(normalizedType)) {
                continue;
            }
            EntitySchema schema = buildEntitySchema(entityType, normalizedType);
            schemas.put(normalizedType, schema);
        }
        return schemas;
    }

    private EntitySchema buildEntitySchema(EntityType<?> entityType, String entityTypeName) {
        List<FieldInfo> fields = new ArrayList<>();
        List<RelationshipInfo> relationships = new ArrayList<>();

        for (Attribute<?, ?> attribute : entityType.getAttributes()) {
            if (attribute.isAssociation()) {
                RelationshipInfo info = buildRelationshipInfo(attribute);
                if (info != null) {
                    relationships.add(info);
                    relationshipMapper.registerRelationship(
                        entityTypeName,
                        info.getTargetEntityType(),
                        info.getFieldName(),
                        info.getDirection(),
                        info.isOptional()
                    );
                }
            } else if (properties.getSchema().isIncludeFields()) {
                fields.add(buildFieldInfo(attribute));
            }
        }

        return EntitySchema.builder()
            .entityType(entityTypeName)
            .className(entityType.getJavaType().getSimpleName())
            .fullClassName(entityType.getJavaType().getName())
            .fields(fields)
            .relationships(relationships)
            .build();
    }

    private FieldInfo buildFieldInfo(Attribute<?, ?> attribute) {
        boolean nullable = attribute instanceof SingularAttribute<?, ?> singular && singular.isOptional();
        return FieldInfo.builder()
            .name(attribute.getName())
            .type(attribute.getJavaType() != null ? attribute.getJavaType().getSimpleName() : "Object")
            .nullable(nullable)
            .searchable(true)
            .build();
    }

    private RelationshipInfo buildRelationshipInfo(Attribute<?, ?> attribute) {
        Class<?> targetType = attribute.getJavaType();
        if (targetType == null || !targetType.isAnnotationPresent(AICapable.class)) {
            return null;
        }
        String targetEntityType = relationshipMapper.registerEntityType(targetType).entityType();
        return RelationshipInfo.builder()
            .fieldName(attribute.getName())
            .targetEntityType(targetEntityType)
            .targetClassName(targetType.getName())
            .relationshipType(attribute.getPersistentAttributeType().name())
            .direction(determineDirection(attribute))
            .optional(isOptional(attribute))
            .build();
    }

    private RelationshipDirection determineDirection(Attribute<?, ?> attribute) {
        return switch (attribute.getPersistentAttributeType()) {
            case MANY_TO_MANY -> RelationshipDirection.BIDIRECTIONAL;
            case ONE_TO_MANY -> RelationshipDirection.REVERSE;
            default -> RelationshipDirection.FORWARD;
        };
    }

    private boolean isOptional(Attribute<?, ?> attribute) {
        if (attribute instanceof SingularAttribute<?, ?> singular) {
            return singular.isOptional();
        }
        if (attribute instanceof PluralAttribute<?, ?, ?>) {
            return true;
        }
        return false;
    }

    private void appendEntity(StringBuilder description, EntitySchema schema) {
        description.append("Entity: ").append(schema.getEntityType())
            .append(" (Class: ").append(schema.getClassName()).append(")\n");
        description.append("  Fields:\n");
        for (FieldInfo field : schema.getFields()) {
            description.append("    - ").append(field.getName())
                .append(" (").append(field.getType()).append(")\n");
        }
        description.append("  Relationships:\n");
        for (RelationshipInfo rel : schema.getRelationships()) {
            description.append("    - ").append(rel.getFieldName())
                .append(" -> ").append(rel.getTargetEntityType())
                .append(" (").append(rel.getRelationshipType()).append(")\n");
        }
        description.append("\n");
    }
}
