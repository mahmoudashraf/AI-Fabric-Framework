update platform_marketplace_plugin_versions
set manifest_json = replace(
    manifest_json,
    ',
        "supportedProviderModes": ["llm:openai", "embedding:onnx"]',
    ''
)
where id = 'mkv-template-shopify-companion-v1'
  and manifest_json like '%"supportedProviderModes": ["llm:openai", "embedding:onnx"]%';
