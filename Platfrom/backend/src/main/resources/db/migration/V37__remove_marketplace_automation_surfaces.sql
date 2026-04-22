update platform_marketplace_plugin_versions
set manifest_json = replace(
    replace(
        replace(
            replace(
                replace(
                    replace(
                        replace(
                            replace(
                                manifest_json,
                                E'  "capabilityProfiles": ["SURFACE"],\n',
                                ''
                            ),
                            E'  "capabilityProfiles": ["POLICY_LOGIC"],\n',
                            ''
                        ),
                        E'    "contributesSurfaceCapabilities": true,\n',
                        ''
                    ),
                    E'    "contributesSurfaceCapabilities": true\n',
                    ''
                ),
                E'    "contributesPolicyLogicCapabilities": true,\n',
                ''
            ),
            E'    "contributesPolicyLogicCapabilities": true\n',
            ''
        ),
        E'    "contributesAnalyticsEventCapabilities": true,\n',
        ''
    ),
    E'    "contributesAnalyticsEventCapabilities": true\n',
    ''
)
where plugin_id in (
    'mkp-template-commerce-shell',
    'mkp-template-support-desk-shell',
    'mkp-action-shopify-admin',
    'mkp-action-notifications',
    'mkp-data-commerce-catalog',
    'mkp-data-help-center',
    'mkp-data-policy-folder'
);

update platform_marketplace_plugin_versions
set manifest_json = replace(
    manifest_json,
    E'    "contributesAutomation": true,\n',
    ''
)
where plugin_id = 'mkp-automation-order-retention';

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

alter table platform_deployment_drafts
    drop column if exists automation_config_json;

alter table platform_deployment_versions
    drop column if exists automation_config_json;
