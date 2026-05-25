-- Customer context summary is a bounded read action. It may consume trusted owned-resource handles
-- from Bridge/session context, but it must not persist shopper-owned resources or perform mutations.
update platform_marketplace_plugin_versions
set
    version = '1.0.6',
    manifest_json = replace(
        replace(
            manifest_json,
            '"version": "1.0.5"',
            '"version": "1.0.6"'
        ),
        '"actions": [
          {',
        '"actions": [
          {
            "actionId": "shopify_get_customer_context_summary",
            "displayName": "Get customer context summary",
            "readOnly": true,
            "anonymousAllowed": true,
            "requiresConfirmation": false,
            "groundingEligible": true,
            "readActionResolutionEligible": true,
            "adapterType": "connector-http",
            "capabilityRef": "shopify.customer_context.summary",
            "description": "Return a bounded, non-persistent summary of the current shopper-owned Shopify context available to the assistant: current cart when a trusted storefront cart handle exists, latest order status when Customer Account auth is connected, specific order status when an order number is supplied, store credit balance when Customer Account auth is connected, and return-request readiness metadata. Use this as a context-discovery read action for questions like what is in my cart, show my latest order, do I have store credit, or I want to return my last order. It must not submit returns or perform mutations.",
            "category": "shopify-companion",
            "params": [
              {
                "name": "cart_id",
                "description": "Shopify cart identifier resolved from trusted storefront context. Never ask the shopper to provide this value.",
                "type": "STRING",
                "required": false,
                "visibility": "INTERNAL",
                "askUser": false,
                "resolveFrom": {
                  "source": "OWNED_RESOURCE",
                  "resourceType": "shopify.cart",
                  "scope": "current_session",
                  "handleField": "cart_id",
                  "metadataKeys": ["cart_id", "cartId"]
                }
              },
              {
                "name": "order_number",
                "description": "Optional customer-visible order number when the shopper names a specific order. Leave empty for latest-order questions.",
                "type": "STRING",
                "required": false,
                "pattern": "^(?=.*[0-9])[#A-Za-z0-9_-]+$"
              }
            ],
            "llmFacts": {
              "copyFields": ["type", "nonPersistent", "includedReads", "excludedMutations"],
              "objects": [
                {
                  "sourcePath": "cart",
                  "target": "cart",
                  "includeFields": ["available", "reason", "summary", "cart_id"]
                },
                {
                  "sourcePath": "latestOrder",
                  "target": "latestOrder",
                  "includeFields": ["available", "authRequired", "errorCode", "summary", "order_number"]
                },
                {
                  "sourcePath": "specificOrder",
                  "target": "specificOrder",
                  "includeFields": ["available", "authRequired", "errorCode", "summary", "order_number"]
                },
                {
                  "sourcePath": "storeCredit",
                  "target": "storeCredit",
                  "includeFields": ["available", "authRequired", "errorCode", "summary"]
                },
                {
                  "sourcePath": "returns",
                  "target": "returns",
                  "includeFields": ["requestReturnSupported", "requestReturnActionId", "confirmationRequired", "requiresOrderNumber", "returnableOrdersReadSupported", "summary"]
                }
              ]
            },
            "route": {"method": "POST", "path": "/actions/execute"}
          },
          {'
    ),
    published_at = current_timestamp
where id = 'mkv-action-shopify-customer-account-mcp-v1'
  and manifest_json not like '%"actionId": "shopify_get_customer_context_summary"%';
