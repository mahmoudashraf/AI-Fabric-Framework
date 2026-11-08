package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class GuidedDataPluginSeedsTest {
    @ParameterizedTest
    @ValueSource(strings = {"HTTP_JSON", "S3_COMPATIBLE_OBJECT_STORAGE", "SQL_QUERY"})
    void guidedSeedsUseExistingDataContractWithoutRootId(String kind) throws Exception {
        var mapper = new ObjectMapper();
        var manifest = seed(mapper, kind);
        var plugin = new MarketplacePluginEntity();
        plugin.setId("opaque-private-id");
        plugin.setPluginType("DATA");
        var version = new MarketplacePluginVersionEntity();
        version.setVersion("1.0.0");
        version.setManifestJson(manifest.toString());
        var parsed = new MarketplaceManifestService(mapper).parseAndValidate(plugin, version);
        assertThat(manifest.has("id")).isFalse();
        assertThat(parsed.datasets()).hasSize(1);
        assertThat(parsed.datasets().get(0).connectorType()).isEqualTo(kind);
        assertThat(parsed.contributions().knowledgeSourceIds()).containsExactly("source-records");
        if ("HTTP_JSON".equals(kind)) assertThat(parsed.installForm().get(2).type()).isEqualTo("secretRef");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SQL_QUERY", "FILE_FOLDER", "PACKAGED_SEED", "MOUNTED_FOLDER", "STATIC_S3"})
    void privateVersionsCannotAcquirePlatformSqlOrFilesystemAuthority(String kind) throws Exception {
        var mapper = new ObjectMapper();
        var manifest = unsafeSeed(mapper, kind);
        var plugin = new MarketplacePluginEntity(); plugin.setId("private"); plugin.setPluginType("DATA");
        plugin.setVisibility("CUSTOMER_PRIVATE"); plugin.setOwnerCustomerId("owner");
        var version = new MarketplacePluginVersionEntity(); version.setManifestJson(manifest.toString());
        assertThat(MarketplacePrivateDataContract.issues(manifest)).isNotEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new MarketplaceManifestService(mapper).parseAndValidate(plugin, version))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTP_JSON", "S3_COMPATIBLE_OBJECT_STORAGE"})
    void privateKnowledgeHandlesMustBeDerivedFromTheirDeclaredDataset(String kind) throws Exception {
        var mapper = new ObjectMapper();
        var manifest = seed(mapper, kind);
        ((ObjectNode) manifest.at("/contributions/knowledgeSources/0")).put("handleRef", "unrelated/source/handle");
        assertThat(MarketplacePrivateDataContract.issues(manifest)).anySatisfy(issue -> {
            assertThat(issue.path()).endsWith("/handleRef");
            assertThat(issue.code()).isEqualTo("PRIVATE_DATASET_HANDLE_DERIVED");
        });
        var plugin = new MarketplacePluginEntity(); plugin.setId("private"); plugin.setPluginType("DATA");
        plugin.setVisibility("CUSTOMER_PRIVATE"); plugin.setOwnerCustomerId("owner");
        var version = new MarketplacePluginVersionEntity(); version.setManifestJson(manifest.toString());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new MarketplaceManifestService(mapper).parseAndValidate(plugin, version))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("handleRef");
    }

    static ObjectNode unsafeSeed(ObjectMapper mapper, String kind) throws Exception {
        var manifest = seed(mapper, "SQL_QUERY".equals(kind) ? kind : "S3_COMPATIBLE_OBJECT_STORAGE");
        ObjectNode dataset = (ObjectNode) manifest.at("/contributions/datasets/0");
        if ("FILE_FOLDER".equals(kind)) {
            dataset.put("ingestionMode", "EXTERNAL_SYNC_FOLDER"); dataset.remove(java.util.List.of("sourceConnector", "documentPolicy"));
            dataset.putObject("syncConnector").put("connectorType", "FILE_FOLDER").put("folderRef", "file:/etc/**");
        } else if ("PACKAGED_SEED".equals(kind)) {
            dataset.put("ingestionMode", kind).put("seedDatasetRef", "classpath:private.jsonl");
            dataset.remove(java.util.List.of("sourceConnector", "documentPolicy"));
        } else if ("MOUNTED_FOLDER".equals(kind)) {
            ((ObjectNode) dataset.path("sourceConnector")).put("connectorType", "MOUNTED_FOLDER").put("folderPathField", "folder");
        } else if ("STATIC_S3".equals(kind)) {
            ((ObjectNode) dataset.path("sourceConnector")).put("storageOwnership", "CUSTOMER_OWNED");
        }
        return manifest;
    }

    // Mirrors creatorDocument.newDefinition, with the required name/read mapping completed.
    static ObjectNode seed(ObjectMapper mapper, String kind) throws Exception {
        ObjectNode root = (ObjectNode) mapper.readTree("""
            {"schemaVersion":1,"pluginId":"customer-records","version":"1.0.0","pluginType":"DATA",
             "displayName":"Customer records","description":"Customer records for search",
             "compatibility":{"requiredCapabilities":["knowledgeSources"]},"pricing":{"pricingModel":"FREE"},
             "permissions":{"contributesKnowledgeSources":true,"requiresSharedDatasetAccess":false},
             "installForm":[],"contributions":{"entityConfig":{"ai-entities":{"record":{
               "indexing":{"enabled":true,"max-characters":8000},
               "searchable-fields":[{"name":"content","destinations":["SEMANTIC_SEARCH","RAG_CONTEXT"],"required":true,"priority":100}],
               "metadata-fields":[]}}},"datasets":[],"knowledgeSources":[{
               "sourceKey":"source-records","sourceType":"deployment-private-vector","adapterType":"deployment-private-vector",
               "datasetRef":"source-records","entityType":"record","attributionLabel":"Customer records",
               "authModes":["PRIVATE_RUNTIME_BACKEND_MEDIATED"]}]}}
            """);
        var fields = (com.fasterxml.jackson.databind.node.ArrayNode) root.at("/contributions/entityConfig/ai-entities/record/metadata-fields");
        for (String field : java.util.List.of("tenantId", "customerId", "deploymentId", "datasetId", "knowledgeSourceHandleRef")) {
            var metadata = fields.addObject().put("name", field).put("data-type", "ID").put("required", true);
            metadata.putArray("destinations").add("VECTOR_METADATA");
        }
        String dataset = switch (kind) {
            case "HTTP_JSON" -> """
                {"ingestionMode":"EXTERNAL_SYNC_HTTP","updateStrategy":"UPSERT_BY_ID","storageScope":"CUSTOMER_MANAGED","sharingScope":"DEPLOYMENT_ONLY",
                 "syncConnector":{"connectorType":"HTTP_JSON","connectionProfile":{"profileId":"provider","environment":"bound",
                   "managedBaseUrlField":"providerBaseUrl","managedAllowedHostField":"providerHost",
                   "auth":{"strategy":"API_KEY","apiKeyHeader":"X-API-Key","apiKeySecretRefField":"providerKey"},"capabilityGrants":["records:read"]},
                 "protectedResource":{"bindingId":"source-account","environment":"bound","resourceType":"account","resourceIdField":"accountId","capabilityGrants":["records:read"]},
                 "httpSource":{"sourceId":"records","path":"/records","method":"GET","trustedResourcePlacements":[{"target":"QUERY","field":"accountId"}],
                   "requiredCapabilityGrants":["records:read"],"pagination":{"strategy":"NONE","maxPages":100},
                   "mapping":{"recordsJsonPointer":"/records","idJsonPointer":"/id","resourceJsonPointer":"/accountId","contentFields":{"content":"/content"},"entityFields":{},"metadataFields":{}},
                   "tombstonePolicy":{"strategy":"NONE"},"scheduleSeconds":900}}}
                """;
            case "SQL_QUERY" -> """
                {"ingestionMode":"EXTERNAL_SYNC_SQL","updateStrategy":"UPSERT_BY_ID","storageScope":"CUSTOMER_MANAGED","sharingScope":"DEPLOYMENT_ONLY",
                 "syncConnector":{"connectorType":"SQL_QUERY","connectionRefField":"connectionRef","query":"SELECT id, content FROM customer_records"}}
                """;
            default -> """
                {"ingestionMode":"EXTERNAL_DOCUMENT_STORAGE","updateStrategy":"VERSIONED_REPLACE","storageScope":"CUSTOMER_SCOPED","sharingScope":"DEPLOYMENT_ONLY",
                 "sourceConnector":{"connectorType":"S3_COMPATIBLE_OBJECT_STORAGE","storageOwnership":"BOUND_CONNECTION","bindingRefField":"documentStorageBindingRef",
                   "allowedOperations":["LIST","READ","HEAD"],"deleteSourceOnRemoval":false},
                 "documentPolicy":{"allowedMediaTypes":["text/plain","application/json"],"allowedExtensions":[".txt",".json"],"jsonContentKeys":["content","text","body"],
                   "maxSourceBytes":1048576,"maxSources":100,"maxTotalIndexedBytes":104857600,"maxChunksPerSource":100,"maxChunkCharacters":8000,
                   "maxTotalCharacters":250000,"previewMaxChunks":10,"previewMaxCharactersPerChunk":500,"evidenceRetentionDays":30,"commandRetentionDays":30,
                   "retentionBatchSize":100,"allowedMetadataKeys":["originalFilename","locale","sourceCategory","publicationStatus"],
                   "initialIndexRequiresConfirmation":true,"trustedAutoIndexingAllowed":false}}
                """;
        };
        ObjectNode definition = (ObjectNode) mapper.readTree(dataset);
        definition.put("datasetId", "source-records").put("entityType", "record");
        ((com.fasterxml.jackson.databind.node.ArrayNode) root.at("/contributions/datasets")).add(definition);
        var form = (com.fasterxml.jackson.databind.node.ArrayNode) root.path("installForm");
        if ("HTTP_JSON".equals(kind)) {
            input(form, "providerBaseUrl", "url"); input(form, "providerHost", "text");
            input(form, "providerKey", "secretref"); input(form, "accountId", "text");
        } else if ("SQL_QUERY".equals(kind)) input(form, "connectionRef", "text");
        else {
            input(form, "documentStorageBindingRef", "text");
            ((ObjectNode) root.path("permissions")).put("requiresSharedDatasetAccess", true);
            var entities = (ObjectNode) root.at("/contributions/entityConfig/ai-entities");
            entities.set("document", entities.remove("record"));
            definition.put("entityType", "document");
            ((ObjectNode) root.at("/contributions/knowledgeSources/0")).put("entityType", "document");
        }
        root.putObject("presentation").put("catalogueFamily", switch (kind) {
            case "SQL_QUERY" -> "SQL_QUERY"; case "HTTP_JSON" -> "HTTP_JSON"; default -> "DOCUMENT_STORAGE";
        }).put("catalogueAudience", "CUSTOMER").put("adapterContract", kind);
        return root;
    }

    private static void input(com.fasterxml.jackson.databind.node.ArrayNode form, String id, String type) {
        form.addObject().put("id", id).put("label", id).put("type", type).put("required", true);
    }
}
