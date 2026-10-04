update platform_marketplace_plugins
set display_name = 'Dealership Knowledge - Customer Storage',
    short_description = 'Approved public dealership policies and operations indexed from customer-owned S3-compatible storage.',
    status = 'ACTIVE',
    updated_at = current_timestamp
where id = 'mkp-data-dealership-knowledge-v1';

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
) values (
    'mkp-data-dealership-knowledge-mounted-demo-v1',
    'dealership-knowledge-mounted-demo-v1',
    'Dealership Knowledge - Mounted Demo Folder',
    'DATA',
    'loom',
    'Loom AI',
    'Approved public dealership policies and operations from a bounded demo-only mounted folder.',
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
) values (
    'mkv-data-dealership-knowledge-v1-0',
    'mkp-data-dealership-knowledge-v1',
    '1.0.0',
    'GA',
    'PUBLISHED',
    '{
      "schemaVersion": 1,
      "pluginId": "mkp-data-dealership-knowledge-v1",
      "version": "1.0.0",
      "pluginType": "DATA",
      "displayName": "Dealership Knowledge - Customer Storage",
      "compatibility": {
        "requiredCapabilities": ["knowledgeSources"],
        "supportedDeploymentTargets": ["custom-start-from-scratch", "dev-openai-qdrant", "dev-openai-pinecone", "dev-openai-weaviate", "dev-openai-milvus", "dev-azure-pinecone", "dev-cohere-weaviate"],
        "supportedAuthModes": ["PRIVATE_RUNTIME_BACKEND_MEDIATED", "PUBLIC_RUNTIME_AUTHENTICATED", "PUBLIC_RUNTIME_ANONYMOUS"]
      },
      "pricing": {"pricingModel": "FREE"},
      "installForm": [
        {
          "id": "documentStorageBindingRef",
          "label": "Document storage binding",
          "type": "text",
          "required": true,
          "description": "Deployment and target-profile scoped customer storage binding created in Document Knowledge operations."
        },
        {
          "id": "publicContentApprovalConfirmed",
          "label": "Public content approval confirmed",
          "type": "boolean",
          "required": true,
          "description": "Confirms that only dealership-owned material approved for website visitors will be registered."
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
                {"name": "sourceCategory", "data-type": "STRING", "destinations": ["VECTOR_METADATA", "LLM_CONTEXT", "API_RESPONSE"], "priority": 80, "required": true, "sanitize-pii": false},
                {"name": "publicationStatus", "data-type": "STRING", "destinations": ["VECTOR_METADATA", "LLM_CONTEXT"], "priority": 100, "required": true, "sanitize-pii": false},
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
            "vectorizationProfile": "dealership-public-document-knowledge",
            "handleTemplate": "documents/{deploymentId}/dealership-public-knowledge",
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
              "allowedMetadataKeys": ["originalFilename", "locale", "sourceCategory", "publicationStatus"],
              "initialIndexRequiresConfirmation": true,
              "trustedAutoIndexingAllowed": false
            }
          }
        ],
        "knowledgeSources": [
          {
            "sourceKey": "dealership-public-document-knowledge",
            "sourceType": "deployment-private-vector",
            "adapterType": "deployment-private-vector",
            "datasetRef": "document-knowledge",
            "entityType": "document",
            "attributionLabel": "Approved dealership policy and operations documents",
            "filters": {"datasetId": "document-knowledge", "publicationStatus": "PUBLIC_APPROVED"},
            "authModes": ["PRIVATE_RUNTIME_BACKEND_MEDIATED", "PUBLIC_RUNTIME_AUTHENTICATED", "PUBLIC_RUNTIME_ANONYMOUS"]
          }
        ]
      }
    }',
    'mpub-loom',
    'system',
    'system',
    current_timestamp,
    'First-party public-safe dealership document contract reviewed for customer-owned storage, explicit source approval, and deployment-local retrieval.',
    'seeded-dealership-knowledge-s3-v1',
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
)
select
    'mkv-data-dealership-knowledge-mounted-demo-v1-0',
    'mkp-data-dealership-knowledge-mounted-demo-v1',
    '1.0.0',
    'DEMO',
    'PUBLISHED',
    jsonb_set(
      jsonb_set(
        jsonb_set(
          jsonb_set(
            manifest_json::jsonb,
            '{pluginId}',
            '"mkp-data-dealership-knowledge-mounted-demo-v1"'::jsonb
          ),
          '{displayName}',
          '"Dealership Knowledge - Mounted Demo Folder"'::jsonb
        ),
        '{compatibility,supportedDeploymentTargets}',
        '["custom-start-from-scratch"]'::jsonb
      ),
      '{contributions,datasets,0,sourceConnector,connectorType}',
      '"MOUNTED_FOLDER"'::jsonb
    )::text,
    'mpub-loom',
    'system',
    'system',
    current_timestamp,
    'Demo-only public-safe dealership document contract reviewed for a bounded operator-mounted folder. It is not a managed customer storage claim.',
    'seeded-dealership-knowledge-mounted-demo-v1',
    current_timestamp,
    current_timestamp
from platform_marketplace_plugin_versions
where id = 'mkv-data-dealership-knowledge-v1-0';
