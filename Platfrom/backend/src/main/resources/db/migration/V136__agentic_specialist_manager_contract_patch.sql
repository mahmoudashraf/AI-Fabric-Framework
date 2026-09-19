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
        'mkv-specialist-deployment-intelligence-v101',
        'mkp-specialist-deployment-intelligence',
        '1.0.1',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-specialist-deployment-intelligence",
          "version": "1.0.1",
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
                  "contentHash": "sha256:ab1a1185dbe5f8ba5dc6c67c10c196bd9a569f211c537a39efb2d47fef05a025",
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
        'First-party source-attested specialist patch requiring a non-null auditable manager routing rationale.',
        'sha256:ab1a1185dbe5f8ba5dc6c67c10c196bd9a569f211c537a39efb2d47fef05a025',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkv-template-agentic-specialist-team-v102',
        'mkp-template-agentic-specialist-team',
        '1.0.2',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-template-agentic-specialist-team",
          "version": "1.0.2",
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
        'First-party verified-auth Agentic template using the patched source-attested specialist manager contract.',
        'seeded-template-agentic-specialist-team-v102',
        current_timestamp,
        current_timestamp
    );
