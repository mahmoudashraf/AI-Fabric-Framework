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
        'mkp-data-document-knowledge-s3',
        'document-knowledge-customer-storage',
        'Document Knowledge - Customer Storage',
        'DATA',
        'loom',
        'Loom AI',
        'Deployment-local document indexing from customer-owned S3-compatible storage.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkp-data-document-knowledge-mounted-demo',
        'document-knowledge-mounted-demo',
        'Document Knowledge - Mounted Demo Folder',
        'DATA',
        'loom',
        'Loom AI',
        'Bounded mounted-folder document indexing for internal canaries and small demos only.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkp-template-document-knowledge-assistant',
        'document-knowledge-assistant-deployment',
        'Document Knowledge Assistant',
        'TEMPLATE',
        'loom',
        'Loom AI',
        'Conversational deployment with governed file discovery, preview, indexing, replacement, retrieval evidence, and exact index deletion.',
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
        'mkv-data-document-knowledge-s3-v1',
        'mkp-data-document-knowledge-s3',
        '1.0.0',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-data-document-knowledge-s3",
          "version": "1.0.0",
          "pluginType": "DATA",
          "displayName": "Document Knowledge - Customer Storage",
          "compatibility": {
            "requiredCapabilities": ["knowledgeSources"],
            "supportedDeploymentTargets": ["custom-start-from-scratch"],
            "supportedAuthModes": ["PRIVATE_RUNTIME_BACKEND_MEDIATED", "PUBLIC_RUNTIME_AUTHENTICATED"]
          },
          "pricing": {"pricingModel": "FREE"},
          "installForm": [
            {
              "id": "documentStorageBindingRef",
              "label": "Document storage binding",
              "type": "text",
              "required": true,
              "description": "Deployment and target-profile scoped customer storage binding created in Document Knowledge operations."
            }
          ],
          "permissions": {
            "contributesKnowledgeSources": true,
            "requiresSharedDatasetAccess": false,
            "requiresDeploymentSecrets": false
          },
          "contributions": {
            "entityConfig": {
              "ai-entities": {
                "document": {
                  "indexing": {"enabled": true, "max-characters": 8000},
                  "analysis": {"enabled": false, "after": []},
                  "searchable-fields": [
                    {
                      "name": "content",
                      "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                      "preprocessing": "CLEAN",
                      "max-length": 8000,
                      "priority": 100,
                      "required": true
                    }
                  ],
                  "metadata-fields": [
                    {"name": "originalFilename", "data-type": "STRING", "destinations": ["VECTOR_METADATA", "LLM_CONTEXT", "API_RESPONSE"], "priority": 90, "required": true, "sanitize-pii": false},
                    {"name": "sourceCategory", "data-type": "STRING", "destinations": ["VECTOR_METADATA", "LLM_CONTEXT"], "priority": 70, "required": false, "sanitize-pii": false},
                    {"name": "tenantId", "data-type": "ID", "destinations": ["VECTOR_METADATA"], "priority": 100, "required": true, "sanitize-pii": false},
                    {"name": "customerId", "data-type": "ID", "destinations": ["VECTOR_METADATA"], "priority": 100, "required": true, "sanitize-pii": false},
                    {"name": "deploymentId", "data-type": "ID", "destinations": ["VECTOR_METADATA"], "priority": 100, "required": true, "sanitize-pii": false},
                    {"name": "datasetId", "data-type": "ID", "destinations": ["VECTOR_METADATA"], "priority": 100, "required": true, "sanitize-pii": false},
                    {"name": "knowledgeSourceHandleRef", "data-type": "ID", "destinations": ["VECTOR_METADATA"], "priority": 100, "required": true, "sanitize-pii": false}
                  ]
                }
              }
            },
            "datasets": [
              {
                "datasetId": "document-knowledge",
                "entityType": "document",
                "storageScope": "CUSTOMER_MANAGED",
                "sharingScope": "DEPLOYMENT_ONLY",
                "ingestionMode": "EXTERNAL_DOCUMENT_STORAGE",
                "updateStrategy": "VERSIONED_REPLACE",
                "vectorizationProfile": "document-knowledge-default",
                "handleTemplate": "documents/{deploymentId}/document-knowledge",
                "sourceConnector": {
                  "connectorType": "S3_COMPATIBLE_OBJECT_STORAGE",
                  "storageOwnership": "CUSTOMER_MANAGED",
                  "bindingRefField": "documentStorageBindingRef",
                  "allowedOperations": ["LIST", "READ", "HEAD"],
                  "deleteSourceOnRemoval": false
                },
                "documentPolicy": {
                  "allowedMediaTypes": ["text/plain", "application/json"],
                  "allowedExtensions": [".txt", ".json"],
                  "jsonContentKeys": ["content", "text", "body"],
                  "maxSourceBytes": 1048576,
                  "maxSources": 100,
                  "maxTotalIndexedBytes": 104857600,
                  "maxChunksPerSource": 100,
                  "maxChunkCharacters": 8000,
                  "maxTotalCharacters": 250000,
                  "previewMaxChunks": 10,
                  "previewMaxCharactersPerChunk": 500,
                  "evidenceRetentionDays": 30,
                  "commandRetentionDays": 30,
                  "retentionBatchSize": 100,
                  "allowedMetadataKeys": ["originalFilename", "locale", "sourceCategory"],
                  "initialIndexRequiresConfirmation": true,
                  "trustedAutoIndexingAllowed": false
                }
              }
            ],
            "knowledgeSources": [
              {
                "sourceKey": "document-knowledge",
                "sourceType": "deployment-private-vector",
                "adapterType": "deployment-private-vector",
                "datasetRef": "document-knowledge",
                "entityType": "document",
                "attributionLabel": "Approved document knowledge",
                "filters": {"datasetId": "document-knowledge"},
                "authModes": ["PRIVATE_RUNTIME_BACKEND_MEDIATED", "PUBLIC_RUNTIME_AUTHENTICATED"]
              }
            ]
          }
        }',
        'mpub-loom',
        'system',
        'system',
        current_timestamp,
        'First-party document DATA contract reviewed for customer-owned S3-compatible storage and deployment-local processing.',
        'seeded-document-knowledge-s3-v1',
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
        'mkv-data-document-knowledge-mounted-demo-v1',
        'mkp-data-document-knowledge-mounted-demo',
        '1.0.0',
        'DEMO',
        'PUBLISHED',
        replace(
          replace(
            replace(
              (select manifest_json from platform_marketplace_plugin_versions where id = 'mkv-data-document-knowledge-s3-v1'),
              'mkp-data-document-knowledge-s3',
              'mkp-data-document-knowledge-mounted-demo'
            ),
            'Document Knowledge - Customer Storage',
            'Document Knowledge - Mounted Demo Folder'
          ),
          'S3_COMPATIBLE_OBJECT_STORAGE',
          'MOUNTED_FOLDER'
        ),
        'mpub-loom',
        'system',
        'system',
        current_timestamp,
        'Demo-only document DATA contract reviewed for a bounded operator-mounted folder; not a managed customer storage claim.',
        'seeded-document-knowledge-mounted-demo-v1',
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
        'mkv-template-document-knowledge-assistant-v1',
        'mkp-template-document-knowledge-assistant',
        '1.0.0',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-template-document-knowledge-assistant",
          "version": "1.0.0",
          "pluginType": "TEMPLATE",
          "displayName": "Document Knowledge Assistant",
          "compatibility": {
            "requiredCapabilities": ["templates"],
            "supportedDeploymentTargets": ["custom-start-from-scratch"]
          },
          "pricing": {"pricingModel": "FREE"},
          "permissions": {"contributesTemplate": true, "contributesShellPresentation": true},
          "contributions": {
            "template": {
              "templateId": "custom-start-from-scratch",
              "deploymentBehavior": {
                "type": "CONVERSATIONAL",
                "contractVersion": 1,
                "requiredRuntimeCapabilityIds": ["ai-fabric-core", "ai-fabric-chat-session"],
                "allowedExecutionExtensions": ["GOVERNED_RESOLVER", "HUMAN_REVIEW"],
                "allowedChannelBindings": ["BACKEND_API", "DOCKED_COMPOSER", "MAX_MODE", "INLINE_ASSISTANT", "QUERY_ONCE"],
                "verificationPackIds": ["conversational-behavior-v1"]
              },
              "security": {"authzMode": "ALLOW_VERIFIED"},
              "requiredPluginRefs": ["mkp-data-document-knowledge-s3@1.0.0"],
              "shell": {
                "greeting": {"title": "Document Assistant", "message": "Ask a question grounded in approved documents."},
                "starterPrompts": [
                  {"id": "document-question", "label": "Ask approved documents", "query": "Summarize the relevant approved document evidence for my question."}
                ],
                "defaultConversationMode": "conversational"
              }
            }
          }
        }',
        'mpub-loom',
        'system',
        'system',
        current_timestamp,
        'First-party Document Knowledge template reviewed with the production customer-storage DATA contract.',
        'seeded-template-document-knowledge-assistant-v1',
        current_timestamp,
        current_timestamp
    );
