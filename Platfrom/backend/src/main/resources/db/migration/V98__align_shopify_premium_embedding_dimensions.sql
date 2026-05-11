update platform_marketplace_plugin_versions
set manifest_json = replace(manifest_json, '"dimensions": 3072', '"dimensions": 1536')
where id = 'mkv-inference-premium-hybrid-v1'
  and plugin_id = 'mkp-inference-premium-hybrid'
  and manifest_json like '%"model": "text-embedding-3-large"%'
  and manifest_json like '%"dimensions": 3072%';
