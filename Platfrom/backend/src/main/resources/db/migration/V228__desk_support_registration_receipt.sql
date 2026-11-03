ALTER TABLE platform_desk_bindings
    ADD COLUMN support_registration_json TEXT;

ALTER TABLE platform_desk_bindings
    ADD CONSTRAINT platform_desk_support_registration_object
    CHECK (support_registration_json IS NULL
        OR jsonb_typeof(support_registration_json::jsonb) = 'object');
