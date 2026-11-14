package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceEndpointEntity;
import com.ai.fabric.platform.backend.marketplace.service.PlatformManagedInferenceEndpointService;
import com.ai.fabric.platform.backend.marketplace.service.PlatformManagedInferenceServiceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentDraftValidationServiceTest {

    private final DeploymentDraftValidationService service = new DeploymentDraftValidationService(new ObjectMapper());

    @ParameterizedTest
    @ValueSource(strings = {"READ", "READ_WRITE", "WRITE_ONLY", "read", "read_write", "write_only",
        "\t ReAd \r\n", "\n Read_Write\t", "\twrite_ONLY "})
    void validateAcceptsNativeActionAccessModesWithoutRewritingDraft(String mode) throws Exception {
        var mapper = new ObjectMapper();
        var draft = actionAccessModeDraft();
        var actions = mapper.readTree(draft.getActionsConfigJson());
        ((com.fasterxml.jackson.databind.node.ObjectNode) actions.at("/actions/0")).put("accessMode", mode);
        draft.setActionsConfigJson(actions.toString());
        String original = draft.getActionsConfigJson();

        var response = service.validate(draft);

        assertThat(response.publishReady()).isTrue();
        assertThat(response.errorCount()).isZero();
        assertThat(draft.getActionsConfigJson()).isEqualTo(original);
        assertThat(actions.at("/actions/0/requiresConfirmation").isMissingNode()).isTrue();
        assertThat(actions.at("/actions/0/groundingVectorSpaces").isMissingNode()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"accessMode\":null}", "{\"accessMode\":\"\"}",
        "{\"accessMode\":\" \\t\\r\\n\"}", "{\"accessMode\":\"\\u2003\"}", "{\"accessMode\":\"\\u0000\"}"})
    void validateRequiresExplicitActionAccessMode(String fields) throws Exception {
        var mapper = new ObjectMapper();
        var draft = actionAccessModeDraft();
        var actions = mapper.readTree(draft.getActionsConfigJson());
        var action = (com.fasterxml.jackson.databind.node.ObjectNode) actions.at("/actions/0");
        action.remove("accessMode");
        action.setAll((com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(fields));
        draft.setActionsConfigJson(actions.toString());
        String original = draft.getActionsConfigJson();

        var response = service.validate(draft);

        assertThat(response.publishReady()).isFalse();
        assertThat(response.errorCount()).isEqualTo(1);
        assertThat(response.issues()).filteredOn(issue -> "ERROR".equals(issue.severity())).singleElement()
            .satisfies(issue -> {
                assertThat(issue.section()).isEqualTo("actions");
                assertThat(issue.code()).isEqualTo("ACTION_ACCESS_MODE_REQUIRED");
                assertThat(issue.path()).isEqualTo("$.actions[0].accessMode");
                assertThat(issue.message()).contains("required", "READ", "READ_WRITE", "WRITE_ONLY");
            });
        assertThat(draft.getActionsConfigJson()).isEqualTo(original);
        assertThat(draft.getStatus()).isEqualTo("DRAFT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"WRITE\"", "\"EXECUTE\"", "\"READ-WRITE\"", "42", "false",
        "[]", "{}", "[\"READ\"]", "{\"mode\":\"READ\"}", "\"\\u2003READ\\u2003\""})
    void validateRejectsUnsupportedActionAccessModeWithExactPath(String value) throws Exception {
        var mapper = new ObjectMapper();
        var draft = actionAccessModeDraft();
        var actions = mapper.readTree(draft.getActionsConfigJson());
        var action = ((com.fasterxml.jackson.databind.node.ArrayNode) actions.path("actions"))
            .addObject().put("name", "another_service_capability").put("description", "Call another service");
        action.set("route", actions.at("/actions/0/route").deepCopy());
        action.set("accessMode", mapper.readTree(value));
        draft.setActionsConfigJson(actions.toString());
        String original = draft.getActionsConfigJson();

        var response = service.validate(draft);

        assertThat(response.publishReady()).isFalse();
        assertThat(response.errorCount()).isEqualTo(1);
        assertThat(response.issues()).filteredOn(issue -> "ERROR".equals(issue.severity())).singleElement()
            .satisfies(issue -> {
                assertThat(issue.section()).isEqualTo("actions");
                assertThat(issue.code()).isEqualTo("ACTION_ACCESS_MODE_INVALID");
                assertThat(issue.path()).isEqualTo("$.actions[1].accessMode");
                assertThat(issue.message()).contains("READ", "READ_WRITE", "WRITE_ONLY");
            });
        assertThat(draft.getActionsConfigJson()).isEqualTo(original);
        assertThat(draft.getStatus()).isEqualTo("DRAFT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"READ", "READ_WRITE", "WRITE_ONLY"})
    void validateDoesNotRequireConfirmationOrVectorsForActionAccessMode(String mode) throws Exception {
        var mapper = new ObjectMapper();
        for (String confirmation : java.util.List.of("true", "false", "null")) {
            var draft = actionAccessModeDraft();
            var actions = mapper.readTree(draft.getActionsConfigJson());
            var action = (com.fasterxml.jackson.databind.node.ObjectNode) actions.at("/actions/0");
            action.put("accessMode", mode);
            action.set("requiresConfirmation", mapper.readTree(confirmation));
            draft.setActionsConfigJson(actions.toString());

            var response = service.validate(draft);

            assertThat(response.publishReady()).as("%s with requiresConfirmation=%s", mode, confirmation).isTrue();
            assertThat(response.errorCount()).isZero();
        }
    }

    @Test
    void taskValidationAcceptsInputOnlyTaskAndReportsUninstalledCapabilities() throws Exception {
        var mapper = new ObjectMapper();
        var draft = draft("{\"actions\":[]}", "{\"ai-entities\":{}}", "{}", "{}", "{}");
        var behavior = com.ai.fabric.platform.backend.deployment.tasks.ManagedTaskFixtures.behavior(mapper);
        draft.setBehaviorConfigJson(behavior.toString());
        assertThat(service.validate(draft).issues()).extracting("code").doesNotContain("MANAGED_TASKS_INVALID");
        ((com.fasterxml.jackson.databind.node.ObjectNode) behavior.at("/managedTasks/tasks/0")).putArray("readActions").add("uninstalled_read");
        draft.setBehaviorConfigJson(behavior.toString());
        assertThat(service.validate(draft).issues()).extracting("code").contains("MANAGED_TASKS_INVALID");
    }

    @Test
    void aiBehaviourMetadataIsValidatedAsOperatorConfigurationNotModelOutput() {
        var mapper = new ObjectMapper();
        var configured = DeploymentAiBehaviourProfiles.select(mapper.createObjectNode(), mapper.createObjectNode());
        var draft = draft("{\"actions\":[]}", "{\"ai-entities\":{}}", "{}", "{}", "{}");
        draft.setPromptConfigJson(configured.toString());
        assertThat(service.validate(draft).issues()).extracting("code")
            .doesNotContain("AI_BEHAVIOUR_CONFIG_INVALID", "NO_PROMPTS_CONFIGURED");
        ((com.fasterxml.jackson.databind.node.ObjectNode) configured.path("aiBehaviour")).put("revision", 999);
        draft.setPromptConfigJson(configured.toString());
        assertThat(service.validate(draft).issues()).extracting("code").contains("AI_BEHAVIOUR_CONFIG_INVALID");
    }

    @Test
    void verifiedSupabaseEmailNeedsOnlyAnUnprivilegedProviderLookupKey() {
        var security = new ObjectMapper().createObjectNode().put("externalIdentityBrokerEnabled", true)
            .put("externalIdentityProvider", "SUPABASE").put("externalIdentityIssuer", "https://identity.example/auth/v1")
            .put("externalIdentityJwkSetUri", "https://identity.example/auth/v1/.well-known/jwks.json")
            .put("externalIdentityRequireVerifiedEmail", true).put("corsAllowedOrigins", "https://workspace.example");
        for (String key : java.util.List.of("", "sb_secret_privileged-key", "sb_publishable_fixture")) {
            security.put("externalIdentitySupabasePublicKey", key);
            var result = service.validate(draft("{\"actions\":[]}", "{\"ai-entities\":{}}", "{}", "{}", security.toString()));
            if (key.equals("sb_publishable_fixture")) assertThat(result.issues()).extracting("code").doesNotContain("EXTERNAL_IDENTITY_PUBLIC_KEY_REQUIRED");
            else assertThat(result.issues()).extracting("code").contains("EXTERNAL_IDENTITY_PUBLIC_KEY_REQUIRED");
        }
    }

    @Test
    void tokenDraftRequiresExplicitModeAndRetainedBindingButNotUnresolvedSecretReferences() {
        var provider = new ObjectMapper().createObjectNode().put("llmProvider", "openai").put("embeddingProvider", "openai")
            .put("vectorStrategy", "milvus").put("vectorProvisioningMode", "EXTERNAL_EXISTING")
            .put("vectorStoragePosture", "SHARED").put("milvusExternalBindingRequired", true)
            .put("milvusAuthenticationType", "TOKEN");
        var valid = service.validate(draft("{\"actions\":[]}", "{\"ai-config\":{\"vector-dimensions\":512},\"ai-entities\":{}}",
            "{}", provider.toString(), "{}"));
        assertThat(valid.issues()).extracting("code").doesNotContain("MILVUS_AUTHENTICATION_INVALID", "MILVUS_HOST_REQUIRED");
        for (String mode : java.util.List.of("token", "", "TOKEN ")) {
            var changed = provider.deepCopy().put("milvusAuthenticationType", mode);
            var response = service.validate(draft("{\"actions\":[]}", "{\"ai-entities\":{}}", "{}", changed.toString(), "{}"));
            assertThat(response.issues()).extracting("code").contains("MILVUS_AUTHENTICATION_INVALID");
        }
        var raw = provider.deepCopy().put("milvusToken", "fixture-credential");
        var rejected = service.validate(draft("{\"actions\":[]}", "{\"ai-entities\":{}}", "{}", raw.toString(), "{}"));
        assertThat(rejected.issues()).extracting("code").contains("MILVUS_AUTHENTICATION_INVALID");
    }

    @Test
    void externalBindingDefersHostOnlyForExplicitScopedExistingMilvus() {
        var provider = new ObjectMapper().createObjectNode().put("llmProvider", "openai").put("embeddingProvider", "openai")
            .put("vectorStrategy", "milvus").put("vectorProvisioningMode", "EXTERNAL_EXISTING")
            .put("vectorStoragePosture", "SHARED").put("milvusExternalBindingRequired", true);
        var valid = service.validate(draft("{\"actions\":[]}", "{\"ai-config\":{\"vector-dimensions\":512},\"ai-entities\":{}}",
            "{}", provider.toString(), "{}"));
        assertThat(valid.issues()).extracting("code").doesNotContain("MILVUS_HOST_REQUIRED", "MILVUS_EXTERNAL_BINDING_MODE_INVALID");
        provider.put("vectorProvisioningMode", "PLATFORM_MANAGED");
        var managed = service.validate(draft("{\"actions\":[]}", "{\"ai-entities\":{}}", "{}", provider.toString(), "{}"));
        assertThat(managed.issues()).extracting("code").contains("MILVUS_EXTERNAL_BINDING_MODE_INVALID");
        provider.put("vectorProvisioningMode", "EXTERNAL_EXISTING").put("milvusExternalBindingRequired", "true");
        var textual = service.validate(draft("{\"actions\":[]}", "{\"ai-entities\":{}}", "{}", provider.toString(), "{}"));
        assertThat(textual.issues()).extracting("code").contains("MILVUS_EXTERNAL_BINDING_MODE_INVALID", "MILVUS_HOST_REQUIRED");
        provider.put("milvusExternalBindingRequired", true).put("vectorStrategy", "pinecone");
        var wrongProvider = service.validate(draft("{\"actions\":[]}", "{\"ai-entities\":{}}", "{}", provider.toString(), "{}"));
        assertThat(wrongProvider.issues()).extracting("code").contains("MILVUS_EXTERNAL_BINDING_MODE_INVALID");
    }

    @Test
    void validateRequiresDurableVectorStorageForCustomerDocumentStorage() {
        DeploymentDraftEntity draft = draft(
            "{\"actions\":[]}",
            "{\"ai-config\":{\"vector-dimensions\":1024},\"ai-entities\":{}}",
            "{\"connector\":{\"inbound-auth\":{\"allow-unauthenticated\":false}},\"authz\":{\"enabled\":false}}",
            "{\"llmProvider\":\"openai\",\"embeddingProvider\":\"openai\",\"vectorStrategy\":\"lucene\",\"runtimeProfile\":\"runtime-managed\",\"connectorProfile\":\"connector-hosted\",\"openaiEmbeddingDimensions\":1024}",
            "{\"authzMode\":\"ALLOW_VERIFIED\",\"adminApiKeyEnabled\":true,\"connectorApiKeyEnabled\":true}"
        );
        draft.setMarketplaceDatasetConfigJson(documentDatasetConfig("S3_COMPATIBLE_OBJECT_STORAGE"));

        DraftValidationResponse response = service.validate(draft);

        assertThat(response.issues())
            .extracting("code")
            .contains("DOCUMENT_DURABLE_VECTOR_REQUIRED");
    }

    @Test
    void validateAllowsDurableVectorStorageForCustomerDocumentStorage() {
        DeploymentDraftEntity draft = draft(
            "{\"actions\":[]}",
            "{\"ai-config\":{\"vector-dimensions\":1024},\"ai-entities\":{}}",
            "{\"connector\":{\"inbound-auth\":{\"allow-unauthenticated\":false}},\"authz\":{\"enabled\":false}}",
            "{\"llmProvider\":\"openai\",\"embeddingProvider\":\"openai\",\"vectorStrategy\":\"pinecone\",\"vectorProvisioningMode\":\"PLATFORM_MANAGED\",\"runtimeProfile\":\"runtime-managed\",\"connectorProfile\":\"connector-hosted\",\"openaiEmbeddingDimensions\":1024,\"pineconeManagedIndexEnabled\":true,\"pineconeIndexName\":\"document-knowledge-test\",\"pineconeCloud\":\"aws\",\"pineconeRegion\":\"us-east-1\",\"pineconeMetric\":\"cosine\",\"pineconeDimensions\":1024}",
            "{\"authzMode\":\"ALLOW_VERIFIED\",\"adminApiKeyEnabled\":true,\"connectorApiKeyEnabled\":true}"
        );
        draft.setMarketplaceDatasetConfigJson(documentDatasetConfig("S3_COMPATIBLE_OBJECT_STORAGE"));

        DraftValidationResponse response = service.validate(draft);

        assertThat(response.issues())
            .extracting("code")
            .doesNotContain("DOCUMENT_DURABLE_VECTOR_REQUIRED");
    }

    @Test
    void modeNeutralDocumentDraftAcceptsBindingOwnedStorageWithoutChangingItsContract() throws Exception {
        var mapper = new ObjectMapper();
        var config = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(documentDatasetConfig("S3_COMPATIBLE_OBJECT_STORAGE"));
        var dataset = (com.fasterxml.jackson.databind.node.ObjectNode) config.at("/datasets/0");
        dataset.put("storageScope", "CUSTOMER_SCOPED");
        ((com.fasterxml.jackson.databind.node.ObjectNode) dataset.path("sourceConnector"))
            .put("storageOwnership", "BOUND_CONNECTION");
        var draft = marketplaceDatasetDraft();
        draft.setMarketplaceDatasetConfigJson(config.toString());

        assertThat(service.validate(draft).issues()).extracting("code")
            .doesNotContain("MARKETPLACE_DATASET_STORAGE_SCOPE_UNSUPPORTED", "DOCUMENT_DATASET_BOUNDARY_INVALID",
                "DOCUMENT_SOURCE_OWNERSHIP_INVALID", "DOCUMENT_SOURCE_OPERATIONS_INVALID", "DOCUMENT_SOURCE_DELETE_FORBIDDEN")
            .contains("DOCUMENT_DURABLE_VECTOR_REQUIRED");
        assertThat(mapper.readTree(draft.getMarketplaceDatasetConfigJson())).isEqualTo(config);
    }

    @Test
    void documentDraftRejectsOwnershipMismatchAndSourceWriteAuthorityInBothContracts() throws Exception {
        var mapper = new ObjectMapper();
        for (String scope : java.util.List.of("CUSTOMER_MANAGED", "CUSTOMER_SCOPED")) {
            for (String violation : java.util.List.of("ownership", "write", "missingOperations", "delete", "missingDelete")) {
                var config = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(documentDatasetConfig("S3_COMPATIBLE_OBJECT_STORAGE"));
                var dataset = (com.fasterxml.jackson.databind.node.ObjectNode) config.at("/datasets/0");
                dataset.put("storageScope", scope);
                var connector = (com.fasterxml.jackson.databind.node.ObjectNode) dataset.path("sourceConnector");
                connector.put("storageOwnership", "CUSTOMER_SCOPED".equals(scope) ? "BOUND_CONNECTION" : "CUSTOMER_MANAGED");
                String expected;
                switch (violation) {
                    case "ownership" -> { connector.put("storageOwnership", "LOOMAI_MANAGED"); expected = "DOCUMENT_SOURCE_OWNERSHIP_INVALID"; }
                    case "write" -> { connector.withArray("allowedOperations").add("DELETE"); expected = "DOCUMENT_SOURCE_OPERATIONS_INVALID"; }
                    case "missingOperations" -> { connector.remove("allowedOperations"); expected = "DOCUMENT_SOURCE_OPERATIONS_INVALID"; }
                    case "delete" -> { connector.put("deleteSourceOnRemoval", true); expected = "DOCUMENT_SOURCE_DELETE_FORBIDDEN"; }
                    default -> { connector.remove("deleteSourceOnRemoval"); expected = "DOCUMENT_SOURCE_DELETE_FORBIDDEN"; }
                }
                var draft = marketplaceDatasetDraft();
                draft.setMarketplaceDatasetConfigJson(config.toString());
                assertThat(service.validate(draft).issues()).as(scope + ": " + violation).extracting("code").contains(expected);
            }
        }
    }

    @Test
    void validateKeepsLocalVectorAvailableForMountedDocumentDemo() {
        DeploymentDraftEntity draft = draft(
            "{\"actions\":[]}",
            "{\"ai-config\":{\"vector-dimensions\":1024},\"ai-entities\":{}}",
            "{\"connector\":{\"inbound-auth\":{\"allow-unauthenticated\":false}},\"authz\":{\"enabled\":false}}",
            "{\"llmProvider\":\"openai\",\"embeddingProvider\":\"openai\",\"vectorStrategy\":\"lucene\",\"runtimeProfile\":\"runtime-managed\",\"connectorProfile\":\"connector-hosted\",\"openaiEmbeddingDimensions\":1024}",
            "{\"authzMode\":\"ALLOW_VERIFIED\",\"adminApiKeyEnabled\":true,\"connectorApiKeyEnabled\":true}"
        );
        draft.setMarketplaceDatasetConfigJson(documentDatasetConfig("MOUNTED_FOLDER"));

        DraftValidationResponse response = service.validate(draft);

        assertThat(response.issues())
            .extracting("code")
            .doesNotContain("DOCUMENT_DURABLE_VECTOR_REQUIRED");
    }

    @Test
    void validateRejectsTwoActiveHttpIngestionAuthorities() {
        DeploymentDraftEntity draft = marketplaceDatasetDraft();
        draft.setMarketplaceDatasetConfigJson(httpDatasetConfig(true, true));

        DraftValidationResponse response = service.validate(draft);

        assertThat(response.issues())
            .extracting("code")
            .contains("MARKETPLACE_HTTP_INGESTION_AUTHORITY_INVALID");
    }

    @Test
    void validateAllowsCustomerBackendAsTheOnlyHttpIngestionAuthority() {
        DeploymentDraftEntity draft = marketplaceDatasetDraft();
        draft.setMarketplaceDatasetConfigJson(httpDatasetConfig(false, true));

        DraftValidationResponse response = service.validate(draft);

        assertThat(response.issues())
            .extracting("code")
            .doesNotContain(
                "MARKETPLACE_HTTP_INGESTION_AUTHORITY_INVALID",
                "MARKETPLACE_HTTP_SOURCE_ENABLED_INVALID",
                "MARKETPLACE_CUSTOMER_BACKEND_INGESTION_ENABLED_INVALID"
            );
    }

    @Test
    void validateAcceptsDeploymentLocalSourceProjectionRouteWithoutHttpTargetOrMethod() {
        DeploymentDraftEntity draft = sourceProjectionDraft("neutral-source", false);
        draft.setMarketplaceDatasetConfigJson(httpDatasetConfig(true, false));

        DraftValidationResponse response = service.validate(draft);

        assertThat(response.issues())
            .extracting("code")
            .doesNotContain(
                "ROUTE_TARGET_REQUIRED",
                "ROUTE_METHOD_REQUIRED",
                "ROUTE_SOURCE_PROJECTION_SOURCE_UNKNOWN",
                "ROUTE_SOURCE_PROJECTION_SOURCE_DISABLED"
            );
    }

    @Test
    void validateAcceptsNumericSourceProjectionAbsentValues() {
        DeploymentDraftEntity draft = sourceProjectionDraft("neutral-source", false);
        draft.setMarketplaceDatasetConfigJson(httpDatasetConfig(true, false));
        draft.setRoutingConfigJson(draft.getRoutingConfigJson().replace(
            "\"filters\": []",
            "\"filters\": [{\"param\":\"maximum\",\"fields\":[\"title\"],\"operator\":\"NUMBER_LESS_THAN_OR_EQUAL\",\"absent-values\":[\"0\"]}]"
        ));

        DraftValidationResponse response = service.validate(draft);

        assertThat(response.issues())
            .extracting("code")
            .doesNotContain(
                "ROUTE_SOURCE_PROJECTION_ABSENT_VALUES_INVALID",
                "ROUTE_SOURCE_PROJECTION_ABSENT_VALUE_INVALID",
                "ROUTE_SOURCE_PROJECTION_NUMERIC_ABSENT_VALUE_INVALID"
            );
    }

    @Test
    void validateRejectsNonNumericSourceProjectionAbsentValue() {
        DeploymentDraftEntity draft = sourceProjectionDraft("neutral-source", false);
        draft.setMarketplaceDatasetConfigJson(httpDatasetConfig(true, false));
        draft.setRoutingConfigJson(draft.getRoutingConfigJson().replace(
            "\"filters\": []",
            "\"filters\": [{\"param\":\"maximum\",\"fields\":[\"title\"],\"operator\":\"NUMBER_LESS_THAN_OR_EQUAL\",\"absent-values\":[\"not-a-number\"]}]"
        ));

        DraftValidationResponse response = service.validate(draft);

        assertThat(response.issues())
            .extracting("code")
            .contains("ROUTE_SOURCE_PROJECTION_NUMERIC_ABSENT_VALUE_INVALID");
    }

    @Test
    void validateRejectsSourceProjectionRouteThatReferencesUnknownOrDisabledSource() {
        DeploymentDraftEntity unknownSourceDraft = sourceProjectionDraft("missing-source", false);
        unknownSourceDraft.setMarketplaceDatasetConfigJson(httpDatasetConfig(true, false));
        DeploymentDraftEntity disabledSourceDraft = sourceProjectionDraft("neutral-source", false);
        disabledSourceDraft.setMarketplaceDatasetConfigJson(httpDatasetConfig(false, true));

        DraftValidationResponse unknownSourceResponse = service.validate(unknownSourceDraft);
        DraftValidationResponse disabledSourceResponse = service.validate(disabledSourceDraft);

        assertThat(unknownSourceResponse.issues())
            .extracting("code")
            .contains("ROUTE_SOURCE_PROJECTION_SOURCE_UNKNOWN");
        assertThat(disabledSourceResponse.issues())
            .extracting("code")
            .contains("ROUTE_SOURCE_PROJECTION_SOURCE_DISABLED");
    }

    @Test
    void validateRejectsSourceProjectionRouteMixedWithHttpTarget() {
        DeploymentDraftEntity draft = sourceProjectionDraft("neutral-source", true);
        draft.setMarketplaceDatasetConfigJson(httpDatasetConfig(true, false));

        DraftValidationResponse response = service.validate(draft);

        assertThat(response.issues())
            .extracting("code")
            .contains("ROUTE_TARGET_CONFLICT");
    }

    @Test
    void validateRejectsSourceProjectionFieldsOutsideMappedEntityProjection() {
        DeploymentDraftEntity draft = sourceProjectionDraft("neutral-source", false);
        draft.setMarketplaceDatasetConfigJson(httpDatasetConfig(true, false));
        draft.setRoutingConfigJson(draft.getRoutingConfigJson()
            .replace("\"filters\": []", "\"filters\": [{\"param\":\"query\",\"fields\":[\"secret\"]}]")
            .replace("\"output-fields\": [\"title\"]", "\"output-fields\": [\"secret\"]"));

        DraftValidationResponse response = service.validate(draft);

        assertThat(response.issues())
            .extracting("code")
            .contains(
                "ROUTE_SOURCE_PROJECTION_FILTER_FIELD_UNKNOWN",
                "ROUTE_SOURCE_PROJECTION_OUTPUT_FIELD_UNKNOWN"
            );
    }

    @Test
    void validateRejectsProviderSettingsOnDeploymentLocalSourceProjectionRoute() {
        DeploymentDraftEntity draft = sourceProjectionDraft("neutral-source", false);
        draft.setMarketplaceDatasetConfigJson(httpDatasetConfig(true, false));
        draft.setRoutingConfigJson(draft.getRoutingConfigJson().replace(
            "\"source-projection\": {",
            "\"connection-profile-ref\": \"provider-profile\", \"source-projection\": {"
        ));

        DraftValidationResponse response = service.validate(draft);

        assertThat(response.issues())
            .extracting("code")
            .contains("ROUTE_SOURCE_PROJECTION_PROVIDER_SETTINGS_CONFLICT");
    }

    @Test
    void validateAcceptsPublishableRoutingDraft() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "accessMode": "READ",
                      "description": "List products",
                      "requiredParameters": ["query"]
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "authz": {
                    "enabled": true,
                    "path": "/api/authz/check",
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search",
                      "request": {
                        "query": {
                          "q": "{{params.query}}"
                        }
                      },
                      "response": {
                        "success-http-status": [200],
                        "message": "Products"
                      }
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true,
                  "corsAllowedOrigins": "https://ai-fabric.dev,http://localhost:8080",
                  "corsAllowedOriginPatterns": "https://*lovable*",
                  "corsAllowCredentials": true
                }
                """
        ));

        assertThat(response.publishReady()).isTrue();
        assertThat(response.errorCount()).isZero();
    }

    @Test
    void validateAllMcpActionsWithoutHttpRouteWarnings() {
        var draft = executorOverrideDraft(null);
        draft.setActionsConfigJson("""
            {"actions":[
              {"name":"search_guides","description":"Search public guides","adapterType":"mcp-tool",
               "inputSchema":{"type":"object"},"execution":{"adapterType":"mcp-tool",
                 "mcp":{"connectionId":"mcp-public","serverRef":"public-guides","toolName":"search_guides","toolSchemaHash":"guides-hash"}}},
              {"name":"get_subscription","description":"Read a subscription","adapterType":"mcp-tool"},
              {"name":"cancel_subscription","description":"Cancel a subscription","execution":{"adapterType":"mcp-tool"}},
              {"name":"schema_example","description":"Read the schema example",
               "execution":{"mcp":{"connectionId":"mcp-company","serverRef":"company","toolName":"schema_example","toolSchemaHash":"example-hash"}}}
            ]}
            """);

        assertThat(service.validate(draft).issues()).extracting("code")
            .doesNotContain("ACTION_WITHOUT_ROUTE", "NO_ROUTES_CONFIGURED", "DRAFT_PARSE_FAILED");
    }

    @Test
    void validateMixedMcpAndHttpActionsStillWarnsForMissingHttpRoute() {
        var draft = executorOverrideDraft(null);
        draft.setActionsConfigJson("""
            {"actions":[
              {"name":"search_guides","description":"Search public guides","adapterType":"mcp-tool",
               "execution":{"mcp":{"connectionId":"mcp-public","serverRef":"public-guides","toolName":"search_guides","toolSchemaHash":"guides-hash"}}},
              {"name":"list_products","description":"List products over HTTP"}
            ]}
            """);

        var response = service.validate(draft);
        assertThat(response.issues()).extracting("code").contains("ACTION_WITHOUT_ROUTE", "NO_ROUTES_CONFIGURED");
        assertThat(response.issues().stream().filter(issue -> "ACTION_WITHOUT_ROUTE".equals(issue.code())))
            .extracting("message").containsExactly("No route is configured yet for action: list_products");
    }

    @Test
    void validateEmptyDraftKeepsExistingActionAndRouteWarnings() {
        assertThat(service.validate(executorOverrideDraft(null)).issues()).extracting("code")
            .contains("NO_ACTIONS_CONFIGURED", "NO_ROUTES_CONFIGURED")
            .doesNotContain("ACTION_WITHOUT_ROUTE");
    }

    @Test
    void validateAcceptsInlineActionRouteWithoutExplicitRoutingEntry() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "accessMode": "READ",
                      "description": "List products",
                      "route": {
                        "method": "GET",
                        "url": "https://customer.example/api/products/search",
                        "request": {
                          "query": {
                            "q": "{{params.query}}"
                          }
                        },
                        "response": {
                          "success-http-status": [200]
                        }
                      },
                      "requiredParameters": ["query"]
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    }
                  },
                  "authz": {
                    "enabled": false
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isTrue();
        assertThat(response.errorCount()).isZero();
        assertThat(response.issues())
            .extracting("code")
            .doesNotContain("ACTION_WITHOUT_ROUTE", "NO_ROUTES_CONFIGURED");
    }

    @Test
    void validateRejectsInvalidInlineActionRouteUrl() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "description": "List products",
                      "route": {
                        "method": "GET",
                        "url": "customer.example/products"
                      }
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    }
                  },
                  "actions": {}
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("ROUTE_URL_INVALID");
    }

    @Test
    void validateRejectsSharedIndexKnowledgeSourceOnEmbeddedVectorProvider() {
        DeploymentDraftEntity draft = draft(
            """
                {
                  "actions": [
                    {
                      "name": "search_docs",
                      "description": "Search docs"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "faq-article": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    }
                  },
                  "authz": {
                    "enabled": false
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "ALLOW_VERIFIED",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        );
        draft.setKnowledgeSourceConfigJson(
            """
                {
                  "contractVersion": "KNOWLEDGE_SOURCE_CONFIG_V1",
                  "sources": [
                    {
                      "id": "help-center",
                      "sourceType": "shared-index",
                      "adapterType": "shared-index",
                      "handleRef": "plugin/help-center",
                      "entityType": "faq-article"
                    }
                  ]
                }
                """
        );

        DraftValidationResponse response = service.validate(draft);

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("SHARED_INDEX_REQUIRES_SHARED_VECTOR_STORAGE");
    }

    @Test
    void validateAcceptsExactManagedActionReceiverUrl() {
        DeploymentDraftEntity draft = draft(
            """
                {"actions":[{"name":"create_request","description":"Create request","accessMode":"WRITE_ONLY","requiresConfirmation":true}]}
                """,
            """
                {"ai-config":{"vector-dimensions":512},"ai-entities":{}}
                """,
            """
                {
                  "connector":{"inbound-auth":{"allow-unauthenticated":false,"api-key":{"enabled":true,"header":"X-AIFABRIC-API-KEY","value":"${CONNECTOR_API_KEY}"}}},
                  "actions":{"create_request":{"url":"${MANAGED_RECEIVER_ACTION_URL}","method":"POST"}}
                }
                """,
            """
                {"llmProvider":"openai","embeddingProvider":"openai","vectorStrategy":"lucene","runtimeProfile":"runtime-dev","connectorProfile":"connector-hosted"}
                """,
            """
                {"authzMode":"ALLOW_VERIFIED","adminApiKeyEnabled":true,"connectorApiKeyEnabled":true}
                """
        );

        assertThat(service(null).validate(draft).issues()).extracting("code")
            .doesNotContain("ROUTE_URL_INVALID");
    }

    @Test
    void validateRejectsSharedIndexKnowledgeSourceDeclaredByAdapterOrTypeOnDedicatedProvider() {
        DeploymentDraftEntity draft = draft(
            """
                {
                  "actions": [
                    {
                      "name": "search_docs",
                      "description": "Search docs"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "faq-article": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    }
                  },
                  "authz": {
                    "enabled": false
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "milvus",
                  "vectorProvisioningMode": "PLATFORM_MANAGED",
                  "vectorStoragePosture": "DEDICATED",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "ALLOW_VERIFIED",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        );
        draft.setKnowledgeSourceConfigJson(
            """
                {
                  "contractVersion": "KNOWLEDGE_SOURCE_CONFIG_V1",
                  "sources": [
                    {
                      "id": "help-center",
                      "type": "shared-index",
                      "adapterType": "shared-index",
                      "handleRef": "plugin/help-center",
                      "entityType": "faq-article"
                    }
                  ]
                }
                """
        );

        DraftValidationResponse response = service.validate(draft);

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("SHARED_INDEX_REQUIRES_SHARED_VECTOR_STORAGE");
    }

    @Test
    void validateRejectsVectorKnowledgeSourceFilterWithoutVectorMetadataProjection() {
        DraftValidationResponse response = service.validate(vectorKnowledgeSourceFilterDraft(false));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("KNOWLEDGE_SOURCE_FILTER_NOT_VECTOR_METADATA");
    }

    @Test
    void validateAcceptsVectorKnowledgeSourceFilterBackedByVectorMetadataProjection() {
        DraftValidationResponse response = service.validate(vectorKnowledgeSourceFilterDraft(true));

        assertThat(response.issues())
            .extracting("code")
            .doesNotContain("KNOWLEDGE_SOURCE_FILTER_NOT_VECTOR_METADATA");
    }

    @Test
    void validateRejectsEnabledKnowledgeSourceWithoutCanonicalEntityType() {
        DeploymentDraftEntity draft = marketplaceDatasetDraft();
        draft.setKnowledgeSourceConfigJson("""
            {
              "contractVersion": "KNOWLEDGE_SOURCE_CONFIG_V1",
              "sources": [{
                "id": "dealer-policies",
                "sourceType": "deployment-private-vector",
                "adapterType": "deployment-private-vector",
                "enabled": true
              }]
            }
            """);

        DraftValidationResponse response = service.validate(draft);

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("KNOWLEDGE_SOURCE_ENTITY_TYPE_REQUIRED");
    }

    @Test
    void validateRejectsKnowledgeSourceWithoutStableId() {
        DeploymentDraftEntity draft = marketplaceDatasetDraft();
        draft.setKnowledgeSourceConfigJson("""
            {
              "contractVersion": "KNOWLEDGE_SOURCE_CONFIG_V1",
              "sources": [{
                "sourceType": "deployment-private-vector",
                "adapterType": "deployment-private-vector",
                "entityType": "document",
                "enabled": true
              }]
            }
            """);

        DraftValidationResponse response = service.validate(draft);

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("KNOWLEDGE_SOURCE_ID_REQUIRED");
    }

    @Test
    void validateRejectsNonCanonicalKnowledgeSourceEntityType() {
        DeploymentDraftEntity draft = marketplaceDatasetDraft();
        draft.setKnowledgeSourceConfigJson("""
            {
              "contractVersion": "KNOWLEDGE_SOURCE_CONFIG_V1",
              "sources": [{
                "id": "dealer-policies",
                "sourceType": "deployment-private-vector",
                "adapterType": "deployment-private-vector",
                "entityType": "Dealer Policy",
                "enabled": true
              }]
            }
            """);

        DraftValidationResponse response = service.validate(draft);

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("KNOWLEDGE_SOURCE_ENTITY_TYPE_INVALID");
    }

    @Test
    void validateAcceptsPartialExplicitOverrideOverInlineActionRoute() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_orders",
                      "accessMode": "READ",
                      "description": "List orders",
                      "route": {
                        "method": "GET",
                        "path": "/api/orders/default",
                        "request": {
                          "query": {
                            "status": "{{params.status}}"
                          }
                        },
                        "response": {
                          "success-http-status": [200],
                          "message": "Orders"
                        }
                      },
                      "requiredParameters": ["status"]
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "order": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "authz": {
                    "enabled": false
                  },
                  "actions": {
                    "list_orders": {
                      "url": "https://customer.example/api/orders",
                      "response": {
                        "message": "Orders override"
                      }
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isTrue();
        assertThat(response.errorCount()).isZero();
        assertThat(response.issues())
            .extracting("code")
            .doesNotContain("ROUTE_METHOD_REQUIRED", "ROUTE_TARGET_REQUIRED");
    }

    @Test
    void validateRejectsLuceneVectorDimensionsAboveBackendLimit() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 1536 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("VECTOR_DIMENSIONS_EXCEED_BACKEND_LIMIT");
    }

    @Test
    void validateAcceptsConfirmationInterceptorsWithKnownConfirmableActions() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "cancel_purchase_order",
                      "accessMode": "WRITE_ONLY",
                      "description": "Cancel purchase order",
                      "requiresConfirmation": true
                    },
                    {
                      "name": "offer_order_discount",
                      "accessMode": "WRITE_ONLY",
                      "description": "Offer discount",
                      "requiresConfirmation": true
                    }
                  ],
                  "confirmationInterceptors": [
                    {
                      "name": "cancel_to_retention_offer",
                      "trigger": {
                        "pendingActions": ["cancel_purchase_order"],
                        "confirmation": "CONFIRMATION_POSITIVE",
                        "onceParam": "_retentionOfferOffered"
                      },
                      "decision": {
                        "type": "PROMPT_ACTION",
                        "action": "offer_order_discount",
                        "params": {
                          "orderNumber": "{{pending.actionParams.orderNumber}}",
                          "discountPercent": 10
                        }
                      }
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "cancel_purchase_order": {
                      "method": "POST",
                      "path": "/api/orders/cancel"
                    },
                    "offer_order_discount": {
                      "method": "POST",
                      "path": "/api/orders/offer-discount"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isTrue();
        assertThat(response.errorCount()).isZero();
    }

    @Test
    void validateRejectsPromptActionTargetThatIsNotConfirmable() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "cancel_purchase_order",
                      "description": "Cancel purchase order",
                      "requiresConfirmation": true
                    },
                    {
                      "name": "list_orders",
                      "description": "List orders",
                      "requiresConfirmation": false
                    }
                  ],
                  "confirmationInterceptors": [
                    {
                      "name": "cancel_to_list_orders",
                      "trigger": {
                        "pendingActions": ["cancel_purchase_order"],
                        "confirmation": "CONFIRMATION_POSITIVE"
                      },
                      "decision": {
                        "type": "PROMPT_ACTION",
                        "action": "list_orders"
                      }
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "cancel_purchase_order": {
                      "method": "POST",
                      "path": "/api/orders/cancel"
                    },
                    "list_orders": {
                      "method": "GET",
                      "path": "/api/orders"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("CONFIRMATION_INTERCEPTOR_PROMPT_ACTION_NOT_CONFIRMABLE");
    }

    @Test
    void validateRejectsOpenAiEmbeddingDimensionsAboveLuceneLimit() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted",
                  "openaiEmbeddingDimensions": 1536
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("OPENAI_EMBEDDING_DIMENSIONS_EXCEED_BACKEND_LIMIT");
    }

    @Test
    void validateRejectsEntityAndOpenAiEmbeddingDimensionDrift() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 1536 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "qdrant",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted",
                  "openaiEmbeddingDimensions": 3072
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("EMBEDDING_DIMENSIONS_MISMATCH");
    }

    @Test
    void validateAcceptsPromptRagSimilarityThresholdWithinRange() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "accessMode": "READ",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """,
            """
                {
                  "ragSimilarityThreshold": 0.1
                }
                """
        ));

        assertThat(response.publishReady()).isTrue();
        assertThat(response.issues())
            .extracting("code")
            .doesNotContain("RAG_SIMILARITY_THRESHOLD_INVALID", "NO_PROMPTS_CONFIGURED");
    }

    @Test
    void validateRejectsPromptRagSimilarityThresholdOutsideRange() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """,
            """
                {
                  "ragSimilarityThreshold": 1.5
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("RAG_SIMILARITY_THRESHOLD_INVALID");
    }

    @Test
    void validateAcceptsSmartSuggestionsToggleWithoutPromptText() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "accessMode": "READ",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """,
            """
                {
                  "smartSuggestionsEnabled": false
                }
                """
        ));

        assertThat(response.publishReady()).isTrue();
        assertThat(response.issues())
            .extracting("code")
            .doesNotContain("SMART_SUGGESTIONS_ENABLED_INVALID", "NO_PROMPTS_CONFIGURED");
    }

    @Test
    void validateAcceptsRagGenerationContextBudgetsWithoutPromptText() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "accessMode": "READ",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """,
            """
                {
                  "ragMaxDocumentsUsedForContext": 6,
                  "ragMaxContextChars": 4500,
                  "responseGenerationMaxTokensConcise": 400,
                  "responseGenerationMaxTokensStandard": 900,
                  "responseGenerationMaxTokensDeep": 1400
                }
                """
        ));

        assertThat(response.publishReady()).isTrue();
        assertThat(response.issues())
            .extracting("code")
            .doesNotContain("POSITIVE_INTEGER_REQUIRED", "NO_PROMPTS_CONFIGURED");
    }

    @Test
    void validateRejectsInvalidSmartSuggestionsToggle() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """,
            """
                {
                  "smartSuggestionsEnabled": "sometimes"
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("SMART_SUGGESTIONS_ENABLED_INVALID");
    }

    @Test
    void validateRejectsInvalidRagGenerationContextBudgets() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """,
            """
                {
                  "ragMaxDocumentsUsedForContext": 0,
                  "ragMaxContextChars": "a lot",
                  "responseGenerationMaxTokensConcise": 0,
                  "responseGenerationMaxTokensStandard": "default",
                  "responseGenerationMaxTokensDeep": -5
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("path")
            .contains(
                "$.ragMaxDocumentsUsedForContext",
                "$.ragMaxContextChars",
                "$.responseGenerationMaxTokensConcise",
                "$.responseGenerationMaxTokensStandard",
                "$.responseGenerationMaxTokensDeep"
            );
    }

    @Test
    void validateRejectsPathRouteWithoutConnectorUpstreamBaseUrl() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("ROUTE_PATH_REQUIRES_CONNECTOR_UPSTREAM");
    }

    @Test
    void validateRejectsQdrantWithoutHost() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "qdrant",
                  "runtimeProfile": "runtime-managed",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("QDRANT_HOST_REQUIRED");
    }

    @Test
    void validateRejectsSharedWeaviateWithoutNativeMultiTenancy() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 1536 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "weaviate",
                  "vectorProvisioningMode": "EXTERNAL_EXISTING",
                  "vectorStoragePosture": "SHARED",
                  "weaviateHost": "example.weaviate.cloud",
                  "weaviateNativeMultiTenancyEnabled": false,
                  "runtimeProfile": "runtime-managed",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("WEAVIATE_NATIVE_MULTI_TENANCY_REQUIRED");
    }

    @Test
    void validateRejectsNonBooleanAnonymousAllowed() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "search_products",
                      "description": "Search products",
                      "anonymousAllowed": "yes"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "authz": {
                    "enabled": true,
                    "path": "/api/authz/check",
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "search_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("ANONYMOUS_ALLOWED_BOOLEAN");
    }

    @Test
    void validateRejectsInvalidPurposeSpecificProviderTuning() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-managed",
                  "connectorProfile": "connector-hosted",
                  "orchestrationLlmProvider": "bedrock",
                  "generationTemperature": "3.4",
                  "openaiMaxTokens": "-10",
                  "azureValidateOnStartup": "true"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains(
                "ORCHESTRATION_PROVIDER_INVALID",
                "GENERATION_TEMPERATURE_INVALID",
                "POSITIVE_INTEGER_REQUIRED",
                "AZURE_VALIDATE_ON_STARTUP_BOOLEAN_REQUIRED"
            );
    }

    @Test
    void validateAcceptsBoundedExecutorReadActionResolutionOverrides() {
        DeploymentDraftEntity draft = draft(
            "{\"actions\":[]}",
            "{\"ai-config\":{\"vector-dimensions\":512},\"ai-entities\":{}}",
            "{\"connector\":{\"inbound-auth\":{\"allow-unauthenticated\":true}},\"authz\":{\"enabled\":false},\"actions\":{}}",
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-managed",
                  "connectorProfile": "connector-hosted",
                  "orchestrationModeOverrides": {
                    "executor": {
                      "vectorSpaceSelectionRequired": true,
                      "readActionResolution": {
                        "planningMode": "ITERATIVE",
                        "maxIterations": 2,
                        "maxPlannerContextChars": 12000,
                        "maxActionEvidenceCharsPerAction": 8000,
                        "maxActionEvidenceItems": 10,
                        "ragCooperationMode": "RAG_IF_ACTIONS_INSUFFICIENT"
                      },
                      "semanticRecovery": {
                        "extractionFailure": "LLM_REPAIR_THEN_FAIL",
                        "missingTarget": "LLM_REPAIR_THEN_CLARIFY",
                        "missingVectorSpace": "LLM_REPAIR_THEN_CLARIFY",
                        "invalidVectorSpace": "LLM_REPAIR_THEN_FAIL",
                        "unknownAction": "LLM_REPAIR_THEN_FAIL",
                        "actionDomainMismatch": "DENY_AND_LLM_REPLAN",
                        "advancedNoEvidence": "RETURN_NO_EVIDENCE"
                      }
                    }
                  }
                }
                """,
            "{\"authzMode\":\"ALLOW_VERIFIED\",\"adminApiKeyEnabled\":true,\"connectorApiKeyEnabled\":true}"
        );

        DraftValidationResponse response = service.validate(draft);

        assertThat(response.issues())
            .extracting("code")
            .doesNotContain(
                "ORCHESTRATION_MODE_OVERRIDES_OBJECT_REQUIRED",
                "ORCHESTRATION_MODE_OVERRIDE_UNSUPPORTED",
                "EXECUTOR_MODE_OVERRIDE_OBJECT_REQUIRED",
                "EXECUTOR_MODE_OVERRIDE_FIELD_UNSUPPORTED",
                "VECTOR_SPACE_SELECTION_REQUIRED_BOOLEAN_REQUIRED",
                "READ_ACTION_RESOLUTION_OVERRIDE_OBJECT_REQUIRED",
                "READ_ACTION_RESOLUTION_OVERRIDE_FIELD_UNSUPPORTED",
                "READ_ACTION_RESOLUTION_PLANNING_MODE_INVALID",
                "READ_ACTION_RESOLUTION_MAX_ITERATIONS_INVALID",
                "READ_ACTION_RESOLUTION_MAX_PLANNER_CONTEXT_CHARS_INVALID",
                "READ_ACTION_RESOLUTION_MAX_ACTION_EVIDENCE_CHARS_PER_ACTION_INVALID",
                "READ_ACTION_RESOLUTION_MAX_ACTION_EVIDENCE_ITEMS_INVALID",
                "READ_ACTION_RESOLUTION_RAG_COOPERATION_MODE_INVALID",
                "SEMANTIC_RECOVERY_OVERRIDE_OBJECT_REQUIRED",
                "SEMANTIC_RECOVERY_OVERRIDE_FIELD_UNSUPPORTED",
                "SEMANTIC_RECOVERY_POLICY_INVALID"
            );
    }

    @Test
    void validateAcceptsExecutorReadEvidenceBudgetBoundaries() {
        var mapper = new ObjectMapper();
        for (int[] budgets : java.util.List.of(new int[] {1, 1, 1},
            new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE, 100})) {
            var executor = mapper.createObjectNode();
            executor.putObject("readActionResolution")
                .put("maxPlannerContextChars", budgets[0])
                .put("maxActionEvidenceCharsPerAction", budgets[1])
                .put("maxActionEvidenceItems", budgets[2]);

            assertThat(service.validate(executorOverrideDraft(executor)).issues())
                .filteredOn(issue -> issue.path().startsWith("$.orchestrationModeOverrides"))
                .isEmpty();
        }
    }

    @Test
    void validateRejectsMalformedOrOutOfRangeExecutorReadEvidenceBudgets() throws Exception {
        var mapper = new ObjectMapper();
        var codes = java.util.Map.of(
            "maxPlannerContextChars", "READ_ACTION_RESOLUTION_MAX_PLANNER_CONTEXT_CHARS_INVALID",
            "maxActionEvidenceCharsPerAction", "READ_ACTION_RESOLUTION_MAX_ACTION_EVIDENCE_CHARS_PER_ACTION_INVALID",
            "maxActionEvidenceItems", "READ_ACTION_RESOLUTION_MAX_ACTION_EVIDENCE_ITEMS_INVALID"
        );
        for (var entry : codes.entrySet()) {
            var invalidValues = new java.util.ArrayList<>(java.util.List.of(
                "0", "-1", "1.0", "1.5", "\"10\"", "true", "[]", "{}",
                "2147483648", "4294967297", "9223372036854775808"
            ));
            if (entry.getKey().equals("maxActionEvidenceItems")) {
                invalidValues.add("101");
            }
            for (String valueJson : invalidValues) {
                var executor = mapper.createObjectNode();
                executor.putObject("readActionResolution").set(entry.getKey(), mapper.readTree(valueJson));

                assertThat(service.validate(executorOverrideDraft(executor)).issues())
                    .as("%s=%s", entry.getKey(), valueJson)
                    .anySatisfy(issue -> {
                        assertThat(issue.code()).isEqualTo(entry.getValue());
                        assertThat(issue.path()).isEqualTo(
                            "$.orchestrationModeOverrides.executor.readActionResolution." + entry.getKey());
                    });
            }
        }
    }

    @Test
    void validateRejectsNonObjectExecutorReadActionResolutionOverrides() throws Exception {
        var mapper = new ObjectMapper();
        for (String valueJson : java.util.List.of("true", "1", "\"read\"", "[]")) {
            var executor = mapper.createObjectNode();
            executor.set("readActionResolution", mapper.readTree(valueJson));

            assertThat(service.validate(executorOverrideDraft(executor)).issues())
                .as("readActionResolution=%s", valueJson)
                .anySatisfy(issue -> {
                    assertThat(issue.code()).isEqualTo("READ_ACTION_RESOLUTION_OVERRIDE_OBJECT_REQUIRED");
                    assertThat(issue.path()).isEqualTo("$.orchestrationModeOverrides.executor.readActionResolution");
                });
        }
    }

    @Test
    void validateRejectsReadAuthorityOverridesAlongsideValidEvidenceBudgets() throws Exception {
        var executor = new ObjectMapper().readTree("""
            {"readActionResolution":{
              "maxPlannerContextChars":12000,
              "maxActionEvidenceCharsPerAction":8000,
              "maxActionEvidenceItems":10,
              "allowedReadActions":["unapproved"],
              "requireAllowlist":false,
              "requireGroundingEligible":false,
              "rawEnvironment":{}
            }}
            """);

        assertThat(service.validate(executorOverrideDraft(executor)).issues())
            .filteredOn(issue -> issue.path().startsWith("$.orchestrationModeOverrides"))
            .allSatisfy(issue -> assertThat(issue.code()).isEqualTo("READ_ACTION_RESOLUTION_OVERRIDE_FIELD_UNSUPPORTED"))
            .extracting("path").containsExactlyInAnyOrder(
                "$.orchestrationModeOverrides.executor.readActionResolution.allowedReadActions",
                "$.orchestrationModeOverrides.executor.readActionResolution.requireAllowlist",
                "$.orchestrationModeOverrides.executor.readActionResolution.requireGroundingEligible",
                "$.orchestrationModeOverrides.executor.readActionResolution.rawEnvironment"
            );
    }

    @Test
    void validateLeavesAbsentExecutorReadEvidenceBudgetsUnchanged() throws Exception {
        var mapper = new ObjectMapper();
        DraftValidationResponse baseline = service.validate(executorOverrideDraft(null));
        for (String executorJson : java.util.List.of(
            "{}", "{\"readActionResolution\":null}", "{\"readActionResolution\":{}}",
            "{\"readActionResolution\":{\"maxPlannerContextChars\":null,\"maxActionEvidenceCharsPerAction\":null,\"maxActionEvidenceItems\":null}}"
        )) {
            assertThat(service.validate(executorOverrideDraft(mapper.readTree(executorJson))))
                .as("executor=%s", executorJson)
                .usingRecursiveComparison().ignoringFields("validatedAt").isEqualTo(baseline);
        }
    }

    @Test
    void validateAcceptsBoundedExecutorRagAndOverviewOverrides() {
        var mapper = new ObjectMapper();
        for (int maxSpaces : java.util.List.of(1, 12)) {
            var executor = mapper.createObjectNode().put("knowledgeBaseOverviewEnabled", true);
            executor.putObject("rag").put("fanoutEnabled", true).put("maxSpaces", maxSpaces);
            executor.putObject("semanticRecovery").put("missingVectorSpace", " bounded_allowed_fan_out ");

            DraftValidationResponse response = service.validate(executorOverrideDraft(executor));

            assertThat(response.issues())
                .filteredOn(issue -> issue.path().startsWith("$.orchestrationModeOverrides"))
                .isEmpty();
        }
    }

    @Test
    void validateAcceptsDisabledExecutorRagAndOverviewWithoutRecoveryBudget() {
        var mapper = new ObjectMapper();
        var executor = mapper.createObjectNode().put("knowledgeBaseOverviewEnabled", false);
        executor.putObject("rag").put("fanoutEnabled", false);

        DraftValidationResponse response = service.validate(executorOverrideDraft(executor));

        assertThat(response.issues())
            .filteredOn(issue -> issue.path().startsWith("$.orchestrationModeOverrides"))
            .isEmpty();
    }

    @Test
    void validateRequiresExplicitExecutorFanoutBudgetForBoundedRecovery() throws Exception {
        var mapper = new ObjectMapper();
        var executor = mapper.createObjectNode();
        executor.putObject("semanticRecovery").put("missingVectorSpace", "BOUNDED_ALLOWED_FAN_OUT");
        assertThat(service.validate(executorOverrideDraft(executor)).issues())
            .extracting("code").contains("EXECUTOR_RAG_FANOUT_BUDGET_REQUIRED");

        for (String ragJson : java.util.List.of(
            "null", "{}", "{\"maxSpaces\":2}",
            "{\"fanoutEnabled\":false,\"maxSpaces\":2}",
            "{\"fanoutEnabled\":\"true\",\"maxSpaces\":2}",
            "{\"fanoutEnabled\":true}",
            "{\"fanoutEnabled\":true,\"maxSpaces\":null}",
            "{\"fanoutEnabled\":true,\"maxSpaces\":0}",
            "{\"fanoutEnabled\":true,\"maxSpaces\":13}",
            "{\"fanoutEnabled\":true,\"maxSpaces\":\"2\"}",
            "{\"fanoutEnabled\":true,\"maxSpaces\":4294967297}"
        )) {
            executor.set("rag", mapper.readTree(ragJson));

            assertThat(service.validate(executorOverrideDraft(executor)).issues())
                .as("rag=%s", ragJson)
                .anySatisfy(issue -> {
                    assertThat(issue.code()).isEqualTo("EXECUTOR_RAG_FANOUT_BUDGET_REQUIRED");
                    assertThat(issue.path()).isEqualTo("$.orchestrationModeOverrides.executor.semanticRecovery.missingVectorSpace");
                });
        }
    }

    @Test
    void validateRejectsInvalidExecutorOverviewAndFanoutBooleanTypes() throws Exception {
        var mapper = new ObjectMapper();
        for (String valueJson : java.util.List.of("\"true\"", "1", "[]", "{}")) {
            var executor = mapper.createObjectNode();
            executor.set("knowledgeBaseOverviewEnabled", mapper.readTree(valueJson));
            executor.putObject("rag").set("fanoutEnabled", mapper.readTree(valueJson));

            DraftValidationResponse response = service.validate(executorOverrideDraft(executor));

            assertThat(response.issues()).as("value=%s", valueJson).extracting("code")
                .contains("KNOWLEDGE_BASE_OVERVIEW_ENABLED_BOOLEAN_REQUIRED", "EXECUTOR_RAG_FANOUT_ENABLED_BOOLEAN_REQUIRED");
            assertThat(response.issues()).extracting("path")
                .contains("$.orchestrationModeOverrides.executor.knowledgeBaseOverviewEnabled",
                    "$.orchestrationModeOverrides.executor.rag.fanoutEnabled");
        }
    }

    @Test
    void validateRejectsInvalidExecutorRagMaxSpaces() throws Exception {
        var mapper = new ObjectMapper();
        for (String valueJson : java.util.List.of(
            "0", "-1", "13", "1.0", "1.5", "\"2\"", "true", "[]", "{}", "4294967297", "9223372036854775808"
        )) {
            var executor = mapper.createObjectNode();
            executor.putObject("rag").set("maxSpaces", mapper.readTree(valueJson));

            assertThat(service.validate(executorOverrideDraft(executor)).issues())
                .as("maxSpaces=%s", valueJson)
                .anySatisfy(issue -> {
                    assertThat(issue.code()).isEqualTo("EXECUTOR_RAG_MAX_SPACES_INVALID");
                    assertThat(issue.path()).isEqualTo("$.orchestrationModeOverrides.executor.rag.maxSpaces");
                });
        }
    }

    @Test
    void validateRejectsNonObjectExecutorRagOverrides() throws Exception {
        var mapper = new ObjectMapper();
        for (String ragJson : java.util.List.of("true", "1", "\"rag\"", "[]")) {
            var executor = mapper.createObjectNode();
            executor.set("rag", mapper.readTree(ragJson));

            assertThat(service.validate(executorOverrideDraft(executor)).issues())
                .as("rag=%s", ragJson)
                .anySatisfy(issue -> {
                    assertThat(issue.code()).isEqualTo("EXECUTOR_RAG_OVERRIDE_OBJECT_REQUIRED");
                    assertThat(issue.path()).isEqualTo("$.orchestrationModeOverrides.executor.rag");
                });
        }
    }

    @Test
    void validateRejectsUnknownExecutorRagFieldsIncludingAllowlistOverrides() throws Exception {
        var executor = new ObjectMapper().readTree("""
            {
              "retrievalVectorSpacesAllowlist": ["unapproved"],
              "rag": {
                "fanoutEnabled": true,
                "maxSpaces": 2,
                "retrievalVectorSpacesAllowlist": ["unapproved"],
                "topKPerSpace": 100,
                "rawEnvironment": {}
              }
            }
            """);

        DraftValidationResponse response = service.validate(executorOverrideDraft(executor));

        assertThat(response.issues()).extracting("code")
            .contains("EXECUTOR_MODE_OVERRIDE_FIELD_UNSUPPORTED", "EXECUTOR_RAG_OVERRIDE_FIELD_UNSUPPORTED");
        assertThat(response.issues()).extracting("path").contains(
            "$.orchestrationModeOverrides.executor.retrievalVectorSpacesAllowlist",
            "$.orchestrationModeOverrides.executor.rag.retrievalVectorSpacesAllowlist",
            "$.orchestrationModeOverrides.executor.rag.topKPerSpace",
            "$.orchestrationModeOverrides.executor.rag.rawEnvironment"
        );
    }

    @Test
    void validateLeavesAbsentExecutorRagAndOverviewOverridesUnchanged() throws Exception {
        var mapper = new ObjectMapper();
        DraftValidationResponse baseline = service.validate(executorOverrideDraft(null));
        for (String executorJson : java.util.List.of(
            "{}", "{\"rag\":{}}", "{\"knowledgeBaseOverviewEnabled\":null,\"rag\":null}",
            "{\"rag\":{\"fanoutEnabled\":null,\"maxSpaces\":null}}"
        )) {
            DraftValidationResponse response = service.validate(executorOverrideDraft(mapper.readTree(executorJson)));

            assertThat(response).as("executor=%s", executorJson)
                .usingRecursiveComparison().ignoringFields("validatedAt").isEqualTo(baseline);
        }
    }

    @Test
    void validateRejectsUnboundedOrUnknownReadActionResolutionOverrides() {
        DeploymentDraftEntity draft = draft(
            "{\"actions\":[]}",
            "{\"ai-config\":{\"vector-dimensions\":512},\"ai-entities\":{}}",
            "{\"connector\":{\"inbound-auth\":{\"allow-unauthenticated\":true}},\"authz\":{\"enabled\":false},\"actions\":{}}",
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-managed",
                  "connectorProfile": "connector-hosted",
                  "orchestrationModeOverrides": {
                    "executor": {
                      "vectorSpaceSelectionRequired": "yes",
                      "readActionResolution": {
                        "planningMode": "FOREVER",
                        "maxIterations": 20,
                        "ragCooperationMode": "ALWAYS",
                        "rawEnvironment": "not-allowed"
                      },
                      "semanticRecovery": {
                        "missingVectorSpace": "SEARCH_EVERYTHING",
                        "rawEnvironment": "not-allowed"
                      }
                    },
                    "unknown": {}
                  }
                }
                """,
            "{\"authzMode\":\"ALLOW_VERIFIED\",\"adminApiKeyEnabled\":true,\"connectorApiKeyEnabled\":true}"
        );

        DraftValidationResponse response = service.validate(draft);

        assertThat(response.issues())
            .extracting("code")
            .contains(
                "ORCHESTRATION_MODE_OVERRIDE_UNSUPPORTED",
                "VECTOR_SPACE_SELECTION_REQUIRED_BOOLEAN_REQUIRED",
                "READ_ACTION_RESOLUTION_OVERRIDE_FIELD_UNSUPPORTED",
                "READ_ACTION_RESOLUTION_PLANNING_MODE_INVALID",
                "READ_ACTION_RESOLUTION_MAX_ITERATIONS_INVALID",
                "READ_ACTION_RESOLUTION_RAG_COOPERATION_MODE_INVALID",
                "SEMANTIC_RECOVERY_OVERRIDE_FIELD_UNSUPPORTED",
                "SEMANTIC_RECOVERY_POLICY_INVALID"
            );
    }

    @Test
    void validateRejectsPlatformManagedQdrantWithoutCloudRegion() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 1536 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "qdrant",
                  "vectorProvisioningMode": "PLATFORM_MANAGED",
                  "runtimeProfile": "runtime-managed",
                  "connectorProfile": "connector-hosted",
                  "qdrantCloudProviderId": "aws"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("QDRANT_CLOUD_REGION_REQUIRED");
    }

    @Test
    void validateAcceptsPlatformManagedSharedQdrant() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "accessMode": "READ",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 1536 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "qdrant",
                  "vectorProvisioningMode": "PLATFORM_MANAGED",
                  "vectorStoragePosture": "SHARED",
                  "runtimeProfile": "runtime-managed",
                  "connectorProfile": "connector-hosted",
                  "qdrantManagedCollectionsEnabled": true,
                  "qdrantCloudProviderId": "aws",
                  "qdrantCloudRegionId": "eu-west-1"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isTrue();
        assertThat(response.errorCount()).isZero();
    }

    @Test
    void validateAcceptsExternalExistingSharedQdrantWithManagedCollections() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "accessMode": "READ",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 1536 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "qdrant",
                  "vectorProvisioningMode": "EXTERNAL_EXISTING",
                  "vectorStoragePosture": "SHARED",
                  "runtimeProfile": "runtime-managed",
                  "connectorProfile": "connector-hosted",
                  "qdrantManagedCollectionsEnabled": true,
                  "qdrantHost": "https://shared-qdrant.example"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isTrue();
        assertThat(response.errorCount()).isZero();
    }

    @Test
    void validateRejectsPlatformManagedMilvusWithoutZillizProjectAndRegion() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 768 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "gemini",
                  "embeddingProvider": "gemini",
                  "vectorStrategy": "milvus",
                  "vectorProvisioningMode": "PLATFORM_MANAGED",
                  "runtimeProfile": "runtime-managed",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("ZILLIZ_CLOUD_PROJECT_REQUIRED", "ZILLIZ_CLOUD_REGION_REQUIRED");
    }

    @Test
    void validateRejectsEnabledAuthzWithoutReachableBaseUrl() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    }
                  },
                  "authz": {
                    "enabled": true,
                    "path": "/api/authz/check"
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "url": "https://customer.example/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("AUTHZ_BASE_URL_REQUIRED");
    }

    @Test
    void validateRejectsWildcardAllowedOriginWhenCredentialsEnabled() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true,
                  "corsAllowedOrigins": "*,https://ai-fabric.dev",
                  "corsAllowCredentials": true
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("CORS_ALLOWED_ORIGIN_INVALID", "CORS_WILDCARD_WITH_CREDENTIALS");
    }

    @Test
    void validateAcceptsExpandedAzureAndPineconeProviderDraft() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "accessMode": "READ",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 1024 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "azure",
                  "embeddingProvider": "azure",
                  "vectorStrategy": "pinecone",
                  "runtimeProfile": "runtime-managed",
                  "connectorProfile": "connector-hosted",
                  "azureEndpoint": "https://example-resource.openai.azure.com",
                  "azureDeploymentName": "gpt-4o-deployment",
                  "azureEmbeddingDeploymentName": "embedding-deployment",
                  "azureApiVersion": "2024-02-15-preview",
                  "pineconeEnvironment": "us-east-1-aws",
                  "pineconeIndexName": "ai-fabric",
                  "pineconeDimensions": "1024"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isTrue();
        assertThat(response.errorCount()).isZero();
    }

    @Test
    void validateAcceptsManagedPineconeProvisioningWithoutEnvironmentWhenRegionIsConfigured() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "accessMode": "READ",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 1536 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "pinecone",
                  "runtimeProfile": "runtime-managed",
                  "connectorProfile": "connector-hosted",
                  "pineconeManagedIndexEnabled": true,
                  "pineconeIndexName": "dep-123",
                  "pineconeCloud": "aws",
                  "pineconeRegion": "eu-west-1",
                  "pineconeMetric": "cosine",
                  "pineconeDimensions": "1536"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isTrue();
        assertThat(response.errorCount()).isZero();
    }

    @Test
    void validateRejectsManagedPineconeProvisioningWithUnsupportedMetric() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 1536 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "pinecone",
                  "runtimeProfile": "runtime-managed",
                  "connectorProfile": "connector-hosted",
                  "pineconeManagedIndexEnabled": true,
                  "pineconeIndexName": "dep-123",
                  "pineconeCloud": "aws",
                  "pineconeRegion": "eu-west-1",
                  "pineconeMetric": "manhattan",
                  "pineconeDimensions": "1536"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues()).extracting("code").contains("PINECONE_METRIC_INVALID");
    }

    @Test
    void validateRejectsMilvusWithoutHost() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 384 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "cohere",
                  "embeddingProvider": "cohere",
                  "vectorStrategy": "milvus",
                  "runtimeProfile": "runtime-managed",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("MILVUS_HOST_REQUIRED");
    }

    @Test
    void validateAcceptsOpenAiAnthropicAndOnnxProviderOverrides() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "accessMode": "READ",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 1536 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "onnx",
                  "vectorStrategy": "memory",
                  "runtimeProfile": "runtime-managed",
                  "connectorProfile": "connector-hosted",
                  "openaiBaseUrl": "https://gateway.example/openai",
                  "openaiModel": "gpt-4.1-mini",
                  "anthropicBaseUrl": "https://anthropic-gateway.example",
                  "anthropicModel": "claude-3-haiku-20240307",
                  "onnxModelAlias": "all-mpnet-base-v2",
                  "onnxMaxSequenceLength": "384",
                  "onnxUseGpu": true
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isTrue();
        assertThat(response.errorCount()).isZero();
    }

    @Test
    void validateRejectsInvalidProviderBaseUrlAndOnnxSequenceLength() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 384 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "anthropic",
                  "embeddingProvider": "onnx",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-managed",
                  "connectorProfile": "connector-hosted",
                  "anthropicBaseUrl": "not-a-url",
                  "onnxMaxSequenceLength": "0"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("ANTHROPIC_BASE_URL_INVALID", "POSITIVE_INTEGER_REQUIRED");
    }

    @Test
    void validateRejectsNonStringPromptEntries() {
        DeploymentDraftEntity draft = draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        );
        draft.setPromptConfigJson(
            """
                {
                  "systemPrompt": 42,
                  "answerGenerationPrompt": "Answer using grounded information."
                }
                """
        );

        DraftValidationResponse response = service.validate(draft);

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("PROMPT_TEXT_REQUIRED");
    }

    @Test
    void validateRejectsInvalidPublicRuntimeTokenAudienceIdentifiers() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
                      "description": "List products"
                    }
                  ]
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {
                    "list_products": {
                      "method": "GET",
                      "path": "/api/products/search"
                    }
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true,
                  "publicRuntimeAcceptedAudiences": "storefront chat,account-chat"
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("PUBLIC_RUNTIME_ACCEPTED_AUDIENCE_INVALID");
    }

    @Test
    void validateRejectsUnsupportedShellModuleAndStarterPromptCard() {
        DeploymentDraftEntity draft = draft(
            """
                {
                  "actions": []
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {}
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        );
        draft.setShellConfigJson(
            """
                {
                  "contractVersion": "SHELL_CONFIG_V1",
                  "modules": [
                    { "id": "custom-module" }
                  ],
                  "starterPrompts": [
                    {
                      "label": "Browse",
                      "query": "Show products",
                      "cardId": "custom-card"
                    }
                  ]
                }
                """
        );

        DraftValidationResponse response = service.validate(draft);

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("SHELL_MODULE_ID_UNSUPPORTED", "SHELL_STARTER_PROMPT_CARD_UNSUPPORTED");
    }

    @Test
    void validateRejectsUnknownManagedInferenceEndpointProfile() {
        PlatformManagedInferenceEndpointService endpointService = mock(PlatformManagedInferenceEndpointService.class);
        when(endpointService.requireActive("missing-profile"))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "missing"));

        DraftValidationResponse response = service(endpointService).validate(draft(
            """
                {
                  "actions": []
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    }
                  },
                  "authz": {
                    "enabled": false
                  }
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "onnx",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted",
                  "orchestrationLlmProvider": "openai",
                  "orchestrationEndpointProfile": "missing-profile"
                }
                """,
            """
                {
                  "authzMode": "ALLOW_VERIFIED",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("ORCHESTRATIONENDPOINTPROFILE_UNKNOWN");
    }

    @Test
    void validateRejectsManagedInferenceEndpointProfileWhenProviderMismatches() {
        PlatformManagedInferenceEndpointEntity endpoint = new PlatformManagedInferenceEndpointEntity();
        endpoint.setId("pmie-1");
        endpoint.setProfileRef("openai-cloud-default");
        endpoint.setProviderType("openai");
        endpoint.setStatus("ACTIVE");

        PlatformManagedInferenceEndpointService endpointService = mock(PlatformManagedInferenceEndpointService.class);
        when(endpointService.requireActive("openai-cloud-default")).thenReturn(endpoint);

        DraftValidationResponse response = service(endpointService).validate(draft(
            """
                {
                  "actions": []
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    }
                  },
                  "authz": {
                    "enabled": false
                  }
                }
                """,
            """
                {
                  "llmProvider": "anthropic",
                  "embeddingProvider": "onnx",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted",
                  "generationLlmProvider": "anthropic",
                  "generationEndpointProfile": "openai-cloud-default"
                }
                """,
            """
                {
                  "authzMode": "ALLOW_VERIFIED",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        ));

        assertThat(response.publishReady()).isFalse();
        assertThat(response.issues())
            .extracting("code")
            .contains("GENERATIONENDPOINTPROFILE_PROVIDER_MISMATCH");
    }

    @Test
    void validateAcceptsDedicatedEmbeddingWorkerRefsBeforeManagedServiceExists() {
        PlatformManagedInferenceEndpointService endpointService = mock(PlatformManagedInferenceEndpointService.class);
        PlatformManagedInferenceServiceService inferenceService = mock(PlatformManagedInferenceServiceService.class);
        when(endpointService.requireActive(anyString()))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "endpoint should not be resolved"));
        when(inferenceService.requireActiveEndpointForService(anyString(), anyString(), anyString()))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "service should not be resolved"));

        DraftValidationResponse response = service(endpointService, inferenceService).validate(draft(
            """
                {
                  "actions": []
                }
                """,
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {
                    "product": {
                      "indexing": { "enabled": true, "max-characters": 8000 },
                      "analysis": { "enabled": false, "after": [] },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
                """,
            """
                {
                  "connector": {
                    "inbound-auth": {
                      "allow-unauthenticated": false,
                      "api-key": {
                        "enabled": true,
                        "header": "X-AIFABRIC-API-KEY",
                        "value": "${CONNECTOR_API_KEY}"
                      }
                    },
                    "upstream": {
                      "base-url": "https://customer.example"
                    }
                  },
                  "actions": {}
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted",
                  "embeddingServiceMode": "DEPLOYMENT_DEDICATED_SERVICE",
                  "embeddingManagedServiceRef": "dedicated-embedding-dep-123-mpi-abc",
                  "embeddingEndpointProfile": "dep-dep-123-embedding-worker"
                }
                """,
            """
                {
                  "authzMode": "REMOTE_HTTP",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """,
            """
                {
                  "systemPrompt": "You are helpful.",
                  "intentExtractionPrompt": "Extract the user intent.",
                  "actionSelectionPrompt": "Select actions when needed.",
                  "clarificationPrompt": "Ask for missing information.",
                  "answerGenerationPrompt": "Answer using grounded information.",
                  "retrievalPrompt": "Use retrieval when relevant.",
                  "assistantUiPrompt": "Be concise."
                }
                """
        ));

        assertThat(response.publishReady()).isTrue();
        assertThat(response.issues())
            .extracting("code")
            .doesNotContain("EMBEDDINGMANAGEDSERVICEREF_UNKNOWN", "EMBEDDINGENDPOINTPROFILE_UNKNOWN");
    }

    private DeploymentDraftEntity actionAccessModeDraft() {
        var draft = executorOverrideDraft(null);
        draft.setActionsConfigJson("""
            {"actions":[{"name":"service_capability","description":"Call external service","accessMode":"READ",
              "route":{"method":"GET","url":"https://service.example/capability"}}]}
            """);
        return draft;
    }

    private DeploymentDraftEntity executorOverrideDraft(JsonNode executor) {
        var providers = new ObjectMapper().createObjectNode()
            .put("llmProvider", "openai")
            .put("embeddingProvider", "openai")
            .put("vectorStrategy", "lucene")
            .put("runtimeProfile", "runtime-managed")
            .put("connectorProfile", "connector-hosted");
        if (executor != null) {
            providers.putObject("orchestrationModeOverrides").set("executor", executor);
        }
        return draft(
            "{\"actions\":[]}",
            "{\"ai-config\":{\"vector-dimensions\":512},\"ai-entities\":{}}",
            "{\"connector\":{\"inbound-auth\":{\"allow-unauthenticated\":true}},\"authz\":{\"enabled\":false},\"actions\":{}}",
            providers.toString(),
            "{\"authzMode\":\"ALLOW_VERIFIED\",\"adminApiKeyEnabled\":true,\"connectorApiKeyEnabled\":true}"
        );
    }

    private DeploymentDraftEntity draft(String actionsConfig,
                                        String entityConfig,
                                        String routingConfig,
                                        String providerConfig,
                                        String securityConfig) {
        return draft(
            actionsConfig,
            entityConfig,
            routingConfig,
            providerConfig,
            securityConfig,
            """
                {
                  "systemPrompt": "",
                  "intentExtractionPrompt": "",
                  "actionSelectionPrompt": "",
                  "clarificationPrompt": "",
                  "answerGenerationPrompt": "",
                  "retrievalPrompt": "",
                  "assistantUiPrompt": ""
                }
                """
        );
    }

    private DeploymentDraftEntity draft(String actionsConfig,
                                        String entityConfig,
                                        String routingConfig,
                                        String providerConfig,
                                        String securityConfig,
                                        String promptConfig) {
        DeploymentDraftEntity draft = new DeploymentDraftEntity();
        draft.setId("drf-123");
        draft.setDeploymentId("dep-123");
        draft.setRevisionNumber(1);
        draft.setStatus("DRAFT");
        draft.setActionsConfigJson(actionsConfig);
        draft.setEntityConfigJson(entityConfig);
        draft.setRoutingConfigJson(routingConfig);
        draft.setProviderConfigJson(providerConfig);
        draft.setSecurityConfigJson(securityConfig);
        draft.setPromptConfigJson(promptConfig);
        draft.setCreatedAt(Instant.parse("2026-03-29T00:00:00Z"));
        draft.setUpdatedAt(Instant.parse("2026-03-29T00:00:00Z"));
        return draft;
    }

    private DeploymentDraftValidationService service(PlatformManagedInferenceEndpointService endpointService) {
        return new DeploymentDraftValidationService(new ObjectMapper(), endpointService);
    }

    private DeploymentDraftValidationService service(PlatformManagedInferenceEndpointService endpointService,
                                                     PlatformManagedInferenceServiceService inferenceService) {
        return new DeploymentDraftValidationService(new ObjectMapper(), endpointService, inferenceService);
    }

    private DeploymentDraftEntity vectorKnowledgeSourceFilterDraft(boolean projectDatasetId) {
        String datasetMetadataField = projectDatasetId
            ? """
                        ,{
                          "name": "datasetId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 90,
                          "required": true
                        }
                """
            : "";
        DeploymentDraftEntity draft = draft(
            "{\"actions\":[]}",
            """
                {
                  "ai-config": {"vector-dimensions": 512},
                  "ai-entities": {
                    "vehicle": {
                      "indexing": {"enabled": true, "max-characters": 8000},
                      "analysis": {"enabled": false, "after": []},
                      "searchable-fields": [{
                        "name": "content",
                        "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                        "preprocessing": "CLEAN",
                        "priority": 100,
                        "required": true
                      }],
                      "metadata-fields": [{
                        "name": "tenantId",
                        "data-type": "ID",
                        "destinations": ["VECTOR_METADATA"],
                        "priority": 100,
                        "required": true
                      }%s]
                    }
                  }
                }
                """.formatted(datasetMetadataField),
            """
                {
                  "connector": {"inbound-auth": {"allow-unauthenticated": true}},
                  "authz": {"enabled": false},
                  "actions": {}
                }
                """,
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """,
            """
                {
                  "authzMode": "ALLOW_VERIFIED",
                  "adminApiKeyEnabled": true,
                  "connectorApiKeyEnabled": true
                }
                """
        );
        draft.setKnowledgeSourceConfigJson(
            """
                {
                  "contractVersion": "KNOWLEDGE_SOURCE_CONFIG_V1",
                  "sources": [{
                    "id": "vehicle-inventory",
                    "sourceType": "deployment-private-vector",
                    "adapterType": "deployment-private-vector",
                    "entityType": "vehicle",
                    "filters": {"datasetId": "vehicle-inventory"}
                  }]
                }
                """
        );
        return draft;
    }

    private String documentDatasetConfig(String connectorType) {
        return """
            {
              "contractVersion": "MARKETPLACE_DATASET_CONFIG_V1",
              "datasets": [{
                "datasetId": "document-knowledge",
                "entityType": "document",
                "storageScope": "CUSTOMER_MANAGED",
                "sharingScope": "DEPLOYMENT_ONLY",
                "ingestionMode": "EXTERNAL_DOCUMENT_STORAGE",
                "updateStrategy": "VERSIONED_REPLACE",
                "handleRef": "documents/{deploymentId}/document-knowledge",
                "datasetHash": "test-document-dataset",
                "sourceConnector": {
                  "connectorType": "%s",
                  "storageOwnership": "CUSTOMER_MANAGED",
                  "bindingRef": "dsh-test",
                  "allowedOperations": ["LIST", "READ", "HEAD"],
                  "deleteSourceOnRemoval": false
                },
                "documentPolicy": {
                  "evidenceRetentionDays": 30,
                  "commandRetentionDays": 30,
                  "retentionBatchSize": 100
                }
              }]
            }
            """.formatted(connectorType);
    }

    private DeploymentDraftEntity marketplaceDatasetDraft() {
        return draft(
            "{\"actions\":[]}",
            "{\"ai-config\":{\"vector-dimensions\":512},\"ai-entities\":{}}",
            "{\"connector\":{\"inbound-auth\":{\"allow-unauthenticated\":false}},\"authz\":{\"enabled\":false}}",
            "{\"llmProvider\":\"openai\",\"embeddingProvider\":\"openai\",\"vectorStrategy\":\"lucene\",\"runtimeProfile\":\"runtime-managed\",\"connectorProfile\":\"connector-hosted\"}",
            "{\"authzMode\":\"ALLOW_VERIFIED\",\"adminApiKeyEnabled\":true,\"connectorApiKeyEnabled\":true}"
        );
    }

    private DeploymentDraftEntity sourceProjectionDraft(String sourceRef, boolean includeHttpPath) {
        DeploymentDraftEntity draft = marketplaceDatasetDraft();
        draft.setActionsConfigJson("""
            {
              "actions": [{
                "name": "search_records",
                "description": "Search synchronized records"
              }]
            }
            """);
        String optionalPath = includeHttpPath ? "\"path\": \"/records\"," : "";
        draft.setRoutingConfigJson("""
            {
              "connector": {
                "inbound-auth": {"allow-unauthenticated": true},
                "upstream": {"base-url": "https://customer.example"}
              },
              "authz": {"enabled": false},
              "actions": {
                "search_records": {
                  %s
                  "source-projection": {
                    "source-ref": "%s",
                    "filters": [],
                    "output-fields": ["title"],
                    "default-limit": 10,
                    "max-limit": 50,
                    "max-staleness-seconds": 1800
                  }
                }
              }
            }
            """.formatted(optionalPath, sourceRef));
        return draft;
    }

    private String httpDatasetConfig(boolean connectorPullEnabled, boolean customerBackendPushEnabled) {
        return """
            {
              "contractVersion": "MARKETPLACE_DATASET_CONFIG_V1",
              "datasets": [{
                "datasetId": "neutral-records",
                "entityType": "neutral-record",
                "storageScope": "CUSTOMER_MANAGED",
                "sharingScope": "DEPLOYMENT_ONLY",
                "ingestionMode": "EXTERNAL_SYNC_HTTP",
                "updateStrategy": "UPSERT_BY_ID",
                "handleRef": "neutral/deployment/records",
                "datasetHash": "neutral-records-v1",
                "customerBackendIngestion": {
                  "enabled": %s,
                  "operations": ["UPSERT", "DELETE", "WORK_STATUS", "READINESS"]
                },
                "syncConnector": {
                  "connectorType": "HTTP_JSON",
                  "connectionProfile": {"profileId": "neutral-provider"},
                  "protectedResource": {"resourceId": "scope-1"},
                  "httpSource": {
                    "sourceId": "neutral-source",
                    "enabled": %s,
                    "mapping": {
                      "entityFields": {"title": "/title", "amount": "/amount"}
                    }
                  }
                }
              }]
            }
            """.formatted(customerBackendPushEnabled, connectorPullEnabled);
    }
}
