package com.ai.fabric.platform.backend.deployment.entityconfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntityConfigV03ToV04MigratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityConfigContractService contractService = new EntityConfigContractService(objectMapper);
    private final EntityConfigV03ToV04Migrator migrator =
        new EntityConfigV03ToV04Migrator(objectMapper, contractService);

    @Test
    void convertsLegacyProjectionWithExplicitWarnings() throws Exception {
        EntityConfigMigrationResult result = migrator.preview(json(
            """
                {
                  "ai-config": {
                    "vector-dimensions": 512
                  },
                  "ai-entities": {
                    "product": {
                      "features": ["embedding", "search"],
                      "auto-process": false,
                      "indexable": true,
                      "searchable-fields": [
                        {
                          "name": "name",
                          "weight": 2.0
                        },
                        {
                          "name": "description",
                          "include-in-rag": false
                        }
                      ],
                      "embeddable-fields": [
                        {
                          "name": "description"
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "sku",
                          "type": "string"
                        }
                      ]
                    }
                  }
                }
                """
        ));

        assertThat(result.report().blocked()).isFalse();
        assertThat(result.report().migrationRequired()).isTrue();
        assertThat(result.report().sourceContractVersion()).isEqualTo("AI_ENTITY_CONFIG_V0_3");
        assertThat(result.report().targetContractVersion()).isEqualTo("AI_ENTITY_CONFIG_V0_4");
        assertThat(result.report().warnings())
            .extracting(EntityConfigMigrationMessage::code)
            .contains("WEIGHT_REMOVED", "EMBEDDABLE_FIELDS_REQUIRE_MANUAL_REVIEW");
        assertThat(result.report().droppedKeys())
            .contains(
                "$.ai-entities['product'].searchable-fields[0].weight",
                "$.ai-entities['product'].embeddable-fields",
                "$.ai-entities['product'].metadata-fields[0].type"
            );

        JsonNode product = result.migratedConfig().path("ai-entities").path("product");
        assertThat(product.path("indexing").path("enabled").asBoolean()).isTrue();
        assertThat(product.path("analysis").path("enabled").asBoolean()).isFalse();
        assertThat(product.path("searchable-fields").get(0).path("priority").asInt()).isEqualTo(100);
        assertThat(product.path("searchable-fields").get(1).path("priority").asInt()).isEqualTo(99);
        assertThat(product.path("metadata-fields").get(0).path("data-type").asText()).isEqualTo("STRING");
        assertThat(contractService.validate(
            result.migratedConfig(),
            EntityConfigValidationContext.standard()
        ).valid()).isTrue();
    }

    @Test
    void repeatedMigrationIsNoOp() throws Exception {
        EntityConfigMigrationResult first = migrator.preview(json(
            """
                {
                  "ai-config": {
                    "vector-dimensions": 512
                  },
                  "ai-entities": {
                    "document": {
                      "indexable": true,
                      "searchable-fields": [
                        {
                          "name": "content"
                        }
                      ]
                    }
                  }
                }
                """
        ));
        EntityConfigMigrationResult second = migrator.preview(first.migratedConfig());

        assertThat(first.report().blocked()).isFalse();
        assertThat(second.report().blocked()).isFalse();
        assertThat(second.report().migrationRequired()).isFalse();
        assertThat(second.report().sourceContractVersion()).isEqualTo("AI_ENTITY_CONFIG_V0_4");
        assertThat(second.report().beforeHash()).isEqualTo(second.report().afterHash());
    }

    @Test
    void blocksUnknownMetadataType() throws Exception {
        EntityConfigMigrationResult result = migrator.preview(json(
            """
                {
                  "ai-config": {
                    "vector-dimensions": 512
                  },
                  "ai-entities": {
                    "document": {
                      "searchable-fields": [
                        {
                          "name": "content"
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "classification",
                          "type": "custom-value"
                        }
                      ]
                    }
                  }
                }
                """
        ));

        assertThat(result.report().blocked()).isTrue();
        assertThat(result.report().blockers())
            .extracting(EntityConfigMigrationMessage::code)
            .contains("UNKNOWN_METADATA_TYPE");
    }

    @Test
    void blocksLegacyEntityWithoutSemanticProjection() throws Exception {
        EntityConfigMigrationResult result = migrator.preview(json(
            """
                {
                  "ai-config": {
                    "vector-dimensions": 512
                  },
                  "ai-entities": {
                    "document": {
                      "indexable": true,
                      "fields": []
                    }
                  }
                }
                """
        ));

        assertThat(result.report().blocked()).isTrue();
        assertThat(result.report().blockers())
            .extracting(EntityConfigMigrationMessage::code)
            .contains(
                "SEARCHABLE_FIELDS_MIGRATION_BLOCKED",
                "UNSUPPORTED_SIMPLIFIED_FIELDS",
                "V04_SEARCHABLE_FIELDS_REQUIRED",
                "V04_SEMANTIC_SEARCH_FIELD_REQUIRED"
            );
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }
}
