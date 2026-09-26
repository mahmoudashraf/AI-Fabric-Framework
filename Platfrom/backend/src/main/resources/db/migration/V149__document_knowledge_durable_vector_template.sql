update platform_marketplace_plugin_versions
set status = 'DEPRECATED'
where id = 'mkv-data-document-knowledge-s3-v1'
  and status = 'PUBLISHED';

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
    'mkv-data-document-knowledge-s3-v1-1',
    plugin_id,
    '1.1.0',
    release_channel,
    'PUBLISHED',
    replace(
      replace(
        replace(
          manifest_json,
          '"version": "1.0.0"',
          '"version": "1.1.0"'
        ),
        '"supportedDeploymentTargets": ["custom-start-from-scratch"]',
        '"supportedDeploymentTargets": ["custom-start-from-scratch", "dev-openai-qdrant", "dev-openai-pinecone", "dev-openai-weaviate", "dev-openai-milvus", "dev-azure-pinecone", "dev-cohere-weaviate"]'
      ),
      'Deployment-local document indexing from customer-owned S3-compatible storage.',
      'Deployment-local document indexing from customer-owned S3-compatible storage using durable external vector storage.'
    ),
    submitted_by_publisher_id,
    submitted_by_actor_id,
    reviewed_by_actor_id,
    current_timestamp,
    'Customer-storage Document Knowledge contract updated to require a durable external vector deployment target.',
    'seeded-document-knowledge-s3-v1-1',
    current_timestamp,
    current_timestamp
from platform_marketplace_plugin_versions
where id = 'mkv-data-document-knowledge-s3-v1'
  and not exists (
      select 1
      from platform_marketplace_plugin_versions
      where id = 'mkv-data-document-knowledge-s3-v1-1'
  );

update platform_marketplace_plugin_versions
set status = 'DEPRECATED'
where id = 'mkv-template-document-knowledge-assistant-v1'
  and status = 'PUBLISHED';

insert into platform_marketplace_plugin_versions (
    id,
    plugin_id,
    version,
    release_channel,
    status,
    manifest_json,
    created_at,
    published_at
)
select
    'mkv-template-document-knowledge-assistant-v1-1',
    'mkp-template-document-knowledge-assistant',
    '1.1.0',
    'GA',
    'PUBLISHED',
    '{
      "schemaVersion": 1,
      "pluginId": "mkp-template-document-knowledge-assistant",
      "version": "1.1.0",
      "pluginType": "TEMPLATE",
      "displayName": "Document Knowledge Assistant",
      "compatibility": {
        "requiredCapabilities": ["templates"],
        "supportedDeploymentTargets": ["dev-openai-pinecone"]
      },
      "pricing": {"pricingModel": "FREE"},
      "permissions": {"contributesTemplate": true, "contributesShellPresentation": true},
      "contributions": {
        "template": {
          "templateId": "dev-openai-pinecone",
          "deploymentBehavior": {
            "type": "CONVERSATIONAL",
            "contractVersion": 1,
            "requiredRuntimeCapabilityIds": ["ai-fabric-core", "ai-fabric-chat-session"],
            "allowedExecutionExtensions": ["GOVERNED_RESOLVER", "HUMAN_REVIEW"],
            "allowedChannelBindings": ["BACKEND_API", "DOCKED_COMPOSER", "MAX_MODE", "INLINE_ASSISTANT", "QUERY_ONCE"],
            "verificationPackIds": ["conversational-behavior-v1"]
          },
          "security": {"authzMode": "ALLOW_VERIFIED"},
          "requiredPluginRefs": ["mkp-data-document-knowledge-s3@1.1.0"],
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
    current_timestamp,
    current_timestamp
where not exists (
      select 1
      from platform_marketplace_plugin_versions
      where id = 'mkv-template-document-knowledge-assistant-v1-1'
);
