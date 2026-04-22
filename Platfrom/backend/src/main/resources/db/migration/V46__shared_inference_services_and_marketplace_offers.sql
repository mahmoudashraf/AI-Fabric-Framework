insert into platform_managed_inference_services (
    id,
    service_ref,
    display_name,
    service_kind,
    deployment_mode,
    provider_type,
    protocol_type,
    model_id,
    environment_scope,
    tier_scope,
    desired_replicas,
    actual_replicas,
    min_replicas,
    max_replicas,
    autoscaling_mode,
    health_path,
    secret_name,
    status,
    details_json,
    created_at,
    updated_at
)
select
    'pmis-shared-embeddings-standard',
    'shared-embeddings-standard',
    'Shared Embeddings Standard',
    'SHARED_EMBEDDING_SERVICE',
    'SHARED_PLATFORM_SERVICE',
    'openai',
    'OPENAI_COMPATIBLE',
    'bge-small-en-v1.5',
    'dev',
    'standard',
    1,
    0,
    1,
    6,
    'MANUAL',
    '/actuator/health',
    'MANAGED_SHARED_INFERENCE_SHARED_EMBEDDINGS_STANDARD_API_KEY',
    'CREATED',
    '{}',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_managed_inference_services where service_ref = 'shared-embeddings-standard'
);

insert into platform_managed_inference_services (
    id,
    service_ref,
    display_name,
    service_kind,
    deployment_mode,
    provider_type,
    protocol_type,
    model_id,
    environment_scope,
    tier_scope,
    desired_replicas,
    actual_replicas,
    min_replicas,
    max_replicas,
    autoscaling_mode,
    health_path,
    secret_name,
    status,
    details_json,
    created_at,
    updated_at
)
select
    'pmis-shared-ollama-orchestration',
    'shared-ollama-orchestration',
    'Shared Ollama Orchestration',
    'SHARED_OLLAMA_SERVICE',
    'SHARED_PLATFORM_SERVICE',
    'openai',
    'OPENAI_COMPATIBLE',
    'llama3.1:8b',
    'dev',
    'standard',
    1,
    0,
    1,
    4,
    'MANUAL',
    '/api/tags',
    'MANAGED_SHARED_INFERENCE_SHARED_OLLAMA_ORCHESTRATION_API_KEY',
    'CREATED',
    '{}',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_managed_inference_services where service_ref = 'shared-ollama-orchestration'
);

insert into platform_managed_inference_endpoints (
    id,
    profile_ref,
    service_id,
    endpoint_purpose,
    display_name,
    provider_type,
    protocol_type,
    base_url,
    deployment_name,
    api_version,
    secret_name,
    status,
    created_at,
    updated_at
)
select
    'pmie-shared-embeddings-standard',
    'shared-embeddings-standard',
    'pmis-shared-embeddings-standard',
    'EMBEDDING',
    'Shared Embeddings Standard',
    'openai',
    'OPENAI_COMPATIBLE',
    null,
    null,
    null,
    'MANAGED_SHARED_INFERENCE_SHARED_EMBEDDINGS_STANDARD_API_KEY',
    'CREATED',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_managed_inference_endpoints where profile_ref = 'shared-embeddings-standard'
);

insert into platform_managed_inference_endpoints (
    id,
    profile_ref,
    service_id,
    endpoint_purpose,
    display_name,
    provider_type,
    protocol_type,
    base_url,
    deployment_name,
    api_version,
    secret_name,
    status,
    created_at,
    updated_at
)
select
    'pmie-shared-ollama-orchestration',
    'shared-ollama-orchestration',
    'pmis-shared-ollama-orchestration',
    'ORCHESTRATION',
    'Shared Ollama Orchestration',
    'openai',
    'OPENAI_COMPATIBLE',
    null,
    null,
    null,
    'MANAGED_SHARED_INFERENCE_SHARED_OLLAMA_ORCHESTRATION_API_KEY',
    'CREATED',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_managed_inference_endpoints where profile_ref = 'shared-ollama-orchestration'
);

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
)
select 'mkp-inference-shared-embeddings', 'shared-embeddings-profile', 'Shared Embeddings Profile', 'INFERENCE_PROFILE', 'loom', 'Loom AI', 'First-party shared embedding worker backed by a platform-managed ONNX service.', 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from platform_marketplace_plugins where id = 'mkp-inference-shared-embeddings');

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
)
select 'mkp-inference-shared-ollama-orchestration', 'shared-ollama-orchestration-profile', 'Shared Ollama Orchestration Profile', 'INFERENCE_PROFILE', 'loom', 'Loom AI', 'Platform-managed shared Ollama orchestration with cloud generation fallback.', 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from platform_marketplace_plugins where id = 'mkp-inference-shared-ollama-orchestration');

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
)
select 'mkp-inference-dedicated-embedding-worker', 'dedicated-embedding-worker-profile', 'Dedicated Embedding Worker Profile', 'INFERENCE_PROFILE', 'loom', 'Loom AI', 'Provision a deployment-scoped embedding worker when bundled ONNX is not enough.', 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from platform_marketplace_plugins where id = 'mkp-inference-dedicated-embedding-worker');

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
    'mkv-inference-shared-embeddings-v1',
    'mkp-inference-shared-embeddings',
    '1.0.0',
    'GA',
    'PUBLISHED',
    '{
      "schemaVersion": 1,
      "pluginId": "mkp-inference-shared-embeddings",
      "version": "1.0.0",
      "pluginType": "INFERENCE_PROFILE",
      "displayName": "Shared Embeddings Profile",
      "compatibility": {
        "minPlatformVersion": "0.1.0",
        "requiredCapabilities": ["providers"],
        "supportedDeploymentTargets": ["custom-start-from-scratch", "dev-openai-lucene", "dev-openai-memory", "dev-openai-qdrant", "dev-openai-pinecone", "dev-openai-weaviate", "dev-openai-milvus"],
        "supportedProviderModes": ["embedding:openai"]
      },
      "pricing": {
        "pricingModel": "SUBSCRIPTION",
        "amount": 9.00,
        "currency": "USD",
        "billingInterval": "MONTHLY",
        "trialDays": 7
      },
      "permissions": {
        "contributesProviders": true
      },
      "contributions": {
        "inferenceProfile": {
          "profileId": "shared-embeddings",
          "embedding": {
            "provider": "openai",
            "managedServiceRef": "shared-embeddings-standard",
            "serviceMode": "SHARED_PLATFORM_SERVICE",
            "model": "text-embedding-bge-small-en-v1.5"
          }
        }
      }
    }',
    current_timestamp,
    current_timestamp
where not exists (select 1 from platform_marketplace_plugin_versions where id = 'mkv-inference-shared-embeddings-v1');

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
    'mkv-inference-shared-ollama-orchestration-v1',
    'mkp-inference-shared-ollama-orchestration',
    '1.0.0',
    'GA',
    'PUBLISHED',
    '{
      "schemaVersion": 1,
      "pluginId": "mkp-inference-shared-ollama-orchestration",
      "version": "1.0.0",
      "pluginType": "INFERENCE_PROFILE",
      "displayName": "Shared Ollama Orchestration Profile",
      "compatibility": {
        "minPlatformVersion": "0.1.0",
        "requiredCapabilities": ["providers"],
        "supportedDeploymentTargets": ["custom-start-from-scratch", "dev-openai-lucene", "dev-openai-memory", "dev-openai-qdrant", "dev-openai-pinecone", "dev-openai-weaviate", "dev-openai-milvus"],
        "supportedProviderModes": ["llm:openai", "embedding:onnx", "embedding:openai"]
      },
      "pricing": {
        "pricingModel": "SUBSCRIPTION",
        "amount": 19.00,
        "currency": "USD",
        "billingInterval": "MONTHLY",
        "trialDays": 7
      },
      "permissions": {
        "contributesProviders": true
      },
      "contributions": {
        "inferenceProfile": {
          "profileId": "shared-ollama-orchestration",
          "orchestration": {
            "provider": "openai",
            "managedServiceRef": "shared-ollama-orchestration",
            "model": "llama3.1:8b",
            "timeout": 45
          },
          "generation": {
            "provider": "openai",
            "endpointProfileRef": "openai-cloud-default",
            "model": "gpt-4.1-mini",
            "timeout": 60
          }
        }
      }
    }',
    current_timestamp,
    current_timestamp
where not exists (select 1 from platform_marketplace_plugin_versions where id = 'mkv-inference-shared-ollama-orchestration-v1');

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
    'mkv-inference-dedicated-embedding-worker-v1',
    'mkp-inference-dedicated-embedding-worker',
    '1.0.0',
    'GA',
    'PUBLISHED',
    '{
      "schemaVersion": 1,
      "pluginId": "mkp-inference-dedicated-embedding-worker",
      "version": "1.0.0",
      "pluginType": "INFERENCE_PROFILE",
      "displayName": "Dedicated Embedding Worker Profile",
      "compatibility": {
        "minPlatformVersion": "0.1.0",
        "requiredCapabilities": ["providers"],
        "supportedDeploymentTargets": ["custom-start-from-scratch", "dev-openai-lucene", "dev-openai-memory", "dev-openai-qdrant", "dev-openai-pinecone", "dev-openai-weaviate", "dev-openai-milvus"],
        "supportedProviderModes": ["embedding:openai"]
      },
      "pricing": {
        "pricingModel": "ONE_OFF",
        "amount": 39.00,
        "currency": "USD"
      },
      "permissions": {
        "contributesProviders": true
      },
      "contributions": {
        "inferenceProfile": {
          "profileId": "dedicated-embedding-worker",
          "embedding": {
            "provider": "openai",
            "serviceMode": "DEPLOYMENT_DEDICATED_SERVICE",
            "model": "text-embedding-bge-small-en-v1.5"
          }
        }
      }
    }',
    current_timestamp,
    current_timestamp
where not exists (select 1 from platform_marketplace_plugin_versions where id = 'mkv-inference-dedicated-embedding-worker-v1');
