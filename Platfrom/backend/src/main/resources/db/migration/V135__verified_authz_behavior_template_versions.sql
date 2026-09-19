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
        'mkv-template-conversational-assistant-v101',
        'mkp-template-conversational-assistant',
        '1.0.1',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-template-conversational-assistant",
          "version": "1.0.1",
          "pluginType": "TEMPLATE",
          "displayName": "Conversational Assistant",
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
              "requiredPluginRefs": [],
              "shell": {
                "greeting": {"title": "Assistant", "message": "How can I help?"},
                "starterPrompts": [
                  {"id": "ask-question", "label": "Ask a question", "query": "Help me understand the available information."}
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
        'First-party behavior template reviewed for verified deployment-bound runtime access.',
        'seeded-template-conversational-assistant-v101',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkv-template-agentic-specialist-team-v101',
        'mkp-template-agentic-specialist-team',
        '1.0.1',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-template-agentic-specialist-team",
          "version": "1.0.1",
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
              "requiredPluginRefs": ["mkp-specialist-deployment-intelligence@1.0.0"],
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
        'First-party behavior template reviewed for verified deployment-bound runtime access and an exact specialist dependency.',
        'seeded-template-agentic-specialist-team-v101',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkv-template-smart-brain-v101',
        'mkp-template-smart-brain',
        '1.0.1',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-template-smart-brain",
          "version": "1.0.1",
          "pluginType": "TEMPLATE",
          "displayName": "Smart Brain",
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
                "type": "SMART_BRAIN",
                "contractVersion": 1,
                "requiredRuntimeCapabilityIds": ["ai-fabric-execution", "smart-brain-durable-operations", "cloudevents-ingress", "deployment-result-store"],
                "allowedExecutionExtensions": [],
                "allowedChannelBindings": ["BACKEND_API", "SIGNED_WEBHOOK"],
                "verificationPackIds": ["smart-brain-behavior-v1"]
              },
              "security": {"authzMode": "ALLOW_VERIFIED"},
              "requiredPluginRefs": ["mkp-specialist-smart-brain-analysis@1.0.0"],
              "shell": {
                "greeting": {"title": "Smart Brain", "message": "Configure trusted events, schedules, and durable result delivery."},
                "defaultConversationMode": "smart-brain"
              }
            }
          }
        }',
        'mpub-loom',
        'system',
        'system',
        current_timestamp,
        'First-party behavior template reviewed for verified deployment-bound runtime access and an exact Smart Brain specialist dependency.',
        'seeded-template-smart-brain-v101',
        current_timestamp,
        current_timestamp
    );
