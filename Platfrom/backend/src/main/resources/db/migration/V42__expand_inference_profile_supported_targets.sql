update platform_marketplace_plugin_versions
set manifest_json = replace(
    manifest_json,
    '              "dev-openai-milvus"' || chr(10) || '            ]',
    '              "dev-openai-milvus",' || chr(10) ||
    '              "dev-anthropic-lucene",' || chr(10) ||
    '              "dev-azure-pinecone",' || chr(10) ||
    '              "dev-openai-rest-pinecone",' || chr(10) ||
    '              "dev-gemini-milvus"' || chr(10) ||
    '            ]'
)
where plugin_id in (
    'mkp-inference-local-embeddings',
    'mkp-inference-optimized-orchestration',
    'mkp-inference-premium-hybrid',
    'mkp-inference-byok-openai'
)
  and manifest_json like '%"dev-openai-milvus"%'
  and manifest_json not like '%"dev-anthropic-lucene"%';
