update platform_marketplace_plugin_versions
set version = '1.0.1',
    manifest_json = replace(
        replace(
            manifest_json,
            '"version": "1.0.0"',
            '"version": "1.0.1"'
        ),
        '"readActionResolutionEligible": true,
        "adapterType": "mcp-tool",
        "capabilityRef": "shopify.storefront.catalog.search"',
        '"readActionResolutionEligible": true,
        "llmFacts": {
          "rootPath": "toolResult.content.0.text",
          "lists": [
            {
              "sourcePath": "products.0.variants",
              "target": "documents",
              "maxItems": 5,
              "includeFields": ["id", "title", "availability.available"]
            }
          ]
        },
        "adapterType": "mcp-tool",
        "capabilityRef": "shopify.storefront.catalog.search"'
    )
where id = 'mkv-action-shopify-storefront-read-mcp-v1'
  and manifest_json like '%"capabilityRef": "shopify.storefront.catalog.search"%'
  and manifest_json not like '%"sourcePath": "products.0.variants"%';

update platform_marketplace_plugin_versions
set version = '1.0.5',
    manifest_json = replace(
        replace(
            manifest_json,
            '"version": "1.0.4"',
            '"version": "1.0.5"'
        ),
        '"evidenceKeys": ["product_variant_id", "firstAvailableVariantId"]',
        '"evidenceKeys": ["product_variant_id", "firstAvailableVariantId", "id"]'
    )
where id = 'mkv-action-shopify-cart-mcp-v1'
  and manifest_json like '%"name": "product_variant_id"%'
  and manifest_json not like '%"evidenceKeys": ["product_variant_id", "firstAvailableVariantId", "id"]%';
