ALTER TABLE platform_deployment_releases
    ADD COLUMN runtime_generation_transition_state VARCHAR(32);

ALTER TABLE platform_deployment_releases
    ADD COLUMN runtime_generation_transition_fingerprint VARCHAR(64);

ALTER TABLE platform_deployment_releases
    ADD COLUMN runtime_generation_transition_json TEXT;
