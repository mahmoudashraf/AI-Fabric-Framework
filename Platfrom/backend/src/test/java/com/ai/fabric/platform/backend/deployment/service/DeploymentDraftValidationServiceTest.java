package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentDraftValidationServiceTest {

    private final DeploymentDraftValidationService service = new DeploymentDraftValidationService(new ObjectMapper());

    @Test
    void validateAcceptsPublishableRoutingDraft() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
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
                      "fields": []
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
                      "fields": []
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
                      "fields": []
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
                      "fields": []
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
                      "fields": []
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
                      "fields": []
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
                      "fields": []
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
                      "fields": []
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
    void validateRejectsRestEmbeddingWithoutBaseUrl() {
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
                      "fields": []
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
                  "llmProvider": "gemini",
                  "embeddingProvider": "rest",
                  "vectorStrategy": "lucene",
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
            .contains("REST_EMBEDDING_BASE_URL_REQUIRED");
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
                      "fields": []
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
                      "fields": []
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
                      "fields": []
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
                      "fields": []
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

    private DeploymentDraftEntity draft(String actionsConfig,
                                        String entityConfig,
                                        String routingConfig,
                                        String providerConfig,
                                        String securityConfig) {
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
        draft.setPromptConfigJson(
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
        draft.setCreatedAt(Instant.parse("2026-03-29T00:00:00Z"));
        draft.setUpdatedAt(Instant.parse("2026-03-29T00:00:00Z"));
        return draft;
    }
}
