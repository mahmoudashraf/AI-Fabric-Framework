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
        'mkp-specialist-deployment-intelligence',
        'deployment-intelligence-specialist-team',
        'Deployment Intelligence Specialist Team',
        'SPECIALIST',
        'loom',
        'Loom AI',
        'Reviewed source-attested manager and specialist bundle for durable deployment intelligence work.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkp-specialist-smart-brain-analysis',
        'smart-brain-event-analysis',
        'Smart Brain Event Analysis',
        'SPECIALIST',
        'loom',
        'Loom AI',
        'Reviewed source-attested specialist for deployment-local durable event analysis.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkp-template-conversational-assistant',
        'conversational-assistant-deployment',
        'Conversational Assistant',
        'TEMPLATE',
        'loom',
        'Loom AI',
        'Interactive assistant deployment with authenticated chat, query-once, and embedded UI channels.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkp-template-agentic-specialist-team',
        'agentic-specialist-team-deployment',
        'Agentic Specialist Team',
        'TEMPLATE',
        'loom',
        'Loom AI',
        'Larger-task deployment with a bounded manager, exact specialists, and durable chain state.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkp-template-smart-brain',
        'smart-brain-deployment',
        'Smart Brain',
        'TEMPLATE',
        'loom',
        'Loom AI',
        'Proactive deployment-local analysis triggered by trusted CloudEvents or schedules with durable results.',
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
        'mkv-specialist-deployment-intelligence-v1',
        'mkp-specialist-deployment-intelligence',
        '1.0.0',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-specialist-deployment-intelligence",
          "version": "1.0.0",
          "pluginType": "SPECIALIST",
          "displayName": "Deployment Intelligence Specialist Team",
          "compatibility": {
            "requiredCapabilities": ["specialists"],
            "supportedDeploymentTargets": ["custom-start-from-scratch"]
          },
          "pricing": {"pricingModel": "FREE"},
          "permissions": {
            "contributesSpecialists": true,
            "requiresDeploymentSecrets": false
          },
          "contributions": {
            "specialist": {
              "contractVersion": "LOOMAI_SOURCE_ATTESTED_SPECIALIST_BUNDLE_V1",
              "compatibleBehaviorTypes": ["AGENTIC_SPECIALIST_TEAM"],
              "sourceBundleRefs": [
                {
                  "bundleId": "deployment-intelligence-team@1",
                  "contractVersion": "LOOMAI_SOURCE_ATTESTED_SPECIALIST_BUNDLE_V1",
                  "contentHash": "sha256:00b9f8f582195eb18857361d94c02c48ab703e72a9a5d70d9e4c2cd8ea51a0d8",
                  "specialistRefs": [
                    "deployment-intelligence-manager@1",
                    "deployment-knowledge-specialist@1",
                    "deployment-runtime-state-specialist@1"
                  ],
                  "chainRefs": ["deployment-intelligence-team@1"]
                }
              ],
              "requiredRuntimeCapabilityIds": [
                "ai-fabric-execution",
                "specialist-chains",
                "jdbc-specialist-chain-state"
              ],
              "requiredMigrationIds": ["ai-specialist-chain-execution-v1"],
              "requiredSecretNames": [],
              "verificationPackIds": ["agentic-specialist-team-v1"],
              "unsupportedClaims": [
                "Does not install customer-provided executable specialist definitions.",
                "Does not grant write authority to read-only specialists."
              ]
            }
          }
        }',
        'mpub-loom',
        'system',
        'system',
        current_timestamp,
        'First-party source-attested specialist bundle reviewed for the Agentic Specialist Team behavior.',
        'sha256:00b9f8f582195eb18857361d94c02c48ab703e72a9a5d70d9e4c2cd8ea51a0d8',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkv-specialist-smart-brain-analysis-v1',
        'mkp-specialist-smart-brain-analysis',
        '1.0.0',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-specialist-smart-brain-analysis",
          "version": "1.0.0",
          "pluginType": "SPECIALIST",
          "displayName": "Smart Brain Event Analysis",
          "compatibility": {
            "requiredCapabilities": ["specialists"],
            "supportedDeploymentTargets": ["custom-start-from-scratch"]
          },
          "pricing": {"pricingModel": "FREE"},
          "permissions": {
            "contributesSpecialists": true,
            "requiresDeploymentSecrets": false
          },
          "contributions": {
            "specialist": {
              "contractVersion": "LOOMAI_SOURCE_ATTESTED_SPECIALIST_BUNDLE_V1",
              "compatibleBehaviorTypes": ["SMART_BRAIN"],
              "sourceBundleRefs": [
                {
                  "bundleId": "smart-brain-event-analysis@1",
                  "contractVersion": "LOOMAI_SOURCE_ATTESTED_SPECIALIST_BUNDLE_V1",
                  "contentHash": "sha256:1059173cfb1fe7e0794e971af43d373c4d047a02e9fb50b77c6fe0328094e61d",
                  "specialistRefs": ["smart-brain-event-analyst@1"],
                  "chainRefs": []
                }
              ],
              "requiredRuntimeCapabilityIds": [
                "ai-fabric-execution",
                "smart-brain-durable-operations",
                "cloudevents-ingress",
                "deployment-result-store"
              ],
              "requiredMigrationIds": [
                "ai-specialist-execution-v1",
                "loomai-smart-brain-operation-v1",
                "loomai-smart-brain-delivery-v1",
                "loomai-smart-brain-scheduler-v1"
              ],
              "requiredSecretNames": [],
              "verificationPackIds": ["smart-brain-behavior-v1"],
              "unsupportedClaims": [
                "Does not expose platform-central event ingress.",
                "Does not grant automatic application write authority."
              ]
            }
          }
        }',
        'mpub-loom',
        'system',
        'system',
        current_timestamp,
        'First-party source-attested specialist bundle reviewed for the Smart Brain behavior.',
        'sha256:1059173cfb1fe7e0794e971af43d373c4d047a02e9fb50b77c6fe0328094e61d',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkv-template-conversational-assistant-v1',
        'mkp-template-conversational-assistant',
        '1.0.0',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-template-conversational-assistant",
          "version": "1.0.0",
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
        'First-party behavior template reviewed for conversational deployments.',
        'seeded-template-conversational-assistant-v1',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkv-template-agentic-specialist-team-v1',
        'mkp-template-agentic-specialist-team',
        '1.0.0',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-template-agentic-specialist-team",
          "version": "1.0.0",
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
        'First-party behavior template reviewed with an exact specialist dependency.',
        'seeded-template-agentic-specialist-team-v1',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkv-template-smart-brain-v1',
        'mkp-template-smart-brain',
        '1.0.0',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-template-smart-brain",
          "version": "1.0.0",
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
        'First-party behavior template reviewed with an exact Smart Brain specialist dependency.',
        'seeded-template-smart-brain-v1',
        current_timestamp,
        current_timestamp
    );
