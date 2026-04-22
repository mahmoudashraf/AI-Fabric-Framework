update platform_marketplace_plugin_versions
set manifest_json = replace(manifest_json, '"capabilityProfiles": ["SURFACE"],', '')
where manifest_json like '%"capabilityProfiles": ["SURFACE"],%';

update platform_marketplace_plugin_versions
set manifest_json = replace(manifest_json, '"capabilityProfiles": ["POLICY_LOGIC"],', '')
where manifest_json like '%"capabilityProfiles": ["POLICY_LOGIC"],%';

update platform_marketplace_plugin_versions
set manifest_json = replace(manifest_json, '"contributesSurfaceCapabilities": true,', '')
where manifest_json like '%"contributesSurfaceCapabilities": true,%';

update platform_marketplace_plugin_versions
set manifest_json = replace(manifest_json, '"contributesSurfaceCapabilities": true', '')
where manifest_json like '%"contributesSurfaceCapabilities": true%';

update platform_marketplace_plugin_versions
set manifest_json = replace(manifest_json, '"contributesPolicyLogicCapabilities": true,', '')
where manifest_json like '%"contributesPolicyLogicCapabilities": true,%';

update platform_marketplace_plugin_versions
set manifest_json = replace(manifest_json, '"contributesPolicyLogicCapabilities": true', '')
where manifest_json like '%"contributesPolicyLogicCapabilities": true%';

update platform_marketplace_plugin_versions
set manifest_json = replace(manifest_json, '"contributesAnalyticsEventCapabilities": true,', '')
where manifest_json like '%"contributesAnalyticsEventCapabilities": true,%';

update platform_marketplace_plugin_versions
set manifest_json = replace(manifest_json, '"contributesAnalyticsEventCapabilities": true', '')
where manifest_json like '%"contributesAnalyticsEventCapabilities": true%';

update platform_marketplace_plugin_versions
set manifest_json = replace(manifest_json, '"contributesAutomation": true,', '')
where manifest_json like '%"contributesAutomation": true,%';

update platform_marketplace_plugin_versions
set manifest_json = replace(
    manifest_json,
    '["mkp-action-shopify-admin", "mkp-data-commerce-catalog", "mkp-automation-order-retention"]',
    '["mkp-action-shopify-admin", "mkp-data-commerce-catalog"]'
)
where id = 'mkv-template-commerce-shell-v1';

update platform_marketplace_plugin_versions
set manifest_json = replace(
    manifest_json,
    '["mkp-data-help-center", "mkp-action-notifications", "mkp-automation-order-retention"]',
    '["mkp-data-help-center", "mkp-action-notifications"]'
)
where id = 'mkv-template-support-desk-shell-v1';

delete from platform_marketplace_plugins
where id = 'mkp-automation-order-retention';
