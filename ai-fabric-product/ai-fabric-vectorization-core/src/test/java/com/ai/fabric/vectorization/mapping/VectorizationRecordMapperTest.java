package com.ai.fabric.vectorization.mapping;

import com.ai.fabric.vectorization.model.VectorizationMappedRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VectorizationRecordMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VectorizationRecordMapper mapper = new VectorizationRecordMapper(objectMapper);

    @Test
    void mapUsesConfiguredFieldMappingsAndStableIdentityFields() throws Exception {
        JsonNode mappingConfig = objectMapper.readTree("""
            {
              "entityMappings": {
                "product": {
                  "recordIdField": "sku",
                  "recordVersionField": "meta.version",
                  "entityFieldMappings": {
                    "name": "title",
                    "description": "details.description"
                  },
                  "metadataFieldMappings": {
                    "category": "taxonomy.primary"
                  }
                }
              }
            }
            """);
        JsonNode sourceRecord = objectMapper.readTree("""
            {
              "sku": "sku-123",
              "title": "Trail Shoe",
              "details": {
                "description": "Designed for rough ground"
              },
              "taxonomy": {
                "primary": "footwear"
              },
              "meta": {
                "version": "2026-04-04T00:00:00Z"
              }
            }
            """);

        VectorizationMappedRecord record = mapper.map("product", mappingConfig, sourceRecord);

        assertThat(record.entityType()).isEqualTo("product");
        assertThat(record.logicalEntityId()).isEqualTo("sku-123");
        assertThat(record.sourceRecordId()).isEqualTo("sku-123");
        assertThat(record.sourceRecordVersion()).isEqualTo("2026-04-04T00:00:00Z");
        assertThat(record.entity())
            .containsEntry("name", "Trail Shoe")
            .containsEntry("description", "Designed for rough ground");
        assertThat(record.metadata()).containsEntry("category", "footwear");
    }

    @Test
    void mapFallsBackToFullSourceObjectWhenEntityFieldMappingsAreAbsent() throws Exception {
        JsonNode mappingConfig = objectMapper.readTree("""
            {
              "entityMappings": {
                "policy": {
                  "recordIdField": "id"
                }
              }
            }
            """);
        JsonNode sourceRecord = objectMapper.readTree("""
            {
              "id": "policy-7",
              "title": "Refund window",
              "days": 30
            }
            """);

        VectorizationMappedRecord record = mapper.map("policy", mappingConfig, sourceRecord);

        assertThat(record.logicalEntityId()).isEqualTo("policy-7");
        assertThat(record.entity())
            .containsEntry("id", "policy-7")
            .containsEntry("title", "Refund window")
            .containsEntry("days", 30);
        assertThat(record.metadata()).isEmpty();
    }
}
