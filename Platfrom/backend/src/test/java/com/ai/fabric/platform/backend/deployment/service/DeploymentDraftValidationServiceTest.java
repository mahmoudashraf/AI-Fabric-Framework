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
    void validateAcceptsInlineActionRouteWithoutExplicitRoutingEntry() {
        DraftValidationResponse response = service.validate(draft(
            """
                {
                  "actions": [
                    {
                      "name": "list_products",
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
                      "description": "Cancel purchase order",
                      "requiresConfirmation": true
                    },
                    {
                      "name": "offer_order_discount",
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
    void validateAcceptsPromptRagSimilarityThresholdWithinRange() {
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
}
