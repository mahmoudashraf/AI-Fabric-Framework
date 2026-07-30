package com.ai.fabric.platform.backend.deployment.entityconfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class EntityConfigV03ToV04Migrator {

    private static final Set<String> PLATFORM_PROVENANCE_FIELDS = Set.of(
        "marketplaceManaged",
        "marketplacePluginId",
        "marketplaceInstallId",
        "marketplacePluginVersion"
    );
    private static final Set<String> LEGACY_ENTITY_FIELDS = Set.of(
        "entity-type",
        "features",
        "auto-process",
        "enable-search",
        "enable-recommendations",
        "auto-embedding",
        "indexable",
        "embeddable-fields",
        "crud-operations"
    );
    private static final Set<String> KNOWN_ENTITY_FIELDS = Set.of(
        "indexing",
        "analysis",
        "searchable-fields",
        "metadata-fields",
        "fields",
        "marketplaceManaged",
        "marketplacePluginId",
        "marketplaceInstallId",
        "marketplacePluginVersion"
    );
    private static final Set<String> METADATA_TYPES = Set.of(
        "AUTO",
        "STRING",
        "NUMBER",
        "BOOLEAN",
        "DATE",
        "ENUM",
        "ID",
        "JSON"
    );

    private final ObjectMapper objectMapper;
    private final EntityConfigContractService contractService;

    public EntityConfigV03ToV04Migrator(ObjectMapper objectMapper,
                                        EntityConfigContractService contractService) {
        this.objectMapper = objectMapper;
        this.contractService = contractService;
    }

    public EntityConfigMigrationResult preview(JsonNode candidate) {
        return preview(candidate, EntityConfigValidationContext.standard());
    }

    public EntityConfigMigrationResult preview(JsonNode candidate, EntityConfigValidationContext context) {
        EntityConfigValidationContext effectiveContext =
            context == null ? EntityConfigValidationContext.standard() : context;
        JsonNode source = candidate == null ? objectMapper.createObjectNode() : candidate;
        String beforeHash = hash(source);

        EntityConfigContractValidation existingV04 = contractService.validate(source, effectiveContext);
        if (existingV04.valid()) {
            ObjectNode normalized = existingV04.normalizedPlatformConfig();
            String afterHash = hash(normalized);
            boolean normalizationRequired = !beforeHash.equals(afterHash);
            return new EntityConfigMigrationResult(
                normalized,
                new EntityConfigMigrationReport(
                    EntityConfigContractService.CONTRACT_VERSION_V04,
                    EntityConfigContractService.CONTRACT_VERSION_V04,
                    normalizationRequired,
                    false,
                    beforeHash,
                    afterHash,
                    List.copyOf(existingV04.contract().entities().keySet()),
                    List.of(),
                    normalizationRequired
                        ? List.of(message(
                            "V04_NORMALIZED",
                            "$",
                            "The valid V0_4 draft was normalized to its deterministic representation."
                        ))
                        : List.of(),
                    List.of(),
                    normalizationRequired
                )
            );
        }

        List<EntityConfigMigrationMessage> warnings = new ArrayList<>();
        List<EntityConfigMigrationMessage> blockers = new ArrayList<>();
        LinkedHashSet<String> droppedKeys = new LinkedHashSet<>();
        List<String> convertedEntityTypes = new ArrayList<>();
        ObjectNode migrated = objectMapper.createObjectNode();

        JsonNode aiConfig = source.path("ai-config");
        ObjectNode migratedAiConfig = migrated.putObject("ai-config");
        JsonNode dimensions = aiConfig.path("vector-dimensions");
        if (dimensions.isIntegralNumber() && dimensions.canConvertToInt() && dimensions.asInt() > 0) {
            migratedAiConfig.put("vector-dimensions", dimensions.asInt());
        } else {
            blockers.add(message(
                "VECTOR_DIMENSIONS_MIGRATION_BLOCKED",
                "$.ai-config.vector-dimensions",
                "A positive vector-dimensions value is required before migration."
            ));
        }

        ObjectNode migratedEntities = migrated.putObject("ai-entities");
        JsonNode sourceEntities = source.path("ai-entities");
        if (!sourceEntities.isObject()) {
            blockers.add(message(
                "AI_ENTITIES_MIGRATION_BLOCKED",
                "$.ai-entities",
                "ai-entities must be an object before migration."
            ));
        } else {
            List<String> entityTypes = new ArrayList<>();
            sourceEntities.fieldNames().forEachRemaining(entityTypes::add);
            entityTypes.sort(String::compareTo);
            for (String entityType : entityTypes) {
                migrateEntity(
                    entityType,
                    sourceEntities.get(entityType),
                    migratedEntities,
                    convertedEntityTypes,
                    droppedKeys,
                    warnings,
                    blockers
                );
            }
        }

        EntityConfigContractValidation targetValidation = contractService.validate(migrated, effectiveContext);
        targetValidation.issues().forEach(issue -> blockers.add(message(
            "V04_" + issue.code(),
            issue.path(),
            issue.message()
        )));

        ObjectNode finalConfig = targetValidation.valid()
            ? targetValidation.normalizedPlatformConfig()
            : migrated;
        String afterHash = hash(finalConfig);
        boolean migrationRequired = !beforeHash.equals(afterHash);
        return new EntityConfigMigrationResult(
            finalConfig,
            new EntityConfigMigrationReport(
                EntityConfigContractService.CONTRACT_VERSION_V03,
                EntityConfigContractService.CONTRACT_VERSION_V04,
                migrationRequired,
                !blockers.isEmpty(),
                beforeHash,
                afterHash,
                convertedEntityTypes,
                List.copyOf(droppedKeys),
                warnings,
                blockers,
                migrationRequired
            )
        );
    }

    private void migrateEntity(String entityType,
                               JsonNode source,
                               ObjectNode migratedEntities,
                               List<String> convertedEntityTypes,
                               LinkedHashSet<String> droppedKeys,
                               List<EntityConfigMigrationMessage> warnings,
                               List<EntityConfigMigrationMessage> blockers) {
        String path = entityPath(entityType);
        if (source == null || !source.isObject()) {
            blockers.add(message(
                "ENTITY_MIGRATION_BLOCKED",
                path,
                "Each entity type must map to an object."
            ));
            return;
        }
        ObjectNode target = migratedEntities.putObject(entityType);
        convertedEntityTypes.add(entityType);
        rejectUnknownEntityFields(source, path, blockers);
        recordLegacyEntityFields(source, path, droppedKeys, warnings);
        copyPlatformProvenance(source, target);

        ObjectNode indexing = target.putObject("indexing");
        boolean enabled = legacyIndexingEnabled(source, path, blockers);
        indexing.put("enabled", enabled);
        indexing.put("max-characters", legacyMaxCharacters(source, path, blockers));

        ObjectNode analysis = target.putObject("analysis");
        analysis.put("enabled", false);
        analysis.putArray("after");

        ArrayNode searchable = target.putArray("searchable-fields");
        migrateSearchableFields(
            source.path("searchable-fields"),
            searchable,
            path + ".searchable-fields",
            droppedKeys,
            warnings,
            blockers
        );
        migrateMetadataFields(
            source.path("metadata-fields"),
            target,
            path + ".metadata-fields",
            droppedKeys,
            warnings,
            blockers
        );
        if (source.has("embeddable-fields")) {
            warnings.add(message(
                "EMBEDDABLE_FIELDS_REQUIRE_MANUAL_REVIEW",
                path + ".embeddable-fields",
                "embeddable-fields were dropped and were not merged into searchable-fields."
            ));
        }
        if (source.has("fields")) {
            blockers.add(message(
                "UNSUPPORTED_SIMPLIFIED_FIELDS",
                path + ".fields",
                "The legacy fields array does not identify a semantic projection and cannot be migrated automatically."
            ));
        }
    }

    private void migrateSearchableFields(JsonNode sourceFields,
                                         ArrayNode targetFields,
                                         String path,
                                         LinkedHashSet<String> droppedKeys,
                                         List<EntityConfigMigrationMessage> warnings,
                                         List<EntityConfigMigrationMessage> blockers) {
        if (!sourceFields.isArray() || sourceFields.isEmpty()) {
            blockers.add(message(
                "SEARCHABLE_FIELDS_MIGRATION_BLOCKED",
                path,
                "At least one searchable field must be selected explicitly."
            ));
            return;
        }
        for (int index = 0; index < sourceFields.size(); index++) {
            JsonNode sourceField = sourceFields.get(index);
            String fieldPath = path + "[" + index + "]";
            if (sourceField == null || !sourceField.isObject()) {
                blockers.add(message(
                    "SEARCHABLE_FIELD_MIGRATION_BLOCKED",
                    fieldPath,
                    "Each searchable field must be an object."
                ));
                continue;
            }
            String name = sourceField.path("name").asText("").trim();
            if (!StringUtils.hasText(name)) {
                blockers.add(message(
                    "SEARCHABLE_FIELD_NAME_MIGRATION_BLOCKED",
                    fieldPath + ".name",
                    "Searchable field name is required."
                ));
                continue;
            }

            ObjectNode targetField = targetFields.addObject();
            targetField.put("name", name);
            ArrayNode destinations = targetField.putArray("destinations");
            migrateSearchDestinations(sourceField, destinations, fieldPath, droppedKeys, warnings);
            targetField.put(
                "preprocessing",
                validEnum(sourceField.path("preprocessing").asText(""), Set.of("NONE", "NORMALIZE", "CLEAN", "SANITIZE"))
                    ? sourceField.path("preprocessing").asText().toUpperCase(Locale.ROOT)
                    : "NORMALIZE"
            );
            targetField.put(
                "max-length",
                sourceField.path("max-length").isIntegralNumber()
                    ? sourceField.path("max-length").asInt()
                    : -1
            );
            targetField.put(
                "priority",
                sourceField.path("priority").isIntegralNumber()
                    ? sourceField.path("priority").asInt()
                    : Math.max(0, 100 - index)
            );
            targetField.put("required", sourceField.path("required").asBoolean(false));
            if (sourceField.has("weight")) {
                droppedKeys.add(fieldPath + ".weight");
                warnings.add(message(
                    "WEIGHT_REMOVED",
                    fieldPath + ".weight",
                    "Search weight was removed; priority preserves projection order, not similarity ranking."
                ));
            }
            if (sourceField.has("include-in-rag")) {
                droppedKeys.add(fieldPath + ".include-in-rag");
            }
            if (sourceField.has("enable-semantic-search")) {
                droppedKeys.add(fieldPath + ".enable-semantic-search");
            }
        }
    }

    private void migrateSearchDestinations(JsonNode sourceField,
                                           ArrayNode destinations,
                                           String fieldPath,
                                           LinkedHashSet<String> droppedKeys,
                                           List<EntityConfigMigrationMessage> warnings) {
        JsonNode currentDestinations = sourceField.path("destinations");
        if (currentDestinations.isArray() && !currentDestinations.isEmpty()) {
            Set<String> values = new LinkedHashSet<>();
            currentDestinations.forEach(value -> {
                if (value.isTextual()) {
                    values.add(value.asText().trim().toUpperCase(Locale.ROOT));
                }
            });
            values.stream().sorted().forEach(destinations::add);
            return;
        }

        boolean semantic = !sourceField.has("enable-semantic-search")
            || sourceField.path("enable-semantic-search").asBoolean(false);
        boolean rag = !sourceField.has("include-in-rag")
            || sourceField.path("include-in-rag").asBoolean(false);
        if (semantic) {
            destinations.add("SEMANTIC_SEARCH");
        }
        if (rag) {
            destinations.add("RAG_CONTEXT");
        }
        if (!semantic) {
            warnings.add(message(
                "SEMANTIC_SEARCH_DISABLED",
                fieldPath + ".enable-semantic-search",
                "Legacy semantic search was disabled for this field."
            ));
        }
    }

    private void migrateMetadataFields(JsonNode sourceFields,
                                       ObjectNode targetEntity,
                                       String path,
                                       LinkedHashSet<String> droppedKeys,
                                       List<EntityConfigMigrationMessage> warnings,
                                       List<EntityConfigMigrationMessage> blockers) {
        if (sourceFields.isMissingNode() || sourceFields.isNull()) {
            return;
        }
        if (!sourceFields.isArray()) {
            blockers.add(message(
                "METADATA_FIELDS_MIGRATION_BLOCKED",
                path,
                "metadata-fields must be an array."
            ));
            return;
        }
        ArrayNode targetFields = targetEntity.putArray("metadata-fields");
        for (int index = 0; index < sourceFields.size(); index++) {
            JsonNode sourceField = sourceFields.get(index);
            String fieldPath = path + "[" + index + "]";
            if (sourceField == null || !sourceField.isObject()) {
                blockers.add(message(
                    "METADATA_FIELD_MIGRATION_BLOCKED",
                    fieldPath,
                    "Each metadata field must be an object."
                ));
                continue;
            }
            String name = sourceField.path("name").asText("").trim();
            if (!StringUtils.hasText(name)) {
                blockers.add(message(
                    "METADATA_FIELD_NAME_MIGRATION_BLOCKED",
                    fieldPath + ".name",
                    "Metadata field name is required."
                ));
                continue;
            }
            boolean include = !sourceField.has("include-in-search")
                || sourceField.path("include-in-search").asBoolean(false);
            if (!include && !sourceField.path("destinations").isArray()) {
                droppedKeys.add(fieldPath + ".include-in-search");
                warnings.add(message(
                    "METADATA_FIELD_DROPPED",
                    fieldPath,
                    "Metadata excluded from search was dropped because no V0_4 destination was approved."
                ));
                continue;
            }

            String dataType = migrateMetadataType(sourceField, fieldPath, droppedKeys, blockers);
            if (dataType == null) {
                continue;
            }
            ObjectNode targetField = targetFields.addObject();
            targetField.put("name", name);
            targetField.put("data-type", dataType);
            copyText(sourceField, targetField, "format");
            copyText(sourceField, targetField, "description");
            ArrayNode destinations = targetField.putArray("destinations");
            if (sourceField.path("destinations").isArray() && !sourceField.path("destinations").isEmpty()) {
                Set<String> values = new LinkedHashSet<>();
                sourceField.path("destinations").forEach(value -> {
                    if (value.isTextual()) {
                        values.add(value.asText().trim().toUpperCase(Locale.ROOT));
                    }
                });
                values.stream().sorted().forEach(destinations::add);
            } else {
                destinations.add("VECTOR_METADATA");
            }
            targetField.put(
                "priority",
                sourceField.path("priority").isIntegralNumber()
                    ? sourceField.path("priority").asInt()
                    : Math.max(0, 100 - index)
            );
            targetField.put("required", sourceField.path("required").asBoolean(false));
            targetField.put("sanitize-pii", sourceField.path("sanitize-pii").asBoolean(false));
            if (sourceField.has("include-in-search")) {
                droppedKeys.add(fieldPath + ".include-in-search");
            }
        }
        if (targetFields.isEmpty()) {
            targetEntity.remove("metadata-fields");
        }
    }

    private String migrateMetadataType(JsonNode sourceField,
                                       String fieldPath,
                                       LinkedHashSet<String> droppedKeys,
                                       List<EntityConfigMigrationMessage> blockers) {
        String value = sourceField.path("data-type").asText("").trim();
        if (!StringUtils.hasText(value)) {
            value = sourceField.path("type").asText("AUTO").trim();
            if (sourceField.has("type")) {
                droppedKeys.add(fieldPath + ".type");
            }
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        if (!METADATA_TYPES.contains(normalized)) {
            blockers.add(message(
                "UNKNOWN_METADATA_TYPE",
                sourceField.has("data-type") ? fieldPath + ".data-type" : fieldPath + ".type",
                "Unknown metadata type '" + value + "' requires an operator decision."
            ));
            return null;
        }
        return normalized;
    }

    private boolean legacyIndexingEnabled(JsonNode source,
                                          String path,
                                          List<EntityConfigMigrationMessage> blockers) {
        JsonNode current = source.path("indexing").path("enabled");
        if (current.isBoolean()) {
            return current.asBoolean();
        }
        JsonNode legacy = source.get("indexable");
        if (legacy == null) {
            return true;
        }
        if (!legacy.isBoolean()) {
            blockers.add(message(
                "INDEXABLE_BOOLEAN_REQUIRED",
                path + ".indexable",
                "Legacy indexable must be a boolean."
            ));
            return false;
        }
        return legacy.asBoolean();
    }

    private int legacyMaxCharacters(JsonNode source,
                                    String path,
                                    List<EntityConfigMigrationMessage> blockers) {
        JsonNode current = source.path("indexing").get("max-characters");
        if (current == null) {
            return EntityConfigContractService.DEFAULT_MAX_CHARACTERS;
        }
        if (!current.isIntegralNumber()
            || !current.canConvertToInt()
            || current.asInt() < 1
            || current.asInt() > EntityConfigContractService.DEFAULT_MAX_CHARACTERS) {
            blockers.add(message(
                "MAX_CHARACTERS_MIGRATION_BLOCKED",
                path + ".indexing.max-characters",
                "max-characters must be an integer from 1 through 8000."
            ));
            return EntityConfigContractService.DEFAULT_MAX_CHARACTERS;
        }
        return current.asInt();
    }

    private void rejectUnknownEntityFields(JsonNode source,
                                           String path,
                                           List<EntityConfigMigrationMessage> blockers) {
        Iterator<String> names = source.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!KNOWN_ENTITY_FIELDS.contains(name) && !LEGACY_ENTITY_FIELDS.contains(name)) {
                blockers.add(message(
                    "UNKNOWN_ENTITY_PROPERTY",
                    path + "." + name,
                    "Unknown entity property '" + name + "' requires an operator decision."
                ));
            }
        }
    }

    private void recordLegacyEntityFields(JsonNode source,
                                          String path,
                                          LinkedHashSet<String> droppedKeys,
                                          List<EntityConfigMigrationMessage> warnings) {
        LEGACY_ENTITY_FIELDS.stream().sorted().forEach(name -> {
            if (!source.has(name)) {
                return;
            }
            droppedKeys.add(path + "." + name);
            if (!"embeddable-fields".equals(name)) {
                warnings.add(message(
                    "LEGACY_PROPERTY_REMOVED",
                    path + "." + name,
                    "Legacy property '" + name + "' was removed from the V0_4 contract."
                ));
            }
        });
    }

    private void copyPlatformProvenance(JsonNode source, ObjectNode target) {
        PLATFORM_PROVENANCE_FIELDS.forEach(name -> {
            if (source.has(name)) {
                target.set(name, source.get(name).deepCopy());
            }
        });
    }

    private void copyText(JsonNode source, ObjectNode target, String name) {
        if (source.path(name).isTextual()) {
            target.put(name, source.path(name).asText());
        }
    }

    private boolean validEnum(String value, Set<String> values) {
        return StringUtils.hasText(value) && values.contains(value.trim().toUpperCase(Locale.ROOT));
    }

    private String hash(JsonNode node) {
        try {
            String value = objectMapper.writeValueAsString(canonicalize(node));
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash entity configuration.", ex);
        }
    }

    private JsonNode canonicalize(JsonNode node) throws JsonProcessingException {
        if (node == null || node.isNull()) {
            return objectMapper.nullNode();
        }
        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            node.forEach(item -> {
                try {
                    array.add(canonicalize(item));
                } catch (JsonProcessingException ex) {
                    throw new IllegalStateException(ex);
                }
            });
            return array;
        }
        if (!node.isObject()) {
            return node.deepCopy();
        }
        ObjectNode object = objectMapper.createObjectNode();
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        names.sort(Comparator.naturalOrder());
        for (String name : names) {
            object.set(name, canonicalize(node.get(name)));
        }
        return object;
    }

    private String entityPath(String entityType) {
        return "$.ai-entities['"
            + entityType.replace("\\", "\\\\").replace("'", "\\'")
            + "']";
    }

    private EntityConfigMigrationMessage message(String code, String path, String message) {
        return new EntityConfigMigrationMessage(code, path, message);
    }
}
