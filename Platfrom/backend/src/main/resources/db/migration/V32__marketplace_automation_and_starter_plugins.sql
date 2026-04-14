update platform_marketplace_plugin_versions
set manifest_json = '{
  "schemaVersion": 1,
  "pluginId": "mkp-template-commerce-shell",
  "version": "1.0.0",
  "pluginType": "TEMPLATE",
  "displayName": "Loom Commerce Shell",
  "capabilityProfiles": ["SURFACE"],
  "compatibility": {
    "minPlatformVersion": "0.1.0",
    "requiredCapabilities": ["templates", "shellConfig"],
    "supportedDeploymentTargets": [
      "custom-start-from-scratch",
      "dev-openai-lucene",
      "dev-openai-memory",
      "dev-openai-qdrant",
      "dev-openai-pinecone",
      "dev-openai-weaviate",
      "dev-openai-milvus"
    ],
    "supportedProviderModes": ["llm:openai"]
  },
  "pricing": {
    "pricingModel": "FREE"
  },
  "permissions": {
    "contributesTemplate": true,
    "contributesShellPresentation": true,
    "contributesSurfaceCapabilities": true
  },
  "contributions": {
    "template": {
      "curatedModuleId": "commerce",
      "recommendedPluginIds": ["mkp-action-shopify-admin", "mkp-data-commerce-catalog", "mkp-automation-order-retention"],
      "shell": {
        "enabledModuleIds": ["docs", "products", "ai-search", "actions"],
        "defaultConversationMode": "guided-commerce"
      }
    }
  }
}'
where id = 'mkv-template-commerce-shell-v1';

update platform_marketplace_plugin_versions
set manifest_json = '{
  "schemaVersion": 1,
  "pluginId": "mkp-action-shopify-admin",
  "version": "1.0.0",
  "pluginType": "ACTION",
  "displayName": "Shopify Admin Actions",
  "capabilityProfiles": ["SURFACE"],
  "compatibility": {
    "minPlatformVersion": "0.1.0",
    "requiredCapabilities": ["actions"],
    "supportedDeploymentTargets": [
      "custom-start-from-scratch",
      "dev-openai-lucene",
      "dev-openai-memory",
      "dev-openai-qdrant",
      "dev-openai-pinecone",
      "dev-openai-weaviate",
      "dev-openai-milvus"
    ],
    "supportedAuthModes": ["PLATFORM_PROXY_SESSION", "PRIVATE_RUNTIME_BACKEND_MEDIATED"],
    "supportedProviderModes": ["llm:openai"]
  },
  "pricing": {
    "pricingModel": "ONE_OFF",
    "amount": 49.00,
    "currency": "USD"
  },
  "installForm": [
    {
      "id": "store",
      "label": "Store id",
      "type": "text",
      "required": true,
      "description": "Logical store identifier used by the action bundle."
    },
    {
      "id": "apiKey",
      "label": "Shopify admin secret ref",
      "type": "secretRef",
      "required": true,
      "description": "Secret reference containing the upstream admin API key."
    }
  ],
  "permissions": {
    "contributesActions": true,
    "contributesShellPresentation": true,
    "contributesSurfaceCapabilities": true,
    "requiresExternalHttpExecution": true,
    "requiresDeploymentSecrets": true
  },
  "contributions": {
    "actions": [
      {
        "actionId": "shopify-order-read",
        "displayName": "Read Shopify order",
        "readOnly": true,
        "adapterType": "connector-http",
        "route": {
          "method": "POST",
          "path": "/actions/execute"
        }
      },
      {
        "actionId": "shopify-order-cancel",
        "displayName": "Cancel Shopify order",
        "readOnly": false,
        "confirmationRequired": true,
        "adapterType": "connector-http",
        "route": {
          "method": "POST",
          "path": "/actions/execute"
        }
      }
    ],
    "shell": {
      "moduleRefs": ["actions"]
    }
  }
}'
where id = 'mkv-action-shopify-admin-v1';

update platform_marketplace_plugin_versions
set manifest_json = '{
  "schemaVersion": 1,
  "pluginId": "mkp-data-commerce-catalog",
  "version": "1.0.0",
  "pluginType": "DATA",
  "displayName": "Commerce Catalog Data",
  "capabilityProfiles": ["SURFACE"],
  "compatibility": {
    "minPlatformVersion": "0.1.0",
    "requiredCapabilities": ["knowledgeSources", "shellConfig"],
    "supportedDeploymentTargets": [
      "custom-start-from-scratch",
      "dev-openai-lucene",
      "dev-openai-memory",
      "dev-openai-qdrant",
      "dev-openai-pinecone",
      "dev-openai-weaviate",
      "dev-openai-milvus"
    ],
    "supportedAuthModes": [
      "PLATFORM_PROXY_SESSION",
      "PRIVATE_RUNTIME_BACKEND_MEDIATED",
      "PUBLIC_RUNTIME_AUTHENTICATED"
    ]
  },
  "pricing": {
    "pricingModel": "SUBSCRIPTION",
    "amount": 29.00,
    "currency": "USD",
    "billingInterval": "MONTHLY",
    "trialDays": 7
  },
  "installForm": [
    {
      "id": "scope",
      "label": "Source scope",
      "type": "select",
      "required": true,
      "options": ["refund-policy", "catalog", "all"]
    }
  ],
  "permissions": {
    "contributesKnowledgeSources": true,
    "contributesShellPresentation": true,
    "contributesSurfaceCapabilities": true,
    "requiresSharedDatasetAccess": true
  },
  "contributions": {
    "knowledgeSources": [
      {
        "sourceType": "shared-index",
        "sourceKey": "commerce-catalog",
        "attributionLabel": "Commerce catalog marketplace data",
        "authModes": [
          "PLATFORM_PROXY_SESSION",
          "PRIVATE_RUNTIME_BACKEND_MEDIATED",
          "PUBLIC_RUNTIME_AUTHENTICATED"
        ]
      }
    ],
    "shell": {
      "moduleRefs": ["docs", "products", "ai-search"]
    }
  }
}'
where id = 'mkv-data-commerce-catalog-v1';

insert into platform_marketplace_plugins (
    id,
    slug,
    display_name,
    plugin_type,
    publisher_slug,
    publisher_display_name,
    short_description,
    status,
    created_at,
    updated_at
) values
    (
        'mkp-template-support-desk-shell',
        'support-desk-shell',
        'Loom Support Desk Shell',
        'TEMPLATE',
        'loom',
        'Loom AI',
        'Support-oriented assistant shell starter with docs, actions, search, and support modules enabled by default.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkp-data-help-center',
        'help-center-data',
        'Help Center Data',
        'DATA',
        'loom',
        'Loom AI',
        'Shared FAQ and policy knowledge source for support-oriented deployments and operator copilots.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkp-action-notifications',
        'notifications-actions',
        'Notifications Actions',
        'ACTION',
        'loom',
        'Loom AI',
        'Email, SMS, and Slack notification actions packaged as deployment-level marketplace installs.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkp-automation-order-retention',
        'order-retention-automation',
        'Order Retention Automation',
        'AUTOMATION',
        'loom',
        'Loom AI',
        'Retention-focused workflow package for cancellation-request handling using platform-owned workflow configuration.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    );

insert into platform_marketplace_plugin_versions (
    id,
    plugin_id,
    version,
    release_channel,
    status,
    manifest_json,
    submitted_by_publisher_id,
    submitted_by_actor_id,
    reviewed_by_actor_id,
    reviewed_at,
    review_notes,
    bundle_sha256,
    created_at,
    published_at
) values
    (
        'mkv-template-support-desk-shell-v1',
        'mkp-template-support-desk-shell',
        '1.0.0',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-template-support-desk-shell",
          "version": "1.0.0",
          "pluginType": "TEMPLATE",
          "displayName": "Loom Support Desk Shell",
          "capabilityProfiles": ["SURFACE"],
          "compatibility": {
            "minPlatformVersion": "0.1.0",
            "requiredCapabilities": ["templates", "shellConfig"],
            "supportedDeploymentTargets": [
              "custom-start-from-scratch",
              "dev-openai-lucene",
              "dev-openai-memory",
              "dev-openai-qdrant",
              "dev-openai-pinecone",
              "dev-openai-weaviate",
              "dev-openai-milvus"
            ],
            "supportedProviderModes": ["llm:openai"]
          },
          "pricing": {
            "pricingModel": "FREE"
          },
          "permissions": {
            "contributesTemplate": true,
            "contributesShellPresentation": true,
            "contributesSurfaceCapabilities": true
          },
          "contributions": {
            "template": {
              "curatedModuleId": "support",
              "recommendedPluginIds": ["mkp-data-help-center", "mkp-action-notifications", "mkp-automation-order-retention"],
              "shell": {
                "enabledModuleIds": ["docs", "ai-search", "actions", "support"],
                "defaultConversationMode": "guided-support"
              }
            }
          }
        }',
        'mpub-loom',
        'system',
        'system',
        current_timestamp,
        'Seeded first-party marketplace plugin version.',
        'seeded-template-support-shell-v1',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkv-data-help-center-v1',
        'mkp-data-help-center',
        '1.0.0',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-data-help-center",
          "version": "1.0.0",
          "pluginType": "DATA",
          "displayName": "Help Center Data",
          "capabilityProfiles": ["SURFACE"],
          "compatibility": {
            "minPlatformVersion": "0.1.0",
            "requiredCapabilities": ["knowledgeSources", "shellConfig"],
            "supportedDeploymentTargets": [
              "custom-start-from-scratch",
              "dev-openai-lucene",
              "dev-openai-memory",
              "dev-openai-qdrant",
              "dev-openai-pinecone",
              "dev-openai-weaviate",
              "dev-openai-milvus"
            ],
            "supportedAuthModes": [
              "PLATFORM_PROXY_SESSION",
              "PRIVATE_RUNTIME_BACKEND_MEDIATED",
              "PUBLIC_RUNTIME_AUTHENTICATED"
            ]
          },
          "pricing": {
            "pricingModel": "FREE"
          },
          "installForm": [
            {
              "id": "scope",
              "label": "Help center scope",
              "type": "select",
              "required": true,
              "options": ["faq", "policy", "all"]
            }
          ],
          "permissions": {
            "contributesKnowledgeSources": true,
            "contributesShellPresentation": true,
            "contributesSurfaceCapabilities": true,
            "requiresSharedDatasetAccess": true
          },
          "contributions": {
            "knowledgeSources": [
              {
                "sourceType": "shared-index",
                "sourceKey": "help-center",
                "attributionLabel": "Help center marketplace data",
                "authModes": [
                  "PLATFORM_PROXY_SESSION",
                  "PRIVATE_RUNTIME_BACKEND_MEDIATED",
                  "PUBLIC_RUNTIME_AUTHENTICATED"
                ]
              }
            ],
            "shell": {
              "moduleRefs": ["docs", "ai-search", "support"]
            }
          }
        }',
        'mpub-loom',
        'system',
        'system',
        current_timestamp,
        'Seeded first-party marketplace plugin version.',
        'seeded-data-help-center-v1',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkv-action-notifications-v1',
        'mkp-action-notifications',
        '1.0.0',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-action-notifications",
          "version": "1.0.0",
          "pluginType": "ACTION",
          "displayName": "Notifications Actions",
          "capabilityProfiles": ["SURFACE"],
          "compatibility": {
            "minPlatformVersion": "0.1.0",
            "requiredCapabilities": ["actions"],
            "supportedDeploymentTargets": [
              "custom-start-from-scratch",
              "dev-openai-lucene",
              "dev-openai-memory",
              "dev-openai-qdrant",
              "dev-openai-pinecone",
              "dev-openai-weaviate",
              "dev-openai-milvus"
            ],
            "supportedAuthModes": ["PLATFORM_PROXY_SESSION", "PRIVATE_RUNTIME_BACKEND_MEDIATED"],
            "supportedProviderModes": ["llm:openai"]
          },
          "pricing": {
            "pricingModel": "ONE_OFF",
            "amount": 19.00,
            "currency": "USD"
          },
          "installForm": [
            {
              "id": "provider",
              "label": "Notification provider",
              "type": "select",
              "required": true,
              "options": ["sendgrid", "twilio", "slack"]
            },
            {
              "id": "credentialSecretRef",
              "label": "Credential secret ref",
              "type": "secretRef",
              "required": true
            },
            {
              "id": "defaultSender",
              "label": "Default sender",
              "type": "text",
              "required": false
            }
          ],
          "permissions": {
            "contributesActions": true,
            "contributesShellPresentation": true,
            "contributesSurfaceCapabilities": true,
            "requiresExternalHttpExecution": true,
            "requiresDeploymentSecrets": true
          },
          "contributions": {
            "actions": [
              {
                "actionId": "send-email",
                "displayName": "Send email",
                "readOnly": false,
                "confirmationRequired": true,
                "adapterType": "connector-http",
                "route": {
                  "method": "POST",
                  "path": "/actions/execute"
                }
              },
              {
                "actionId": "send-sms",
                "displayName": "Send SMS",
                "readOnly": false,
                "confirmationRequired": true,
                "adapterType": "connector-http",
                "route": {
                  "method": "POST",
                  "path": "/actions/execute"
                }
              }
            ],
            "shell": {
              "moduleRefs": ["actions"]
            }
          }
        }',
        'mpub-loom',
        'system',
        'system',
        current_timestamp,
        'Seeded first-party marketplace plugin version.',
        'seeded-action-notifications-v1',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkv-automation-order-retention-v1',
        'mkp-automation-order-retention',
        '1.0.0',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-automation-order-retention",
          "version": "1.0.0",
          "pluginType": "AUTOMATION",
          "displayName": "Order Retention Automation",
          "capabilityProfiles": ["POLICY_LOGIC"],
          "compatibility": {
            "minPlatformVersion": "0.1.0",
            "requiredCapabilities": ["automation", "actions"],
            "supportedDeploymentTargets": [
              "custom-start-from-scratch",
              "dev-openai-lucene",
              "dev-openai-memory",
              "dev-openai-qdrant",
              "dev-openai-pinecone",
              "dev-openai-weaviate",
              "dev-openai-milvus"
            ]
          },
          "pricing": {
            "pricingModel": "FREE"
          },
          "installForm": [
            {
              "id": "discountPercent",
              "label": "Retention discount percent",
              "type": "number",
              "required": false
            },
            {
              "id": "cooldownDays",
              "label": "Follow-up cooldown days",
              "type": "number",
              "required": false
            }
          ],
          "permissions": {
            "contributesAutomation": true,
            "contributesPolicyLogicCapabilities": true
          },
          "contributions": {
            "automation": {
              "triggers": [
                {
                  "id": "order-cancel-requested",
                  "eventType": "order.cancel.requested"
                }
              ],
              "actions": [
                {
                  "id": "offer-retention-discount",
                  "actionRef": "offer_order_discount"
                }
              ],
              "workflows": [
                {
                  "id": "order-cancel-retention",
                  "triggerRefs": ["order-cancel-requested"],
                  "actionRefs": ["offer-retention-discount", "shopify-order-cancel"],
                  "template": {
                    "name": "Order cancellation retention"
                  }
                }
              ],
              "schedules": [
                {
                  "id": "retention-follow-up",
                  "workflowRef": "order-cancel-retention",
                  "intervalMinutes": 1440
                }
              ]
            }
          }
        }',
        'mpub-loom',
        'system',
        'system',
        current_timestamp,
        'Seeded first-party marketplace plugin version.',
        'seeded-automation-retention-v1',
        current_timestamp,
        current_timestamp
    );
