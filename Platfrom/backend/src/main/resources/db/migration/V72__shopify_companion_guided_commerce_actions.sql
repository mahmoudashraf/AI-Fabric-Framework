update platform_marketplace_plugins
set display_name = 'Shopify Companion Storefront Actions',
    short_description = 'Storefront action bundle for grounded product discovery, policy guidance, and Elite governed commerce handoffs.',
    updated_at = current_timestamp
where id = 'mkp-action-shopify-companion-read';

update platform_marketplace_plugin_versions
set manifest_json = '{
  "schemaVersion": 1,
  "pluginId": "mkp-action-shopify-companion-read",
  "version": "1.0.0",
  "pluginType": "ACTION",
  "displayName": "Shopify Companion Storefront Actions",
  "compatibility": {
    "minPlatformVersion": "0.1.0",
    "requiredCapabilities": ["actions"],
    "supportedDeploymentTargets": [
      "custom-start-from-scratch",
      "dev-openai-lucene",
      "dev-openai-memory",
      "dev-openai-qdrant",
      "dev-openai-pinecone",
      "dev-openai-weaviate",
      "dev-openai-milvus"
    ],
    "supportedAuthModes": [
      "PLATFORM_PROXY_SESSION",
      "PRIVATE_RUNTIME_BACKEND_MEDIATED",
      "PUBLIC_RUNTIME_AUTHENTICATED",
      "PUBLIC_RUNTIME_ANONYMOUS"
    ],
    "supportedProviderModes": ["llm:openai"]
  },
  "pricing": {
    "pricingModel": "FREE"
  },
  "permissions": {
    "contributesActions": true,
    "contributesShellPresentation": true,
    "requiresExternalHttpExecution": true
  },
  "contributions": {
    "actions": [
      {
        "actionId": "list_products",
        "displayName": "List products",
        "readOnly": true,
        "anonymousAllowed": true,
        "requiresConfirmation": false,
        "groundingEligible": true,
        "readActionResolutionEligible": true,
        "adapterType": "connector-http",
        "description": "List products by shopper query, with grounded catalog results for storefront discovery.",
        "category": "shopify-companion",
        "params": [
          {
            "name": "query",
            "description": "Shopper query or category hint",
            "type": "STRING",
            "required": true
          }
        ],
        "route": {
          "method": "POST",
          "path": "/actions/execute"
        }
      },
      {
        "actionId": "search_products",
        "displayName": "Search products",
        "readOnly": true,
        "anonymousAllowed": true,
        "requiresConfirmation": false,
        "groundingEligible": true,
        "readActionResolutionEligible": true,
        "adapterType": "connector-http",
        "description": "Search products by shopper query for grounded storefront retrieval.",
        "category": "shopify-companion",
        "params": [
          {
            "name": "query",
            "description": "Shopper search query",
            "type": "STRING",
            "required": true
          }
        ],
        "route": {
          "method": "POST",
          "path": "/actions/execute"
        }
      },
      {
        "actionId": "get_product_details",
        "displayName": "Get product details",
        "readOnly": true,
        "anonymousAllowed": true,
        "requiresConfirmation": false,
        "groundingEligible": true,
        "readActionResolutionEligible": true,
        "adapterType": "connector-http",
        "description": "Fetch detailed product data by SKU, handle, title, or shopper query, including stock availability and pricing.",
        "category": "shopify-companion",
        "params": [
          {
            "name": "query",
            "description": "Product SKU, handle, title, or shopper query",
            "type": "STRING",
            "required": true
          }
        ],
        "route": {
          "method": "POST",
          "path": "/actions/execute"
        }
      },
      {
        "actionId": "check_availability",
        "displayName": "Check product availability",
        "readOnly": true,
        "anonymousAllowed": true,
        "requiresConfirmation": false,
        "groundingEligible": true,
        "readActionResolutionEligible": true,
        "adapterType": "connector-http",
        "description": "Look up live stock status for a product by SKU, handle, title, or shopper query.",
        "category": "shopify-companion",
        "params": [
          {
            "name": "query",
            "description": "Product SKU, handle, title, or shopper query",
            "type": "STRING",
            "required": true
          }
        ],
        "route": {
          "method": "POST",
          "path": "/actions/execute"
        }
      },
      {
        "actionId": "get_policy",
        "displayName": "Get policy details",
        "readOnly": true,
        "anonymousAllowed": true,
        "requiresConfirmation": false,
        "groundingEligible": true,
        "readActionResolutionEligible": true,
        "adapterType": "connector-http",
        "description": "Fetch store policy documents by topic or shopper question.",
        "category": "shopify-companion",
        "params": [
          {
            "name": "query",
            "description": "Policy topic or question",
            "type": "STRING",
            "required": true
          }
        ],
        "route": {
          "method": "POST",
          "path": "/actions/execute"
        }
      },
      {
        "actionId": "add_product_to_cart",
        "displayName": "Request add to cart",
        "readOnly": false,
        "anonymousAllowed": true,
        "requiresConfirmation": true,
        "confirmationMessage": "Add {{query}} to your cart?",
        "groundingEligible": false,
        "readActionResolutionEligible": false,
        "adapterType": "connector-http",
        "description": "Create a governed Elite add-to-cart grant for a selected Shopify product after explicit shopper confirmation.",
        "category": "shopify-companion",
        "params": [
          {
            "name": "query",
            "description": "Product title, handle, SKU, or shopper product query",
            "type": "STRING",
            "required": true
          },
          {
            "name": "quantity",
            "description": "Quantity to add",
            "type": "INTEGER",
            "required": false,
            "min": 1,
            "max": 10
          },
          {
            "name": "variantId",
            "description": "Shopify variant identifier when already selected",
            "type": "STRING",
            "required": false
          },
          {
            "name": "productHandle",
            "description": "Shopify product handle when already selected",
            "type": "STRING",
            "required": false
          }
        ],
        "route": {
          "method": "POST",
          "path": "/actions/execute"
        }
      },
      {
        "actionId": "add_to_cart",
        "displayName": "Request add to cart by SKU",
        "readOnly": false,
        "anonymousAllowed": true,
        "requiresConfirmation": true,
        "confirmationMessage": "Add {{sku}} to your cart?",
        "groundingEligible": false,
        "readActionResolutionEligible": false,
        "adapterType": "connector-http",
        "description": "Compatibility alias for governed Elite add-to-cart grants when the planner supplies SKU-style parameters.",
        "category": "shopify-companion",
        "params": [
          {
            "name": "sku",
            "description": "Product SKU, title, handle, or shopper product query",
            "type": "STRING",
            "required": true
          },
          {
            "name": "quantity",
            "description": "Quantity to add",
            "type": "INTEGER",
            "required": false,
            "min": 1,
            "max": 10
          },
          {
            "name": "variantId",
            "description": "Shopify variant identifier when already selected",
            "type": "STRING",
            "required": false
          }
        ],
        "route": {
          "method": "POST",
          "path": "/actions/execute"
        }
      },
      {
        "actionId": "update_cart_quantity",
        "displayName": "Request cart quantity update",
        "readOnly": false,
        "anonymousAllowed": true,
        "requiresConfirmation": true,
        "confirmationMessage": "Update the cart quantity to {{targetQuantity}}?",
        "groundingEligible": false,
        "readActionResolutionEligible": false,
        "adapterType": "connector-http",
        "description": "Create a governed Elite cart quantity update grant after explicit shopper confirmation.",
        "category": "shopify-companion",
        "params": [
          {
            "name": "cartLineKey",
            "description": "Shopify cart line key to update",
            "type": "STRING",
            "required": true
          },
          {
            "name": "variantId",
            "description": "Shopify variant identifier for the line",
            "type": "STRING",
            "required": true
          },
          {
            "name": "targetQuantity",
            "description": "Target quantity",
            "type": "INTEGER",
            "required": true,
            "min": 0,
            "max": 25
          }
        ],
        "route": {
          "method": "POST",
          "path": "/actions/execute"
        }
      }
    ],
    "shell": {
      "moduleRefs": ["actions", "products"]
    }
  }
}',
    published_at = coalesce(published_at, current_timestamp)
where id = 'mkv-action-shopify-companion-read-v1';
