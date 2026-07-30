package com.ai.fabric.platform.backend.deployment.entityconfig;

import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractV04.AnalysisConfig;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractV04.AnalysisOperation;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractV04.EntityProjectionConfig;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractV04.IndexingConfig;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractV04.MarketplaceProvenance;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractV04.MetadataDataType;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractV04.MetadataDestination;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractV04.MetadataFieldConfig;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractV04.SearchDestination;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractV04.SearchPreprocessing;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractV04.SearchableFieldConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class EntityConfigContractService {

    public static final String CONTRACT_VERSION_V03 = "AI_ENTITY_CONFIG_V0_3";
    public static final String CONTRACT_VERSION_V04 = "AI_ENTITY_CONFIG_V0_4";
    public static final int DEFAULT_MAX_CHARACTERS = 8_000;
    public static final int DEFAULT_FIELD_PRIORITY = 50;
    public static final int DEFAULT_FIELD_MAX_LENGTH = -1;

    private static final Set<String> ROOT_PROPERTIES = Set.of("ai-config", "ai-entities");
    private static final Set<String> AI_CONFIG_PROPERTIES = Set.of("vector-dimensions");
    private static final Set<String> ENTITY_PROPERTIES = Set.of(
        "indexing",
        "analysis",
        "searchable-fields",
        "metadata-fields",
        "marketplaceManaged",
        "marketplacePluginId",
        "marketplaceInstallId",
        "marketplacePluginVersion"
    );
    private static final Set<String> INDEXING_PROPERTIES = Set.of("enabled", "max-characters");
    private static final Set<String> ANALYSIS_PROPERTIES = Set.of("enabled", "after");
    private static final Set<String> SEARCHABLE_PROPERTIES = Set.of(
        "name",
        "destinations",
        "preprocessing",
        "max-length",
        "priority",
        "required"
    );
    private static final Set<String> METADATA_PROPERTIES = Set.of(
        "name",
        "data-type",
        "format",
        "description",
        "destinations",
        "priority",
        "required",
        "sanitize-pii"
    );
    private static final Set<String> REMOVED_ENTITY_PROPERTIES = Set.of(
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
    private static final Set<String> REMOVED_SEARCHABLE_PROPERTIES = Set.of(
        "include-in-rag",
        "enable-semantic-search",
        "weight"
    );
    private static final Set<String> REMOVED_METADATA_PROPERTIES = Set.of(
        "include-in-search",
        "type"
    );

    private final ObjectMapper objectMapper;

    public EntityConfigContractService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EntityConfigContractValidation validate(JsonNode candidate, EntityConfigValidationContext context) {
        List<EntityConfigContractIssue> issues = new ArrayList<>();
        EntityConfigValidationContext effectiveContext =
            context == null ? EntityConfigValidationContext.standard() : context;
        if (candidate == null || !candidate.isObject()) {
            issues.add(issue(
                "ENTITY_CONFIG_OBJECT_REQUIRED",
                "$",
                "Entity configuration must be an object."
            ));
            return invalid(issues);
        }

        rejectUnknownProperties(candidate, ROOT_PROPERTIES, Set.of(), "$", "ENTITY_CONFIG_PROPERTY_UNKNOWN", issues);
        JsonNode aiConfigNode = candidate.path("ai-config");
        int vectorDimensions = parseVectorDimensions(aiConfigNode, issues);
        JsonNode entitiesNode = candidate.path("ai-entities");
        if (!entitiesNode.isObject()) {
            issues.add(issue(
                "AI_ENTITIES_REQUIRED",
                "$.ai-entities",
                "ai-entities must be an object keyed by entity type."
            ));
            return invalid(issues);
        }

        Map<String, EntityProjectionConfig> entities = new LinkedHashMap<>();
        List<String> entityTypes = new ArrayList<>();
        entitiesNode.fieldNames().forEachRemaining(entityTypes::add);
        entityTypes.sort(String::compareTo);
        for (String entityType : entityTypes) {
            String entityPath = entityPath(entityType);
            if (!StringUtils.hasText(entityType)) {
                issues.add(issue(
                    "ENTITY_TYPE_REQUIRED",
                    entityPath,
                    "Entity type keys must not be blank."
                ));
                continue;
            }
            EntityProjectionConfig entity = parseEntity(
                entityType.trim(),
                entitiesNode.get(entityType),
                entityPath,
                effectiveContext,
                issues
            );
            if (entity != null) {
                entities.put(entityType.trim(), entity);
            }
        }

        if (!issues.isEmpty()) {
            return invalid(issues);
        }

        EntityConfigContractV04 contract = new EntityConfigContractV04(vectorDimensions, entities);
        return new EntityConfigContractValidation(
            contract,
            toJson(contract, true),
            toJson(contract, false),
            List.of()
        );
    }

    public EntityConfigContractValidation requireValid(JsonNode candidate, EntityConfigValidationContext context) {
        EntityConfigContractValidation validation = validate(candidate, context);
        if (!validation.valid()) {
            throw new EntityConfigContractException(validation.issues());
        }
        return validation;
    }

    private int parseVectorDimensions(JsonNode aiConfigNode, List<EntityConfigContractIssue> issues) {
        if (!aiConfigNode.isObject()) {
            issues.add(issue(
                "AI_CONFIG_REQUIRED",
                "$.ai-config",
                "ai-config object is required."
            ));
            return -1;
        }
        rejectUnknownProperties(
            aiConfigNode,
            AI_CONFIG_PROPERTIES,
            Set.of(),
            "$.ai-config",
            "AI_CONFIG_PROPERTY_UNKNOWN",
            issues
        );
        JsonNode dimensionsNode = aiConfigNode.get("vector-dimensions");
        if (dimensionsNode == null
            || !dimensionsNode.isIntegralNumber()
            || !dimensionsNode.canConvertToInt()
            || dimensionsNode.asInt() <= 0) {
            issues.add(issue(
                "VECTOR_DIMENSIONS_INVALID",
                "$.ai-config.vector-dimensions",
                "vector-dimensions must be a positive integer."
            ));
            return -1;
        }
        return dimensionsNode.asInt();
    }

    private EntityProjectionConfig parseEntity(String entityType,
                                               JsonNode entityNode,
                                               String path,
                                               EntityConfigValidationContext context,
                                               List<EntityConfigContractIssue> issues) {
        if (entityNode == null || !entityNode.isObject()) {
            issues.add(issue(
                "ENTITY_OBJECT_REQUIRED",
                path,
                "Each entity type must map to an object."
            ));
            return null;
        }
        rejectUnknownProperties(
            entityNode,
            ENTITY_PROPERTIES,
            REMOVED_ENTITY_PROPERTIES,
            path,
            "ENTITY_PROPERTY_UNKNOWN",
            issues
        );

        IndexingConfig indexing = parseIndexing(entityNode.path("indexing"), path + ".indexing", issues);
        AnalysisConfig analysis = parseAnalysis(entityNode.path("analysis"), path + ".analysis", issues);
        List<SearchableFieldConfig> searchableFields = parseSearchableFields(
            entityNode.path("searchable-fields"),
            path + ".searchable-fields",
            context,
            issues
        );
        List<MetadataFieldConfig> metadataFields = parseMetadataFields(
            entityNode.path("metadata-fields"),
            path + ".metadata-fields",
            context,
            issues
        );

        boolean semanticSearchDeclared = searchableFields.stream()
            .anyMatch(field -> field.destinations().contains(SearchDestination.SEMANTIC_SEARCH));
        if (indexing != null && indexing.enabled() && !semanticSearchDeclared) {
            issues.add(issue(
                "SEMANTIC_SEARCH_FIELD_REQUIRED",
                path + ".searchable-fields",
                "An enabled entity must declare at least one SEMANTIC_SEARCH field."
            ));
        }
        if (analysis != null && analysis.enabled() && (indexing == null || !indexing.enabled())) {
            issues.add(issue(
                "ANALYSIS_REQUIRES_INDEXING",
                path + ".analysis.enabled",
                "Analysis cannot be enabled when indexing is disabled."
            ));
        }
        if (context.sharedVectorStorage()) {
            validateSharedTenantMetadata(metadataFields, path, issues);
        }

        MarketplaceProvenance provenance = parseMarketplaceProvenance(entityNode, path, issues);
        if (indexing == null || analysis == null) {
            return null;
        }
        return new EntityProjectionConfig(
            indexing,
            analysis,
            searchableFields,
            metadataFields,
            provenance
        );
    }

    private IndexingConfig parseIndexing(JsonNode indexingNode,
                                         String path,
                                         List<EntityConfigContractIssue> issues) {
        if (!indexingNode.isObject()) {
            issues.add(issue(
                "INDEXING_CONFIG_REQUIRED",
                path,
                "indexing must be an object with enabled=true."
            ));
            return null;
        }
        rejectUnknownProperties(
            indexingNode,
            INDEXING_PROPERTIES,
            Set.of(),
            path,
            "INDEXING_PROPERTY_UNKNOWN",
            issues
        );

        JsonNode enabledNode = indexingNode.get("enabled");
        boolean enabled = enabledNode != null && enabledNode.isBoolean() && enabledNode.asBoolean();
        if (!enabled) {
            issues.add(issue(
                "INDEXING_NOT_EXPLICITLY_ENABLED",
                path + ".enabled",
                "Platform Data Sync entity scopes require indexing.enabled=true."
            ));
        }

        int maxCharacters = DEFAULT_MAX_CHARACTERS;
        JsonNode maxNode = indexingNode.get("max-characters");
        if (maxNode != null) {
            if (!maxNode.isIntegralNumber()
                || !maxNode.canConvertToInt()
                || maxNode.asInt() < 1
                || maxNode.asInt() > DEFAULT_MAX_CHARACTERS) {
                issues.add(issue(
                    "INVALID_PROJECTION_BUDGET",
                    path + ".max-characters",
                    "max-characters must be an integer from 1 through 8000."
                ));
            } else {
                maxCharacters = maxNode.asInt();
            }
        }
        return new IndexingConfig(enabled, maxCharacters);
    }

    private AnalysisConfig parseAnalysis(JsonNode analysisNode,
                                         String path,
                                         List<EntityConfigContractIssue> issues) {
        if (analysisNode.isMissingNode() || analysisNode.isNull()) {
            return new AnalysisConfig(false, List.of());
        }
        if (!analysisNode.isObject()) {
            issues.add(issue(
                "ANALYSIS_CONFIG_OBJECT_REQUIRED",
                path,
                "analysis must be an object when provided."
            ));
            return null;
        }
        rejectUnknownProperties(
            analysisNode,
            ANALYSIS_PROPERTIES,
            Set.of(),
            path,
            "ANALYSIS_PROPERTY_UNKNOWN",
            issues
        );

        boolean enabled = false;
        JsonNode enabledNode = analysisNode.get("enabled");
        if (enabledNode != null) {
            if (!enabledNode.isBoolean()) {
                issues.add(issue(
                    "ANALYSIS_ENABLED_BOOLEAN_REQUIRED",
                    path + ".enabled",
                    "analysis.enabled must be a boolean."
                ));
            } else {
                enabled = enabledNode.asBoolean();
            }
        }

        JsonNode afterNode = analysisNode.get("after");
        List<AnalysisOperation> after = !enabled && (afterNode == null || afterNode.isNull())
            ? List.of()
            : enumList(
                afterNode,
                AnalysisOperation.class,
                path + ".after",
                "ANALYSIS_AFTER_ARRAY_REQUIRED",
                "ANALYSIS_OPERATION_UNKNOWN",
                true,
                issues
            );
        if (enabled && after.isEmpty()) {
            issues.add(issue(
                "ANALYSIS_OPERATIONS_REQUIRED",
                path + ".after",
                "analysis.after must contain at least one operation when analysis is enabled."
            ));
        }
        return new AnalysisConfig(enabled, after);
    }

    private List<SearchableFieldConfig> parseSearchableFields(JsonNode fieldsNode,
                                                              String path,
                                                              EntityConfigValidationContext context,
                                                              List<EntityConfigContractIssue> issues) {
        if (!fieldsNode.isArray() || fieldsNode.isEmpty()) {
            issues.add(issue(
                "SEARCHABLE_FIELDS_REQUIRED",
                path,
                "searchable-fields must contain at least one field."
            ));
            return List.of();
        }

        List<SearchableFieldConfig> fields = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int index = 0; index < fieldsNode.size(); index++) {
            JsonNode fieldNode = fieldsNode.get(index);
            String fieldPath = path + "[" + index + "]";
            if (fieldNode == null || !fieldNode.isObject()) {
                issues.add(issue(
                    "SEARCHABLE_FIELD_OBJECT_REQUIRED",
                    fieldPath,
                    "Each searchable field must be an object."
                ));
                continue;
            }
            rejectUnknownProperties(
                fieldNode,
                SEARCHABLE_PROPERTIES,
                REMOVED_SEARCHABLE_PROPERTIES,
                fieldPath,
                "SEARCHABLE_PROPERTY_UNKNOWN",
                issues
            );

            String name = requiredText(fieldNode.get("name"), fieldPath + ".name", "SEARCHABLE_FIELD_NAME_REQUIRED", issues);
            if (StringUtils.hasText(name) && !names.add(name.toLowerCase(Locale.ROOT))) {
                issues.add(issue(
                    "DUPLICATE_SEARCHABLE_FIELD",
                    fieldPath + ".name",
                    "Searchable field names must be unique case-insensitively."
                ));
            }
            List<SearchDestination> destinations = enumList(
                fieldNode.get("destinations"),
                SearchDestination.class,
                fieldPath + ".destinations",
                "SEARCHABLE_DESTINATION_REQUIRED",
                "SEARCHABLE_DESTINATION_UNKNOWN",
                issues
            );
            SearchPreprocessing preprocessing = optionalEnum(
                fieldNode.get("preprocessing"),
                SearchPreprocessing.class,
                SearchPreprocessing.NORMALIZE,
                fieldPath + ".preprocessing",
                "SEARCHABLE_PREPROCESSING_UNKNOWN",
                issues
            );
            int maxLength = optionalInt(
                fieldNode.get("max-length"),
                DEFAULT_FIELD_MAX_LENGTH,
                fieldPath + ".max-length",
                "INVALID_SEARCHABLE_MAX_LENGTH",
                value -> value == -1 || value > 0,
                "max-length must be -1 or a positive integer.",
                issues
            );
            int priority = priority(fieldNode.get("priority"), fieldPath + ".priority", "INVALID_SEARCHABLE_PRIORITY", issues);
            boolean required = optionalBoolean(
                fieldNode.get("required"),
                false,
                fieldPath + ".required",
                "SEARCHABLE_REQUIRED_BOOLEAN_REQUIRED",
                issues
            );
            if (preprocessing == SearchPreprocessing.SANITIZE && !context.piiCapabilityAvailable()) {
                issues.add(issue(
                    "PII_CAPABILITY_REQUIRED",
                    fieldPath + ".preprocessing",
                    "SANITIZE preprocessing requires the AI Fabric PII capability in the runtime."
                ));
            }
            if (StringUtils.hasText(name)) {
                fields.add(new SearchableFieldConfig(
                    name,
                    destinations,
                    preprocessing,
                    maxLength,
                    priority,
                    required
                ));
            }
        }
        return fields;
    }

    private List<MetadataFieldConfig> parseMetadataFields(JsonNode fieldsNode,
                                                          String path,
                                                          EntityConfigValidationContext context,
                                                          List<EntityConfigContractIssue> issues) {
        if (fieldsNode.isMissingNode() || fieldsNode.isNull()) {
            return List.of();
        }
        if (!fieldsNode.isArray()) {
            issues.add(issue(
                "METADATA_FIELDS_ARRAY_REQUIRED",
                path,
                "metadata-fields must be an array when provided."
            ));
            return List.of();
        }

        List<MetadataFieldConfig> fields = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int index = 0; index < fieldsNode.size(); index++) {
            JsonNode fieldNode = fieldsNode.get(index);
            String fieldPath = path + "[" + index + "]";
            if (fieldNode == null || !fieldNode.isObject()) {
                issues.add(issue(
                    "METADATA_FIELD_OBJECT_REQUIRED",
                    fieldPath,
                    "Each metadata field must be an object."
                ));
                continue;
            }
            rejectUnknownProperties(
                fieldNode,
                METADATA_PROPERTIES,
                REMOVED_METADATA_PROPERTIES,
                fieldPath,
                "METADATA_PROPERTY_UNKNOWN",
                issues
            );

            String name = requiredText(fieldNode.get("name"), fieldPath + ".name", "METADATA_FIELD_NAME_REQUIRED", issues);
            if (StringUtils.hasText(name) && !names.add(name.toLowerCase(Locale.ROOT))) {
                issues.add(issue(
                    "DUPLICATE_METADATA_FIELD",
                    fieldPath + ".name",
                    "Metadata field names must be unique case-insensitively."
                ));
            }
            MetadataDataType dataType = optionalEnum(
                fieldNode.get("data-type"),
                MetadataDataType.class,
                MetadataDataType.AUTO,
                fieldPath + ".data-type",
                "METADATA_DATA_TYPE_UNKNOWN",
                issues
            );
            String format = optionalText(fieldNode.get("format"), fieldPath + ".format", "METADATA_FORMAT_STRING_REQUIRED", issues);
            String description = optionalText(
                fieldNode.get("description"),
                fieldPath + ".description",
                "METADATA_DESCRIPTION_STRING_REQUIRED",
                issues
            );
            if (description != null && description.length() > 500) {
                issues.add(issue(
                    "METADATA_DESCRIPTION_TOO_LONG",
                    fieldPath + ".description",
                    "Metadata description must not exceed 500 characters."
                ));
            }
            validateFormat(dataType, format, fieldPath + ".format", issues);
            List<MetadataDestination> destinations = enumList(
                fieldNode.get("destinations"),
                MetadataDestination.class,
                fieldPath + ".destinations",
                "METADATA_DESTINATION_REQUIRED",
                "METADATA_DESTINATION_UNKNOWN",
                issues
            );
            int priority = priority(fieldNode.get("priority"), fieldPath + ".priority", "INVALID_METADATA_PRIORITY", issues);
            boolean required = optionalBoolean(
                fieldNode.get("required"),
                false,
                fieldPath + ".required",
                "METADATA_REQUIRED_BOOLEAN_REQUIRED",
                issues
            );
            boolean sanitizePii = optionalBoolean(
                fieldNode.get("sanitize-pii"),
                false,
                fieldPath + ".sanitize-pii",
                "METADATA_SANITIZE_PII_BOOLEAN_REQUIRED",
                issues
            );
            if (sanitizePii && !context.piiCapabilityAvailable()) {
                issues.add(issue(
                    "PII_CAPABILITY_REQUIRED",
                    fieldPath + ".sanitize-pii",
                    "sanitize-pii requires the AI Fabric PII capability in the runtime."
                ));
            }
            if (StringUtils.hasText(name)) {
                fields.add(new MetadataFieldConfig(
                    name,
                    dataType,
                    format,
                    description,
                    destinations,
                    priority,
                    required,
                    sanitizePii
                ));
            }
        }
        return fields;
    }

    private void validateSharedTenantMetadata(List<MetadataFieldConfig> metadataFields,
                                              String entityPath,
                                              List<EntityConfigContractIssue> issues) {
        MetadataFieldConfig tenantField = metadataFields.stream()
            .filter(field -> "tenantid".equals(field.name().toLowerCase(Locale.ROOT)))
            .findFirst()
            .orElse(null);
        if (tenantField == null) {
            issues.add(issue(
                "SHARED_VECTOR_TENANT_METADATA_REQUIRED",
                entityPath + ".metadata-fields",
                "Shared vector storage requires a tenantId metadata field."
            ));
            return;
        }
        if (!tenantField.required()) {
            issues.add(issue(
                "SHARED_VECTOR_TENANT_METADATA_MUST_BE_REQUIRED",
                entityPath + ".metadata-fields",
                "Shared vector storage requires tenantId metadata to be marked required."
            ));
        }
        if (!tenantField.destinations().contains(MetadataDestination.VECTOR_METADATA)) {
            issues.add(issue(
                "SHARED_VECTOR_TENANT_METADATA_DESTINATION_REQUIRED",
                entityPath + ".metadata-fields",
                "Shared vector storage requires tenantId to include VECTOR_METADATA."
            ));
        }
    }

    private MarketplaceProvenance parseMarketplaceProvenance(JsonNode entityNode,
                                                             String path,
                                                             List<EntityConfigContractIssue> issues) {
        boolean any = entityNode.has("marketplaceManaged")
            || entityNode.has("marketplacePluginId")
            || entityNode.has("marketplaceInstallId")
            || entityNode.has("marketplacePluginVersion");
        if (!any) {
            return null;
        }

        boolean managed = optionalBoolean(
            entityNode.get("marketplaceManaged"),
            false,
            path + ".marketplaceManaged",
            "MARKETPLACE_MANAGED_BOOLEAN_REQUIRED",
            issues
        );
        String pluginId = optionalText(
            entityNode.get("marketplacePluginId"),
            path + ".marketplacePluginId",
            "MARKETPLACE_PLUGIN_ID_STRING_REQUIRED",
            issues
        );
        String installId = optionalText(
            entityNode.get("marketplaceInstallId"),
            path + ".marketplaceInstallId",
            "MARKETPLACE_INSTALL_ID_STRING_REQUIRED",
            issues
        );
        String pluginVersion = optionalText(
            entityNode.get("marketplacePluginVersion"),
            path + ".marketplacePluginVersion",
            "MARKETPLACE_PLUGIN_VERSION_STRING_REQUIRED",
            issues
        );
        return new MarketplaceProvenance(managed, pluginId, installId, pluginVersion);
    }

    private ObjectNode toJson(EntityConfigContractV04 contract, boolean includePlatformProvenance) {
        ObjectNode root = objectMapper.createObjectNode();
        root.putObject("ai-config").put("vector-dimensions", contract.vectorDimensions());
        ObjectNode entitiesNode = root.putObject("ai-entities");
        contract.entities().forEach((entityType, entity) -> {
            ObjectNode entityNode = entitiesNode.putObject(entityType);
            ObjectNode indexingNode = entityNode.putObject("indexing");
            indexingNode.put("enabled", entity.indexing().enabled());
            indexingNode.put("max-characters", entity.indexing().maxCharacters());

            ObjectNode analysisNode = entityNode.putObject("analysis");
            analysisNode.put("enabled", entity.analysis().enabled());
            ArrayNode afterNode = analysisNode.putArray("after");
            entity.analysis().after().forEach(operation -> afterNode.add(operation.name()));

            ArrayNode searchableNode = entityNode.putArray("searchable-fields");
            entity.searchableFields().forEach(field -> {
                ObjectNode fieldNode = searchableNode.addObject();
                fieldNode.put("name", field.name());
                ArrayNode destinations = fieldNode.putArray("destinations");
                field.destinations().forEach(destination -> destinations.add(destination.name()));
                fieldNode.put("preprocessing", field.preprocessing().name());
                fieldNode.put("max-length", field.maxLength());
                fieldNode.put("priority", field.priority());
                fieldNode.put("required", field.required());
            });

            if (!entity.metadataFields().isEmpty()) {
                ArrayNode metadataNode = entityNode.putArray("metadata-fields");
                entity.metadataFields().forEach(field -> {
                    ObjectNode fieldNode = metadataNode.addObject();
                    fieldNode.put("name", field.name());
                    fieldNode.put("data-type", field.dataType().name());
                    if (field.format() != null) {
                        fieldNode.put("format", field.format());
                    }
                    if (field.description() != null) {
                        fieldNode.put("description", field.description());
                    }
                    ArrayNode destinations = fieldNode.putArray("destinations");
                    field.destinations().forEach(destination -> destinations.add(destination.name()));
                    fieldNode.put("priority", field.priority());
                    fieldNode.put("required", field.required());
                    fieldNode.put("sanitize-pii", field.sanitizePii());
                });
            }

            if (includePlatformProvenance && entity.marketplaceProvenance() != null) {
                MarketplaceProvenance provenance = entity.marketplaceProvenance();
                entityNode.put("marketplaceManaged", provenance.managed());
                putIfText(entityNode, "marketplacePluginId", provenance.pluginId());
                putIfText(entityNode, "marketplaceInstallId", provenance.installId());
                putIfText(entityNode, "marketplacePluginVersion", provenance.pluginVersion());
            }
        });
        return root;
    }

    private <E extends Enum<E>> List<E> enumList(JsonNode node,
                                                 Class<E> enumType,
                                                 String path,
                                                 String requiredCode,
                                                 String unknownCode,
                                                 List<EntityConfigContractIssue> issues) {
        return enumList(node, enumType, path, requiredCode, unknownCode, false, issues);
    }

    private <E extends Enum<E>> List<E> enumList(JsonNode node,
                                                 Class<E> enumType,
                                                 String path,
                                                 String requiredCode,
                                                 String unknownCode,
                                                 boolean allowEmpty,
                                                 List<EntityConfigContractIssue> issues) {
        if (node == null || !node.isArray() || (!allowEmpty && node.isEmpty())) {
            issues.add(issue(requiredCode, path, "A non-empty array is required."));
            return List.of();
        }
        Set<E> values = new HashSet<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode valueNode = node.get(index);
            if (!valueNode.isTextual()) {
                issues.add(issue(unknownCode, path + "[" + index + "]", "Enum values must be strings."));
                continue;
            }
            try {
                values.add(Enum.valueOf(enumType, valueNode.asText().trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                issues.add(issue(
                    unknownCode,
                    path + "[" + index + "]",
                    "Unsupported value: " + valueNode.asText()
                ));
            }
        }
        return values.stream().sorted(Comparator.comparing(Enum::ordinal)).toList();
    }

    private <E extends Enum<E>> E optionalEnum(JsonNode node,
                                               Class<E> enumType,
                                               E defaultValue,
                                               String path,
                                               String code,
                                               List<EntityConfigContractIssue> issues) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return defaultValue;
        }
        if (!node.isTextual()) {
            issues.add(issue(code, path, "Enum value must be a string."));
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumType, node.asText().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            issues.add(issue(code, path, "Unsupported value: " + node.asText()));
            return defaultValue;
        }
    }

    private int priority(JsonNode node,
                         String path,
                         String code,
                         List<EntityConfigContractIssue> issues) {
        return optionalInt(
            node,
            DEFAULT_FIELD_PRIORITY,
            path,
            code,
            value -> value >= 0 && value <= 100,
            "Priority must be an integer from 0 through 100.",
            issues
        );
    }

    private int optionalInt(JsonNode node,
                            int defaultValue,
                            String path,
                            String code,
                            java.util.function.IntPredicate predicate,
                            String message,
                            List<EntityConfigContractIssue> issues) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return defaultValue;
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt() || !predicate.test(node.asInt())) {
            issues.add(issue(code, path, message));
            return defaultValue;
        }
        return node.asInt();
    }

    private boolean optionalBoolean(JsonNode node,
                                    boolean defaultValue,
                                    String path,
                                    String code,
                                    List<EntityConfigContractIssue> issues) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return defaultValue;
        }
        if (!node.isBoolean()) {
            issues.add(issue(code, path, "Value must be a boolean."));
            return defaultValue;
        }
        return node.asBoolean();
    }

    private String requiredText(JsonNode node,
                                String path,
                                String code,
                                List<EntityConfigContractIssue> issues) {
        String value = optionalText(node, path, code, issues);
        if (!StringUtils.hasText(value)) {
            if (node == null || node.isNull() || node.isMissingNode() || node.isTextual()) {
                issues.add(issue(code, path, "A non-blank string is required."));
            }
            return null;
        }
        return value.trim();
    }

    private String optionalText(JsonNode node,
                                String path,
                                String code,
                                List<EntityConfigContractIssue> issues) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (!node.isTextual()) {
            issues.add(issue(code, path, "Value must be a string."));
            return null;
        }
        return node.asText();
    }

    private void validateFormat(MetadataDataType dataType,
                                String format,
                                String path,
                                List<EntityConfigContractIssue> issues) {
        if (!StringUtils.hasText(format)) {
            return;
        }
        try {
            if (dataType == MetadataDataType.DATE) {
                DateTimeFormatter.ofPattern(format);
            } else if (dataType == MetadataDataType.NUMBER) {
                new DecimalFormat(format);
            } else {
                issues.add(issue(
                    "METADATA_FORMAT_TYPE_MISMATCH",
                    path,
                    "format is supported only for DATE and NUMBER metadata."
                ));
            }
        } catch (IllegalArgumentException ex) {
            issues.add(issue(
                "METADATA_FORMAT_INVALID",
                path,
                "Metadata format is invalid for " + dataType.name() + "."
            ));
        }
    }

    private void rejectUnknownProperties(JsonNode node,
                                         Set<String> allowed,
                                         Set<String> removed,
                                         String path,
                                         String unknownCode,
                                         List<EntityConfigContractIssue> issues) {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (allowed.contains(name)) {
                continue;
            }
            if (removed.contains(name)) {
                issues.add(issue(
                    "LEGACY_ENTITY_PROPERTY_REMOVED",
                    path + "." + name,
                    "Property '" + name + "' was removed from AI_ENTITY_CONFIG_V0_4."
                ));
                continue;
            }
            issues.add(issue(
                unknownCode,
                path + "." + name,
                "Unknown property '" + name + "' is not allowed by AI_ENTITY_CONFIG_V0_4."
            ));
        }
    }

    private String entityPath(String entityType) {
        String escaped = entityType
            .replace("\\", "\\\\")
            .replace("'", "\\'");
        return "$.ai-entities['" + escaped + "']";
    }

    private void putIfText(ObjectNode node, String name, String value) {
        if (StringUtils.hasText(value)) {
            node.put(name, value);
        }
    }

    private EntityConfigContractValidation invalid(List<EntityConfigContractIssue> issues) {
        return new EntityConfigContractValidation(null, null, null, issues);
    }

    private EntityConfigContractIssue issue(String code, String path, String message) {
        return new EntityConfigContractIssue(code, path, message);
    }
}
