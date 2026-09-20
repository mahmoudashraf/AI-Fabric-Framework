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
    'mkv-template-agentic-specialist-team-v103',
    'mkp-template-agentic-specialist-team',
    '1.0.3',
    'GA',
    'PUBLISHED',
    '{
      "schemaVersion": 1,
      "pluginId": "mkp-template-agentic-specialist-team",
      "version": "1.0.3",
      "pluginType": "TEMPLATE",
      "displayName": "Agentic Specialist Team",
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
            "type": "AGENTIC_SPECIALIST_TEAM",
            "contractVersion": 1,
            "requiredRuntimeCapabilityIds": ["ai-fabric-execution", "specialist-chains", "jdbc-specialist-chain-state"],
            "allowedExecutionExtensions": ["HUMAN_REVIEW"],
            "allowedChannelBindings": ["BACKEND_API", "MAX_MODE"],
            "verificationPackIds": ["agentic-specialist-team-v1"]
          },
          "security": {"authzMode": "ALLOW_VERIFIED"},
          "entityConfig": {
            "ai-entities": {
              "document": {
                "indexing": {
                  "enabled": true,
                  "max-characters": 8000
                },
                "analysis": {
                  "enabled": false,
                  "after": []
                },
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
                  {
                    "name": "title",
                    "data-type": "STRING",
                    "description": "Human-readable evidence title.",
                    "destinations": ["VECTOR_METADATA", "LLM_CONTEXT", "API_RESPONSE"],
                    "priority": 90,
                    "required": false,
                    "sanitize-pii": false
                  },
                  {
                    "name": "tenantId",
                    "data-type": "ID",
                    "description": "Server-owned tenant isolation key.",
                    "destinations": ["VECTOR_METADATA"],
                    "priority": 100,
                    "required": true,
                    "sanitize-pii": false
                  }
                ]
              }
            }
          },
          "requiredPluginRefs": ["mkp-specialist-deployment-intelligence@1.0.1"],
          "shell": {
            "greeting": {"title": "Specialist Team", "message": "Give the team a bounded deployment intelligence task."},
            "starterPrompts": [
              {"id": "analyze-deployment", "label": "Analyze deployment", "query": "Analyze this deployment and summarize verified knowledge and runtime state."}
            ],
            "defaultConversationMode": "agentic-specialist-team"
          }
        }
      }
    }',
    'mpub-loom',
    'system',
    'system',
    current_timestamp,
    'First-party Agentic template with the document entity contract required by its grounded knowledge specialist.',
    'seeded-template-agentic-specialist-team-v103',
    current_timestamp,
    current_timestamp
);
