update shopify_companion_package_profiles
set details_json = '{"merchantVisible":true,"defaultPackage":true,"requiresBilling":false,"requiresReindexOnEmbeddingChange":true,"enableCustomerAccountMcp":true,"enableCheckoutMcp":true,"requiredPluginIds":["mkp-action-shopify-storefront-read-mcp","mkp-action-shopify-cart-mcp","mkp-action-shopify-customer-account-mcp","mkp-action-shopify-checkout-mcp","mkp-data-shopify-catalog","mkp-data-shopify-policies","mkp-inference-premium-hybrid"],"disabledPluginIds":["mkp-action-shopify-companion-read"],"stagingTemplatePluginId":"mkp-template-shopify-companion-staging","productionTemplatePluginId":"mkp-template-shopify-companion-production","productionTargetProfileId":"dtp-coolify-production"}',
    updated_at = current_timestamp
where profile_key = 'HIGH_QUALITY';

update shopify_companion_package_profiles
set details_json = '{"merchantVisible":false,"requiresBilling":true,"requiresOperatorApproval":true,"requiresReindexOnEmbeddingChange":true,"enableCustomerAccountMcp":true,"enableCheckoutMcp":true,"requiredPluginIds":["mkp-action-shopify-storefront-read-mcp","mkp-action-shopify-cart-mcp","mkp-action-shopify-customer-account-mcp","mkp-action-shopify-checkout-mcp","mkp-data-shopify-catalog","mkp-data-shopify-policies","mkp-inference-dedicated-embedding-worker"],"disabledPluginIds":["mkp-action-shopify-companion-read"],"stagingTemplatePluginId":"mkp-template-shopify-companion-staging","productionTemplatePluginId":"mkp-template-shopify-companion-production","productionTargetProfileId":"dtp-coolify-production"}',
    updated_at = current_timestamp
where profile_key = 'ENTERPRISE_DEDICATED';
