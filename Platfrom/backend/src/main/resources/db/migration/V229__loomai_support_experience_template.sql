-- Catalogue mapping only; no installation, target, document binding or release is changed.
UPDATE platform_experience_kit_definitions
SET template_plugin_ids_json = '["mkp-template-ai-support-workspace","mkp-template-loomai-support-workspace"]'
WHERE code = 'ai-support-experience';
