package com.ai.fabric.platform.backend.deployment.entityconfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntityConfigContractServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityConfigContractService service = new EntityConfigContractService(objectMapper);

    @Test
    void validatesAndNormalizesCompleteV04Contract() throws Exception {
        EntityConfigContractValidation validation = service.validate(
            json(
                """
                    {
                      "ai-entities": {
                        "document": {
                          "indexing": {
                            "max-characters": 7000,
                            "enabled": true
                          },
                          "analysis": {
                            "after": [],
                            "enabled": false
                          },
                          "searchable-fields": [
                            {
                              "required": true,
                              "priority": 100,
                              "preprocessing": "CLEAN",
                              "destinations": ["RAG_CONTEXT", "SEMANTIC_SEARCH"],
                              "name": "content"
                            }
                          ],
                          "metadata-fields": [
                            {
                              "destinations": ["API_RESPONSE", "VECTOR_METADATA"],
                              "data-type": "ID",
                              "name": "sourceId"
                            }
                          ]
                        }
                      },
                      "ai-config": {
                        "vector-dimensions": 512
                      }
                    }
                    """
            ),
            EntityConfigValidationContext.standard()
        );

        assertThat(validation.valid()).isTrue();
        assertThat(validation.contract().vectorDimensions()).isEqualTo(512);
        JsonNode normalized = validation.normalizedPlatformConfig();
        assertThat(normalized.path("ai-entities").path("document").path("searchable-fields").get(0)
            .path("max-length").asInt()).isEqualTo(-1);
        assertThat(normalized.path("ai-entities").path("document").path("metadata-fields").get(0)
            .path("priority").asInt()).isEqualTo(50);
        assertThat(normalized.path("ai-entities").path("document").path("searchable-fields").get(0)
            .path("destinations").get(0).asText()).isEqualTo("SEMANTIC_SEARCH");
    }

    @Test
    void rejectsEveryLegacyScopeAndUnknownProperty() throws Exception {
        EntityConfigContractValidation validation = service.validate(
            json(
                """
                    {
                      "ai-config": {
                        "vector-dimensions": 512
                      },
                      "ai-entities": {
                        "document": {
                          "indexable": true,
                          "fields": [],
                          "indexing": {
                            "enabled": true
                          },
                          "searchable-fields": [
                            {
                              "name": "content",
                              "destinations": ["SEMANTIC_SEARCH"],
                              "weight": 2
                            }
                          ],
                          "metadata-fields": [
                            {
                              "name": "sourceId",
                              "type": "string",
                              "destinations": ["VECTOR_METADATA"]
                            }
                          ]
                        }
                      }
                    }
                    """
            ),
            EntityConfigValidationContext.standard()
        );

        assertThat(validation.valid()).isFalse();
        assertThat(validation.issues())
            .extracting(EntityConfigContractIssue::code)
            .contains(
                "LEGACY_ENTITY_PROPERTY_REMOVED",
                "ENTITY_PROPERTY_UNKNOWN"
            );
        assertThat(validation.issues())
            .extracting(EntityConfigContractIssue::path)
            .contains(
                "$.ai-entities['document'].indexable",
                "$.ai-entities['document'].fields",
                "$.ai-entities['document'].searchable-fields[0].weight",
                "$.ai-entities['document'].metadata-fields[0].type"
            );
    }

    @Test
    void stripsKnownMarketplaceProvenanceFromRuntimeProjection() throws Exception {
        EntityConfigContractValidation validation = service.requireValid(
            json(
                """
                    {
                      "ai-config": {
                        "vector-dimensions": 512
                      },
                      "ai-entities": {
                        "document": {
                          "indexing": {
                            "enabled": true
                          },
                          "searchable-fields": [
                            {
                              "name": "content",
                              "destinations": ["SEMANTIC_SEARCH"]
                            }
                          ],
                          "marketplaceManaged": true,
                          "marketplacePluginId": "plugin-1",
                          "marketplaceInstallId": "install-1",
                          "marketplacePluginVersion": "1.0.0"
                        }
                      }
                    }
                    """
            ),
            EntityConfigValidationContext.standard()
        );

        JsonNode platformEntity = validation.normalizedPlatformConfig()
            .path("ai-entities")
            .path("document");
        JsonNode runtimeEntity = validation.runtimeConfig()
            .path("ai-entities")
            .path("document");
        assertThat(platformEntity.path("marketplacePluginId").asText()).isEqualTo("plugin-1");
        assertThat(runtimeEntity.has("marketplaceManaged")).isFalse();
        assertThat(runtimeEntity.has("marketplacePluginId")).isFalse();
    }

    @Test
    void requiresTrustedTenantMetadataForSharedVectorStorage() throws Exception {
        EntityConfigContractValidation validation = service.validate(
            json(validDocumentConfig()),
            new EntityConfigValidationContext(false, true)
        );

        assertThat(validation.valid()).isFalse();
        assertThat(validation.issues())
            .extracting(EntityConfigContractIssue::code)
            .containsExactly("SHARED_VECTOR_TENANT_METADATA_REQUIRED");
    }

    @Test
    void rejectsPiiProjectionWhenRuntimeCapabilityIsUnavailable() throws Exception {
        JsonNode candidate = json(validDocumentConfig());
        ((com.fasterxml.jackson.databind.node.ObjectNode) candidate
            .path("ai-entities")
            .path("document")
            .path("searchable-fields")
            .get(0))
            .put("preprocessing", "SANITIZE");

        EntityConfigContractValidation validation = service.validate(
            candidate,
            EntityConfigValidationContext.standard()
        );

        assertThat(validation.valid()).isFalse();
        assertThat(validation.issues())
            .extracting(EntityConfigContractIssue::code)
            .containsExactly("PII_CAPABILITY_REQUIRED");
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }

    private String validDocumentConfig() {
        return """
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
                    "enabled": false,
                    "after": []
                  },
                  "searchable-fields": [
                    {
                      "name": "content",
                      "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                      "preprocessing": "CLEAN",
                      "priority": 100,
                      "required": true
                    }
                  ]
                }
              }
            }
            """;
    }
}
