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
        draft.setCreatedAt(Instant.parse("2026-03-29T00:00:00Z"));
        draft.setUpdatedAt(Instant.parse("2026-03-29T00:00:00Z"));
        return draft;
    }
}
