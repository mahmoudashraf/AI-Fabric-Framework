create table platform_managed_inference_endpoints (
    id varchar(64) primary key,
    profile_ref varchar(128) not null,
    display_name varchar(255) not null,
    provider_type varchar(64) not null,
    base_url text,
    deployment_name varchar(255),
    api_version varchar(100),
    secret_name varchar(255),
    status varchar(64) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create unique index uk_platform_managed_inference_endpoints_profile_ref
    on platform_managed_inference_endpoints (profile_ref);

insert into platform_managed_inference_endpoints (
    id,
    profile_ref,
    display_name,
    provider_type,
    base_url,
    deployment_name,
    api_version,
    secret_name,
    status,
    created_at,
    updated_at
) values
    (
        'pmie-openai-cloud-default',
        'openai-cloud-default',
        'OpenAI Cloud Default',
        'openai',
        'https://api.openai.com/v1',
        null,
        null,
        'OPENAI_API_KEY',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'pmie-openai-cloud-orchestration',
        'openai-cloud-orchestration',
        'OpenAI Cloud Orchestration',
        'openai',
        'https://api.openai.com/v1',
        null,
        null,
        'OPENAI_API_KEY',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'pmie-openai-cloud-premium',
        'openai-cloud-premium',
        'OpenAI Cloud Premium',
        'openai',
        'https://api.openai.com/v1',
        null,
        null,
        'OPENAI_API_KEY',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'pmie-onnx-bundled',
        'onnx-bundled',
        'Bundled ONNX Embeddings',
        'onnx',
        null,
        null,
        null,
        null,
        'ACTIVE',
        current_timestamp,
        current_timestamp
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
) values
    (
        'mkp-inference-local-embeddings',
        'local-embeddings-profile',
        'Local Embeddings Profile',
        'INFERENCE_PROFILE',
        'loom',
        'Loom AI',
        'First-party ONNX embedding profile for low-cost retrieval on new deployments.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkp-inference-optimized-orchestration',
        'optimized-orchestration-profile',
        'Optimized Orchestration Profile',
        'INFERENCE_PROFILE',
        'loom',
        'Loom AI',
        'Managed profile that keeps orchestration and generation on fast OpenAI cloud defaults with bundled ONNX embeddings.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkp-inference-premium-hybrid',
        'premium-hybrid-profile',
        'Premium Hybrid Response Profile',
        'INFERENCE_PROFILE',
        'loom',
        'Loom AI',
        'Managed profile that routes orchestration and generation to premium OpenAI endpoints and upgrades embeddings to cloud quality.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkp-inference-byok-openai',
        'bring-your-own-openai-profile',
        'Bring Your Own OpenAI Profile',
        'INFERENCE_PROFILE',
        'loom',
        'Loom AI',
        'Customer-supplied OpenAI-compatible inference profile that compiles directly into deployment provider configuration.',
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
    created_at,
    published_at
) values
    (
        'mkv-inference-local-embeddings-v1',
        'mkp-inference-local-embeddings',
        '1.0.0',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-inference-local-embeddings",
          "version": "1.0.0",
          "pluginType": "INFERENCE_PROFILE",
          "displayName": "Local Embeddings Profile",
          "compatibility": {
            "minPlatformVersion": "0.1.0",
            "requiredCapabilities": ["providers"],
            "supportedDeploymentTargets": [
              "custom-start-from-scratch",
              "dev-openai-lucene",
              "dev-openai-memory",
              "dev-openai-qdrant",
              "dev-openai-pinecone",
              "dev-openai-weaviate",
              "dev-openai-milvus"
            ],
            "supportedProviderModes": ["embedding:onnx"]
          },
          "pricing": {
            "pricingModel": "FREE"
          },
          "permissions": {
            "contributesProviders": true
          },
          "contributions": {
            "inferenceProfile": {
              "profileId": "local-embeddings",
              "embedding": {
                "provider": "onnx",
                "endpointProfileRef": "onnx-bundled",
                "modelAlias": "bge-small-en-v1.5",
                "maxSequenceLength": 512,
                "useGpu": false
              }
            }
          }
        }',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkv-inference-optimized-orchestration-v1',
        'mkp-inference-optimized-orchestration',
        '1.0.0',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-inference-optimized-orchestration",
          "version": "1.0.0",
          "pluginType": "INFERENCE_PROFILE",
          "displayName": "Optimized Orchestration Profile",
          "compatibility": {
            "minPlatformVersion": "0.1.0",
            "requiredCapabilities": ["providers"],
            "supportedDeploymentTargets": [
              "custom-start-from-scratch",
              "dev-openai-lucene",
              "dev-openai-memory",
              "dev-openai-qdrant",
              "dev-openai-pinecone",
              "dev-openai-weaviate",
              "dev-openai-milvus"
            ],
            "supportedProviderModes": ["llm:openai", "embedding:onnx"]
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
              "profileId": "optimized-orchestration",
              "orchestration": {
                "provider": "openai",
                "endpointProfileRef": "openai-cloud-orchestration",
                "model": "gpt-4.1-mini",
                "maxTokens": 700,
                "temperature": 0.1,
                "timeout": 30
              },
              "generation": {
                "provider": "openai",
                "endpointProfileRef": "openai-cloud-default",
                "model": "gpt-4.1-mini",
                "maxTokens": 1800,
                "temperature": 0.3,
                "timeout": 60
              },
              "embedding": {
                "provider": "onnx",
                "endpointProfileRef": "onnx-bundled",
                "modelAlias": "bge-small-en-v1.5",
                "maxSequenceLength": 512,
                "useGpu": false
              }
            }
          }
        }',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkv-inference-premium-hybrid-v1',
        'mkp-inference-premium-hybrid',
        '1.0.0',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-inference-premium-hybrid",
          "version": "1.0.0",
          "pluginType": "INFERENCE_PROFILE",
          "displayName": "Premium Hybrid Response Profile",
          "compatibility": {
            "minPlatformVersion": "0.1.0",
            "requiredCapabilities": ["providers"],
            "supportedDeploymentTargets": [
              "custom-start-from-scratch",
              "dev-openai-lucene",
              "dev-openai-memory",
              "dev-openai-qdrant",
              "dev-openai-pinecone",
              "dev-openai-weaviate",
              "dev-openai-milvus"
            ],
            "supportedProviderModes": ["llm:openai", "embedding:openai"]
          },
          "pricing": {
            "pricingModel": "SUBSCRIPTION",
            "amount": 99.00,
            "currency": "USD",
            "billingInterval": "MONTHLY",
            "trialDays": 7
          },
          "permissions": {
            "contributesProviders": true
          },
          "contributions": {
            "inferenceProfile": {
              "profileId": "premium-hybrid",
              "orchestration": {
                "provider": "openai",
                "endpointProfileRef": "openai-cloud-orchestration",
                "model": "gpt-4.1-mini",
                "maxTokens": 900,
                "temperature": 0.1,
                "timeout": 45
              },
              "generation": {
                "provider": "openai",
                "endpointProfileRef": "openai-cloud-premium",
                "model": "gpt-4.1",
                "maxTokens": 2600,
                "temperature": 0.35,
                "timeout": 90
              },
              "embedding": {
                "provider": "openai",
                "endpointProfileRef": "openai-cloud-default",
                "model": "text-embedding-3-large",
                "dimensions": 3072
              }
            }
          }
        }',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkv-inference-byok-openai-v1',
        'mkp-inference-byok-openai',
        '1.0.0',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-inference-byok-openai",
          "version": "1.0.0",
          "pluginType": "INFERENCE_PROFILE",
          "displayName": "Bring Your Own OpenAI Profile",
          "compatibility": {
            "minPlatformVersion": "0.1.0",
            "requiredCapabilities": ["providers"],
            "supportedDeploymentTargets": [
              "custom-start-from-scratch",
              "dev-openai-lucene",
              "dev-openai-memory",
              "dev-openai-qdrant",
              "dev-openai-pinecone",
              "dev-openai-weaviate",
              "dev-openai-milvus"
            ],
            "supportedProviderModes": ["llm:openai", "embedding:openai"]
          },
          "pricing": {
            "pricingModel": "FREE"
          },
          "installForm": [
            {
              "id": "apiKey",
              "label": "OpenAI-compatible API key secret ref",
              "type": "secretRef",
              "required": true
            },
            {
              "id": "baseUrl",
              "label": "OpenAI-compatible base URL",
              "type": "url",
              "required": false
            },
            {
              "id": "generationModel",
              "label": "Generation model",
              "type": "text",
              "required": false
            },
            {
              "id": "embeddingModel",
              "label": "Embedding model",
              "type": "text",
              "required": false
            }
          ],
          "permissions": {
            "contributesProviders": true,
            "requiresDeploymentSecrets": true
          },
          "contributions": {
            "inferenceProfile": {
              "profileId": "customer-openai",
              "generation": {
                "provider": "openai",
                "baseUrlField": "baseUrl",
                "apiKeySecretRefField": "apiKey",
                "modelField": "generationModel",
                "model": "gpt-4.1-mini",
                "maxTokens": 1800,
                "temperature": 0.3,
                "timeout": 60
              },
              "embedding": {
                "provider": "openai",
                "baseUrlField": "baseUrl",
                "apiKeySecretRefField": "apiKey",
                "modelField": "embeddingModel",
                "model": "text-embedding-3-small",
                "dimensions": 1536
              }
            }
          }
        }',
        current_timestamp,
        current_timestamp
    );
