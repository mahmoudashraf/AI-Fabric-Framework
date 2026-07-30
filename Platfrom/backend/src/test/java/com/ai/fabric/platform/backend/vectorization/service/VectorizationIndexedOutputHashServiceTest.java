package com.ai.fabric.platform.backend.vectorization.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorizationIndexedOutputHashServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VectorizationIndexedOutputHashService service =
        new VectorizationIndexedOutputHashService(
            objectMapper,
            new EntityConfigContractService(objectMapper)
        );

    @Test
    void objectEnumAndFieldOrderingDoNotChangeHash() throws Exception {
        DeploymentVersionEntity first = version();
        DeploymentVersionEntity second = version();
        ObjectNode reordered = entityConfig(second);
        ObjectNode document = (ObjectNode) reordered.path("ai-entities").path("document");
        reverse((ArrayNode) document.path("searchable-fields"));
        reverse((ArrayNode) document.path("metadata-fields"));
        reverse((ArrayNode) document.path("searchable-fields").get(0).path("destinations"));
        second.setEntityConfigJson(objectMapper.writeValueAsString(reordered));
        second.setProviderConfigJson(
            """
                {
                  "displayName": "Different display label",
                  "openaiEmbeddingDimensions": 512,
                  "openaiEmbeddingModel": "text-embedding-3-small",
                  "embeddingProvider": "openai"
                }
                """
        );

        assertThat(service.compute(second)).isEqualTo(service.compute(first));
    }

    @Test
    void everySupportedV04ProjectionChangeCausesDrift() throws Exception {
        assertEntityChange(
            "entity type",
            root -> {
                ObjectNode entities = (ObjectNode) root.path("ai-entities");
                entities.set("article", entities.remove("document"));
            }
        );
        assertEntityChange(
            "vector dimensions",
            root -> ((ObjectNode) root.path("ai-config")).put("vector-dimensions", 384)
        );
        assertEntityChange(
            "indexing max characters",
            root -> indexing(root).put("max-characters", 4096)
        );
        assertEntityChange(
            "analysis policy",
            root -> {
                ArrayNode after = (ArrayNode) document(root).path("analysis").path("after");
                after.removeAll().add("UPDATE");
            }
        );
        assertEntityChange(
            "search destinations",
            root -> searchable(root).set(
                "destinations",
                objectMapper.createArrayNode().add("SEMANTIC_SEARCH")
            )
        );
        assertEntityChange(
            "search preprocessing",
            root -> searchable(root).put("preprocessing", "CLEAN")
        );
        assertEntityChange(
            "search max length",
            root -> searchable(root).put("max-length", 400)
        );
        assertEntityChange(
            "search priority",
            root -> searchable(root).put("priority", 75)
        );
        assertEntityChange(
            "search required",
            root -> searchable(root).put("required", true)
        );
        assertEntityChange(
            "metadata data type",
            root -> {
                ObjectNode metadata = metadata(root);
                metadata.put("data-type", "AUTO");
                metadata.remove("format");
            }
        );
        assertEntityChange(
            "metadata format",
            root -> metadata(root).put("format", "#.000")
        );
        assertEntityChange(
            "metadata description",
            root -> metadata(root).put("description", "Normalized relevance score")
        );
        assertEntityChange(
            "metadata destinations",
            root -> metadata(root).set(
                "destinations",
                objectMapper.createArrayNode().add("VECTOR_METADATA").add("LLM_CONTEXT")
            )
        );
        assertEntityChange(
            "metadata priority",
            root -> metadata(root).put("priority", 70)
        );
        assertEntityChange(
            "metadata required",
            root -> metadata(root).put("required", true)
        );
        assertProviderChange(
            "embedding model",
            provider -> provider.put("openaiEmbeddingModel", "text-embedding-3-large")
        );
        assertProviderChange(
            "embedding dimensions",
            provider -> provider.put("openaiEmbeddingDimensions", 384)
        );
        assertProviderChange(
            "embedding endpoint",
            provider -> provider.put("openaiEmbeddingBaseUrl", "https://embeddings.example.test/v1")
        );
    }

    @Test
    void displayOnlyPlatformMetadataDoesNotCauseDrift() throws Exception {
        DeploymentVersionEntity first = version();
        DeploymentVersionEntity second = version();
        ObjectNode entityConfig = entityConfig(second);
        ObjectNode document = document(entityConfig);
        document.put("marketplaceManaged", true);
        document.put("marketplacePluginId", "plugin-new-label");
        document.put("marketplaceInstallId", "install-new-label");
        document.put("marketplacePluginVersion", "9.9.9");
        second.setEntityConfigJson(objectMapper.writeValueAsString(entityConfig));
        ObjectNode provider = providerConfig(second);
        provider.put("displayName", "Owner-facing label");
        provider.put("operatorNotes", "Cosmetic Platform note");
        second.setProviderConfigJson(objectMapper.writeValueAsString(provider));

        assertThat(service.compute(second)).isEqualTo(service.compute(first));
    }

    @Test
    void invalidOrLegacyBehaviorIsRejectedInsteadOfSilentlyHashed() throws Exception {
        DeploymentVersionEntity disabled = version();
        ObjectNode disabledConfig = entityConfig(disabled);
        indexing(disabledConfig).put("enabled", false);
        disabled.setEntityConfigJson(objectMapper.writeValueAsString(disabledConfig));

        assertThatThrownBy(() -> service.compute(disabled))
            .hasMessageContaining("INDEXING_NOT_EXPLICITLY_ENABLED");

        DeploymentVersionEntity legacyWeight = version();
        ObjectNode legacyWeightConfig = entityConfig(legacyWeight);
        searchable(legacyWeightConfig).put("weight", 2.0);
        legacyWeight.setEntityConfigJson(objectMapper.writeValueAsString(legacyWeightConfig));

        assertThatThrownBy(() -> service.compute(legacyWeight))
            .hasMessageContaining("LEGACY_ENTITY_PROPERTY_REMOVED")
            .hasMessageContaining("weight");

        DeploymentVersionEntity legacyType = version();
        ObjectNode legacyTypeConfig = entityConfig(legacyType);
        ObjectNode metadata = metadata(legacyTypeConfig);
        metadata.remove("data-type");
        metadata.put("type", "number");
        legacyType.setEntityConfigJson(objectMapper.writeValueAsString(legacyTypeConfig));

        assertThatThrownBy(() -> service.compute(legacyType))
            .hasMessageContaining("LEGACY_ENTITY_PROPERTY_REMOVED")
            .hasMessageContaining("type");
    }

    @Test
    void piiProjectionCannotBeHashedUntilRuntimeCapabilityIsAvailable() throws Exception {
        DeploymentVersionEntity version = version();
        ObjectNode entityConfig = entityConfig(version);
        metadata(entityConfig).put("sanitize-pii", true);
        version.setEntityConfigJson(objectMapper.writeValueAsString(entityConfig));

        assertThatThrownBy(() -> service.compute(version))
            .hasMessageContaining("PII_CAPABILITY_REQUIRED");
    }

    private void assertEntityChange(String label, Consumer<ObjectNode> change) throws Exception {
        DeploymentVersionEntity baseline = version();
        DeploymentVersionEntity changed = version();
        ObjectNode config = entityConfig(changed);
        change.accept(config);
        changed.setEntityConfigJson(objectMapper.writeValueAsString(config));

        assertThat(service.compute(changed))
            .as(label)
            .isNotEqualTo(service.compute(baseline));
    }

    private void assertProviderChange(String label, Consumer<ObjectNode> change) throws Exception {
        DeploymentVersionEntity baseline = version();
        DeploymentVersionEntity changed = version();
        ObjectNode provider = providerConfig(changed);
        change.accept(provider);
        changed.setProviderConfigJson(objectMapper.writeValueAsString(provider));

        assertThat(service.compute(changed))
            .as(label)
            .isNotEqualTo(service.compute(baseline));
    }

    private ObjectNode entityConfig(DeploymentVersionEntity version) throws Exception {
        return (ObjectNode) objectMapper.readTree(version.getEntityConfigJson());
    }

    private ObjectNode providerConfig(DeploymentVersionEntity version) throws Exception {
        return (ObjectNode) objectMapper.readTree(version.getProviderConfigJson());
    }

    private ObjectNode document(ObjectNode root) {
        return (ObjectNode) root.path("ai-entities").path("document");
    }

    private ObjectNode indexing(ObjectNode root) {
        return (ObjectNode) document(root).path("indexing");
    }

    private ObjectNode searchable(ObjectNode root) {
        return (ObjectNode) document(root).path("searchable-fields").get(0);
    }

    private ObjectNode metadata(ObjectNode root) {
        return (ObjectNode) document(root).path("metadata-fields").get(0);
    }

    private void reverse(ArrayNode array) {
        for (int left = 0, right = array.size() - 1; left < right; left++, right--) {
            var leftValue = array.get(left);
            var rightValue = array.get(right);
            array.set(left, rightValue);
            array.set(right, leftValue);
        }
    }

    private DeploymentVersionEntity version() {
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setEntityConfigContractVersion(EntityConfigContractService.CONTRACT_VERSION_V04);
        version.setEntityConfigJson(
            """
                {
                  "ai-config": {
                    "vector-dimensions": 512
                  },
                  "ai-entities": {
                    "document": {
                      "indexing": {
                        "enabled": true,
                        "max-characters": 8000
                      },
                      "analysis": {
                        "enabled": true,
                        "after": ["CREATE"]
                      },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "NORMALIZE",
                          "max-length": -1,
                          "priority": 50,
                          "required": false
                        },
                        {
                          "name": "title",
                          "destinations": ["SEMANTIC_SEARCH"],
                          "preprocessing": "CLEAN",
                          "max-length": 200,
                          "priority": 40,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "score",
                          "data-type": "NUMBER",
                          "format": "#.##",
                          "description": "Relevance score",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 50,
                          "required": false,
                          "sanitize-pii": false
                        },
                        {
                          "name": "source",
                          "data-type": "STRING",
                          "description": "Source system",
                          "destinations": ["VECTOR_METADATA", "API_RESPONSE"],
                          "priority": 40,
                          "required": true,
                          "sanitize-pii": false
                        }
                      ]
                    }
                  }
                }
                """
        );
        version.setProviderConfigJson(
            """
                {
                  "embeddingProvider": "openai",
                  "openaiEmbeddingModel": "text-embedding-3-small",
                  "openaiEmbeddingDimensions": 512,
                  "displayName": "Baseline"
                }
                """
        );
        return version;
    }
}
