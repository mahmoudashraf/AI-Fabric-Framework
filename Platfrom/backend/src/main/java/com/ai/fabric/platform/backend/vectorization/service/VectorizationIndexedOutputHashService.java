package com.ai.fabric.platform.backend.vectorization.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractService;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractV04;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractV04.EntityProjectionConfig;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractV04.MetadataFieldConfig;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractV04.SearchableFieldConfig;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigValidationContext;
import com.ai.fabric.platform.backend.deployment.service.ManagedDeploymentProfileCatalog;
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
import java.util.List;

@Component
public class VectorizationIndexedOutputHashService {

    private final ObjectMapper objectMapper;
    private final EntityConfigContractService entityConfigContractService;

    public VectorizationIndexedOutputHashService(ObjectMapper objectMapper,
                                                 EntityConfigContractService entityConfigContractService) {
        this.objectMapper = objectMapper;
        this.entityConfigContractService = entityConfigContractService;
    }

    public String compute(DeploymentVersionEntity version) {
        if (version == null) {
            return null;
        }
        if (!EntityConfigContractService.CONTRACT_VERSION_V04.equals(
            version.getEntityConfigContractVersion()
        )) {
            throw new IllegalStateException(
                "Indexed output hash requires "
                    + EntityConfigContractService.CONTRACT_VERSION_V04
                    + "; found "
                    + display(version.getEntityConfigContractVersion())
                    + "."
            );
        }
        try {
            JsonNode providerConfig = objectMapper.readTree(version.getProviderConfigJson());
            EntityConfigContractV04 contract = entityConfigContractService.requireValid(
                objectMapper.readTree(version.getEntityConfigJson()),
                new EntityConfigValidationContext(
                    false,
                    ManagedDeploymentProfileCatalog.sharedVectorStorageRequested(providerConfig)
                )
            ).contract();

            ObjectNode canonical = objectMapper.createObjectNode();
            canonical.put("vectorDimensions", contract.vectorDimensions());
            canonical.set("entities", canonicalEntities(contract));
            canonical.set(
                "embeddingProvider",
                embeddingProviderProjection(providerConfig, contract.vectorDimensions())
            );
            return sha256(objectMapper.writeValueAsString(canonical));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute indexed output hash: " + ex.getMessage(), ex);
        }
    }

    private ArrayNode canonicalEntities(EntityConfigContractV04 contract) {
        ArrayNode entities = objectMapper.createArrayNode();
        contract.entities().entrySet().stream()
            .sorted(java.util.Map.Entry.comparingByKey())
            .forEach(entry -> {
                ObjectNode entity = entities.addObject();
                entity.put("entityType", entry.getKey());
                EntityProjectionConfig projection = entry.getValue();
                entity.set("indexing", objectMapper.valueToTree(projection.indexing()));

                ObjectNode analysis = entity.putObject("analysis");
                analysis.put("enabled", projection.analysis().enabled());
                ArrayNode after = analysis.putArray("after");
                projection.analysis().after().stream()
                    .map(Enum::name)
                    .sorted()
                    .forEach(after::add);

                ArrayNode searchable = entity.putArray("searchableFields");
                projection.searchableFields().stream()
                    .sorted(Comparator.comparing(
                        SearchableFieldConfig::name,
                        String.CASE_INSENSITIVE_ORDER
                    ).thenComparing(SearchableFieldConfig::name))
                    .forEach(field -> searchable.add(canonicalSearchableField(field)));

                ArrayNode metadata = entity.putArray("metadataFields");
                projection.metadataFields().stream()
                    .sorted(Comparator.comparing(
                        MetadataFieldConfig::name,
                        String.CASE_INSENSITIVE_ORDER
                    ).thenComparing(MetadataFieldConfig::name))
                    .forEach(field -> metadata.add(canonicalMetadataField(field)));
            });
        return entities;
    }

    private ObjectNode canonicalSearchableField(SearchableFieldConfig field) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", field.name());
        ArrayNode destinations = node.putArray("destinations");
        field.destinations().stream()
            .map(Enum::name)
            .sorted()
            .forEach(destinations::add);
        node.put("preprocessing", field.preprocessing().name());
        node.put("maxLength", field.maxLength());
        node.put("priority", field.priority());
        node.put("required", field.required());
        return node;
    }

    private ObjectNode canonicalMetadataField(MetadataFieldConfig field) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", field.name());
        node.put("dataType", field.dataType().name());
        putNullable(node, "format", field.format());
        putNullable(node, "description", field.description());
        ArrayNode destinations = node.putArray("destinations");
        field.destinations().stream()
            .map(Enum::name)
            .sorted()
            .forEach(destinations::add);
        node.put("priority", field.priority());
        node.put("required", field.required());
        node.put("sanitizePii", field.sanitizePii());
        return node;
    }

    private ObjectNode embeddingProviderProjection(JsonNode providerConfig, int vectorDimensions) {
        ObjectNode projection = objectMapper.createObjectNode();
        String provider = ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerConfig);
        projection.put("provider", provider);
        putIfText(
            projection,
            "endpointProfile",
            ManagedDeploymentProfileCatalog.embeddingEndpointProfile(providerConfig)
        );
        putIfText(
            projection,
            "managedServiceRef",
            ManagedDeploymentProfileCatalog.embeddingManagedServiceRef(providerConfig)
        );
        putIfText(
            projection,
            "serviceMode",
            ManagedDeploymentProfileCatalog.embeddingServiceMode(providerConfig)
        );

        switch (provider) {
            case ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_OPENAI -> {
                projection.put(
                    "model",
                    ManagedDeploymentProfileCatalog.openAiEmbeddingModel(providerConfig)
                );
                projection.put(
                    "dimensions",
                    ManagedDeploymentProfileCatalog.effectiveOpenAiEmbeddingDimensions(
                        providerConfig,
                        vectorDimensions
                    )
                );
                putIfText(
                    projection,
                    "baseUrl",
                    firstNonBlank(
                        ManagedDeploymentProfileCatalog.embeddingBaseUrl(providerConfig),
                        ManagedDeploymentProfileCatalog.openAiEmbeddingBaseUrl(providerConfig),
                        ManagedDeploymentProfileCatalog.openAiBaseUrl(providerConfig)
                    )
                );
            }
            case ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_AZURE -> {
                putIfText(
                    projection,
                    "baseUrl",
                    firstNonBlank(
                        ManagedDeploymentProfileCatalog.embeddingBaseUrl(providerConfig),
                        ManagedDeploymentProfileCatalog.azureEmbeddingEndpoint(providerConfig),
                        ManagedDeploymentProfileCatalog.azureEndpoint(providerConfig)
                    )
                );
                putIfText(
                    projection,
                    "deployment",
                    firstNonBlank(
                        ManagedDeploymentProfileCatalog.embeddingDeploymentName(providerConfig),
                        ManagedDeploymentProfileCatalog.azureEmbeddingDeploymentName(providerConfig),
                        ManagedDeploymentProfileCatalog.azureDeploymentName(providerConfig)
                    )
                );
                putIfText(
                    projection,
                    "apiVersion",
                    firstNonBlank(
                        ManagedDeploymentProfileCatalog.embeddingApiVersion(providerConfig),
                        ManagedDeploymentProfileCatalog.azureEmbeddingApiVersion(providerConfig),
                        ManagedDeploymentProfileCatalog.azureApiVersion(providerConfig)
                    )
                );
            }
            case ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_COHERE -> {
                projection.put(
                    "model",
                    ManagedDeploymentProfileCatalog.cohereEmbeddingModel(providerConfig)
                );
                putIfText(
                    projection,
                    "baseUrl",
                    firstNonBlank(
                        ManagedDeploymentProfileCatalog.embeddingBaseUrl(providerConfig),
                        ManagedDeploymentProfileCatalog.cohereBaseUrl(providerConfig)
                    )
                );
            }
            case ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_GEMINI -> {
                projection.put(
                    "model",
                    ManagedDeploymentProfileCatalog.geminiEmbeddingModel(providerConfig)
                );
                putIfText(
                    projection,
                    "baseUrl",
                    firstNonBlank(
                        ManagedDeploymentProfileCatalog.embeddingBaseUrl(providerConfig),
                        ManagedDeploymentProfileCatalog.geminiBaseUrl(providerConfig)
                    )
                );
            }
            case ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_ONNX -> {
                projection.put("modelAlias", ManagedDeploymentProfileCatalog.onnxModelAlias(providerConfig));
                putIfText(
                    projection,
                    "modelPath",
                    ManagedDeploymentProfileCatalog.onnxModelPath(providerConfig)
                );
                putIfText(
                    projection,
                    "tokenizerPath",
                    ManagedDeploymentProfileCatalog.onnxTokenizerPath(providerConfig)
                );
                projection.put(
                    "maxSequenceLength",
                    ManagedDeploymentProfileCatalog.onnxMaxSequenceLength(providerConfig)
                );
                projection.put("useGpu", ManagedDeploymentProfileCatalog.onnxUseGpu(providerConfig));
            }
            default -> throw new IllegalStateException("Unsupported embedding provider: " + provider);
        }
        return projection;
    }

    private void putIfText(ObjectNode node, String field, String value) {
        if (StringUtils.hasText(value)) {
            node.put(field, value.trim());
        }
    }

    private void putNullable(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String display(String value) {
        return StringUtils.hasText(value) ? "'" + value + "'" : "<missing>";
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash indexed output signature.", ex);
        }
    }
}
