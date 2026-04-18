update platform_marketplace_plugins
set short_description = 'Read-first storefront action bundle for product discovery, availability lookup, and policy guidance.',
    updated_at = current_timestamp
where id = 'mkp-action-shopify-companion-read';

update platform_marketplace_plugin_versions
set manifest_json = '{
  "schemaVersion": 1,
  "pluginId": "mkp-action-shopify-companion-read",
  "version": "1.0.0",
  "pluginType": "ACTION",
  "displayName": "Shopify Companion Read Actions",
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
        "adapterType": "connector-http",
        "route": {
          "method": "POST",
          "path": "/actions/execute"
        }
      },
      {
        "actionId": "search_products",
        "displayName": "Search products",
        "readOnly": true,
        "adapterType": "connector-http",
        "route": {
          "method": "POST",
          "path": "/actions/execute"
        }
      },
      {
        "actionId": "get_product_details",
        "displayName": "Get product details",
        "readOnly": true,
        "adapterType": "connector-http",
        "description": "Fetch detailed product data by SKU, including stock availability and pricing.",
        "category": "shopify-companion",
        "params": [
          {
            "name": "sku",
            "description": "Product SKU",
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
        "adapterType": "connector-http",
        "description": "Look up live stock status for a product by SKU.",
        "category": "shopify-companion",
        "params": [
          {
            "name": "sku",
            "description": "Product SKU",
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
        "adapterType": "connector-http",
        "description": "Search store policies by topic or shopper question.",
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
      }
    ],
    "shell": {
      "moduleRefs": ["actions", "products"]
    }
  }
}',
    published_at = coalesce(published_at, current_timestamp)
where id = 'mkv-action-shopify-companion-read-v1';
