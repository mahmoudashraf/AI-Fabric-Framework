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
    'mkv-template-conversation-handover-v1-0-1',
    'mkp-template-conversation-handover',
    '1.0.1',
    'GA',
    'PUBLISHED',
    '{
      "schemaVersion": 1,
      "pluginId": "mkp-template-conversation-handover",
      "version": "1.0.1",
      "pluginType": "TEMPLATE",
      "displayName": "Conversation Handover Workspace",
      "compatibility": {
        "requiredCapabilities": ["templates", "shellConfig"],
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
            "allowedExecutionExtensions": ["CONVERSATION_HANDOVER"],
            "defaultExecutionExtensions": ["CONVERSATION_HANDOVER"],
            "allowedChannelBindings": ["BACKEND_API", "DOCKED_COMPOSER", "MAX_MODE", "INLINE_ASSISTANT"]
          },
          "security": {
            "authzMode": "ALLOW_VERIFIED",
            "publicRuntimeBootstrapEnabled": true,
            "publicRuntimeTokenIssuer": "runtime-public-bootstrap",
            "publicRuntimeAcceptedIssuers": "runtime-public-bootstrap",
            "publicRuntimeAcceptedAudiences": "loomai-ai-workspace",
            "publicRuntimeDefaultAudience": "loomai-ai-workspace"
          },
          "requiredPluginRefs": [
            "mkp-action-conversation-handover@1.0.0",
            "mkp-integration-basic-loomai-inbox@1.0.0"
          ],
          "shell": {
            "greeting": {
              "title": "AI Workspace",
              "message": "Ask the AI or transfer this conversation to a person when you need human help."
            },
            "starterPrompts": [],
            "defaultConversationMode": "conversational"
          }
        }
      }
    }',
    'mpub-loom',
    'system',
    'system',
    current_timestamp,
    'Reviewed public anonymous session bootstrap for the Conversation Handover workspace. Token signing material remains deployment-managed.',
    'seeded-conversation-handover-template-v1-0-1',
    current_timestamp,
    current_timestamp
);
