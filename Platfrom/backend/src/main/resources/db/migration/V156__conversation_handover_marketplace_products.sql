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
        'mkp-action-conversation-handover',
        'conversation-handover-action',
        'Conversation Handover Action',
        'ACTION',
        'loom',
        'Loom AI',
        'Canonical confirmed request that transfers a live conversation to an installed human channel.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkp-integration-basic-loomai-inbox',
        'basic-loomai-inbox-channel',
        'Basic LoomAI Inbox Channel',
        'INTEGRATION',
        'loom',
        'Loom AI',
        'Reviewed conversation-channel binding for the Basic LoomAI Inbox reference provider.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkp-template-conversation-handover',
        'conversation-handover-workspace',
        'Conversation Handover Workspace',
        'TEMPLATE',
        'loom',
        'Loom AI',
        'Conversational AI Workspace with durable, explicit transfer to a human and return to AI.',
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
        'mkv-action-conversation-handover-v1',
        'mkp-action-conversation-handover',
        '1.0.0',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-action-conversation-handover",
          "version": "1.0.0",
          "pluginType": "ACTION",
          "displayName": "Conversation Handover Action",
          "compatibility": {
            "requiredCapabilities": ["actions"],
            "supportedDeploymentTargets": ["custom-start-from-scratch"],
            "supportedAuthModes": ["PRIVATE_RUNTIME_BACKEND_MEDIATED", "PUBLIC_RUNTIME_AUTHENTICATED", "PUBLIC_RUNTIME_ANONYMOUS"]
          },
          "pricing": {"pricingModel": "FREE"},
          "permissions": {
            "contributesActions": true,
            "requiresExternalHttpExecution": false,
            "requiresDeploymentSecrets": false
          },
          "contributions": {
            "actions": [
              {
                "actionId": "request_conversation_handover",
                "displayName": "Talk to a person",
                "description": "Request temporary human ownership of the current conversation.",
                "readOnly": false,
                "confirmationRequired": true,
                "anonymousAllowed": true,
                "terminal": true,
                "adapterType": "conversation-control",
                "operation": "REQUEST_HANDOVER"
              }
            ]
          }
        }',
        'mpub-loom',
        'system',
        'system',
        current_timestamp,
        'Reviewed canonical terminal conversation-control action. It selects no remote route and cannot grant operator or domain authority.',
        'seeded-conversation-handover-action-v1',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkv-integration-basic-loomai-inbox-v1',
        'mkp-integration-basic-loomai-inbox',
        '1.0.0',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-integration-basic-loomai-inbox",
          "version": "1.0.0",
          "pluginType": "INTEGRATION",
          "displayName": "Basic LoomAI Inbox Channel",
          "compatibility": {
            "requiredCapabilities": ["integrations"],
            "supportedDeploymentTargets": ["custom-start-from-scratch"],
            "supportedAuthModes": ["PRIVATE_RUNTIME_BACKEND_MEDIATED", "PUBLIC_RUNTIME_AUTHENTICATED", "PUBLIC_RUNTIME_ANONYMOUS"]
          },
          "pricing": {"pricingModel": "FREE"},
          "installForm": [
            {"id": "providerBaseUrl", "label": "Inbox provider URL", "type": "url", "required": true, "description": "HTTPS base URL of the Basic LoomAI Inbox provider."},
            {"id": "operatorConsoleUrl", "label": "Operator console URL", "type": "url", "required": false, "description": "Optional HTTPS URL shown to authorized operators."},
            {"id": "providerApiKeySecretRef", "label": "Provider API key secret", "type": "secretRef", "required": true, "description": "Deployment secret containing the provider dispatch API key."},
            {"id": "runtimeCallbackSigningSecretRef", "label": "Runtime callback signing secret", "type": "secretRef", "required": true, "description": "Deployment secret used to verify signed provider callbacks at the runtime."},
            {"id": "providerWebhookSigningSecretRef", "label": "Provider webhook signing secret", "type": "secretRef", "required": true, "description": "Deployment secret used by the provider to sign callbacks that the connector verifies."}
          ],
          "permissions": {
            "contributesIntegrations": true,
            "requiresExternalHttpExecution": true,
            "requiresDeploymentSecrets": true
          },
          "contributions": {
            "conversationChannel": {
              "contractVersion": "LOOMAI_CONVERSATION_CHANNEL_V1",
              "bindingId": "basic-loomai-inbox",
              "adapterImplementationId": "loomai-basic-inbox",
              "adapterVersion": "1",
              "compatibleBehaviorTypes": ["CONVERSATIONAL"],
              "capabilities": ["DISPATCH", "CLAIM", "INBOUND_MESSAGE", "OUTBOUND_MESSAGE", "RETURN_TO_AI", "CLOSE", "AVAILABILITY"],
              "providerBaseUrlField": "providerBaseUrl",
              "providerApiKeySecretRefField": "providerApiKeySecretRef",
              "runtimeCallbackSigningSecretRefField": "runtimeCallbackSigningSecretRef",
              "providerWebhookSigningSecretRefField": "providerWebhookSigningSecretRef",
              "operatorConsoleUrlField": "operatorConsoleUrl",
              "requiredRuntimeCapabilityIds": ["conversation-control", "conversation-channel-outbox", "conversation-event-stream"],
              "requiredMigrationIds": ["loomai-conversation-handover-v1"],
              "verificationPackIds": ["conversation-handover-v1"],
              "configuration": {
                "modelInitiatedPolicy": "REQUIRE_USER_CONFIRMATION",
                "userCanCancelPending": true,
                "userCanResumeAi": true,
                "activeIdleAutoResume": false,
                "queueTimeoutSeconds": 900,
                "queueTimeoutOutcome": "EXPIRED_REQUIRES_EXPLICIT_RECOVERY",
                "transcriptProjection": "SAFE_TEXT_AND_ATTACHMENT_REFS",
                "intake": {
                  "anonymousAllowed": true,
                  "fields": [
                    {"id": "name", "label": "Name", "type": "text", "required": false, "maxLength": 120, "purpose": "Human conversation continuity", "consentRequired": false},
                    {"id": "email", "label": "Email", "type": "email", "required": false, "maxLength": 254, "purpose": "Optional follow-up after the live conversation", "consentRequired": true},
                    {"id": "phone", "label": "Phone", "type": "tel", "required": false, "maxLength": 40, "purpose": "Optional phone follow-up after the live conversation", "consentRequired": true}
                  ]
                }
              }
            }
          }
        }',
        'mpub-loom',
        'system',
        'system',
        current_timestamp,
        'First reviewed V1 conversation-channel adapter. All endpoint and credential inputs remain deployment-owned configuration.',
        'seeded-basic-loomai-inbox-channel-v1',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkv-template-conversation-handover-v1',
        'mkp-template-conversation-handover',
        '1.0.0',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-template-conversation-handover",
          "version": "1.0.0",
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
              "security": {"authzMode": "ALLOW_VERIFIED"},
              "requiredPluginRefs": [
                "mkp-action-conversation-handover@1.0.0",
                "mkp-integration-basic-loomai-inbox@1.0.0"
              ],
              "shell": {
                "greeting": {"title": "AI Workspace", "message": "Ask the AI or transfer this conversation to a person when you need human help."},
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
        'Conversation Handover activates only after the exact action and configured reviewed channel dependencies are enabled.',
        'seeded-conversation-handover-template-v1',
        current_timestamp,
        current_timestamp
    );
