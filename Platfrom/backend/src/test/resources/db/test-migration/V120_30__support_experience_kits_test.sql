-- H2 schema mirror; production catalogue data is asserted by the PostgreSQL migration tests.
CREATE TABLE platform_experience_kit_definitions (
    code VARCHAR(64) PRIMARY KEY,
    version VARCHAR(32) NOT NULL,
    definition_json TEXT NOT NULL,
    template_plugin_ids_json TEXT NOT NULL,
    sort_order INTEGER NOT NULL
);

CREATE TABLE platform_customer_experience_kits (
    id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL REFERENCES platform_customers(id),
    kit_code VARCHAR(64) NOT NULL REFERENCES platform_experience_kit_definitions(code),
    kit_version VARCHAR(32) NOT NULL,
    name VARCHAR(120) NOT NULL,
    deployment_id VARCHAR(64) NOT NULL REFERENCES platform_deployments(id) ON DELETE CASCADE,
    consumer_id VARCHAR(128) NOT NULL,
    installation_entity_id VARCHAR(64) REFERENCES ai_workspace_installations(id) ON DELETE SET NULL,
    embedded_installation_entity_id VARCHAR(64) REFERENCES ai_workspace_installations(id) ON DELETE SET NULL,
    delivery_mode VARCHAR(16) NOT NULL CHECK (delivery_mode IN ('standalone','embedded','both')),
    offer_id VARCHAR(64) NOT NULL REFERENCES platform_customer_offers(id),
    target_profile_id VARCHAR(64) NOT NULL,
    human_support_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ticketing_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ticket_provider VARCHAR(64),
    operation_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_customer_experience_kit_operation UNIQUE (customer_id, operation_key),
    CONSTRAINT uq_customer_experience_kit_deployment UNIQUE (deployment_id),
    CONSTRAINT uq_customer_experience_kit_installation UNIQUE (installation_entity_id),
    CONSTRAINT ck_experience_kit_ticket_provider CHECK (
        (NOT ticketing_enabled OR ticket_provider IS NOT NULL)
        AND (kit_code = 'ai-support-experience' OR (NOT ticketing_enabled AND ticket_provider IS NULL)))
);
CREATE INDEX ix_customer_experience_kits ON platform_customer_experience_kits(customer_id, created_at);

ALTER TABLE platform_desk_bindings ADD COLUMN support_registration_json TEXT;
