update platform_marketplace_plugin_versions
set manifest_json = replace(
    manifest_json,
    '"mkp-inference-shopify-companion-default"',
    '"mkp-inference-shared-embeddings"'
)
where id = 'mkv-template-shopify-companion-v1'
  and manifest_json like '%"mkp-inference-shopify-companion-default"%';
