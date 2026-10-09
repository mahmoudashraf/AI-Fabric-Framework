update ai_workspace_installations
set configuration_json = ((configuration_json::jsonb #- '{knowledge,retrievalVectorSpaces}')::text),
    experience_pack_version = '1.1.0',
    updated_at = current_timestamp,
    row_version = row_version + 1
where experience_pack_code = 'dealership';
