CREATE TABLE platform_experience_kit_definitions (
    code varchar(64) PRIMARY KEY,
    version varchar(32) NOT NULL,
    definition_json text NOT NULL,
    template_plugin_ids_json text NOT NULL,
    sort_order integer NOT NULL
);

INSERT INTO platform_experience_kit_definitions VALUES
('ai-customer-experience', '1.0.0',
'{"code":"ai-customer-experience","version":"1.0.0","name":"AI Customer Experience","summary":"Turn questions into answers, discovery and useful actions.","description":"Your knowledge, live integrations and useful actions in one branded workspace.","experiencePackCode":"generic-workspace","defaultWorkspaceName":"Your company AI","features":["Your knowledge","Live integrations","Branded AI workspace"],"demoUrl":null}',
'["mkp-template-document-knowledge-assistant"]', 10),
('dealership-ai-experience', '1.0.0',
'{"code":"dealership-ai-experience","version":"1.0.0","name":"Dealership AI Experience","summary":"Help buyers find the right car and take the next step.","description":"A configured workspace for stock discovery, comparisons and dealership knowledge. Connect your authorized stock source or start with the simulator.","experiencePackCode":"dealership","defaultWorkspaceName":"Your dealership AI","features":["Live stock","Vehicle comparisons","Dealership policies"],"demoUrl":"https://loomai.pro/demos/dealership-ai"}',
'["mkp-template-simulated-dealership-experience-v1","mkp-template-dealership-external-systems-v1","mkp-template-dealership-external-systems-managed-inbox-v1"]', 20);

CREATE TABLE platform_customer_experience_kits (
    id varchar(64) PRIMARY KEY,
    customer_id varchar(64) NOT NULL REFERENCES platform_customers(id),
    kit_code varchar(64) NOT NULL REFERENCES platform_experience_kit_definitions(code),
    kit_version varchar(32) NOT NULL,
    name varchar(120) NOT NULL,
    deployment_id varchar(64) NOT NULL REFERENCES platform_deployments(id) ON DELETE CASCADE,
    consumer_id varchar(128) NOT NULL,
    installation_entity_id varchar(64) REFERENCES ai_workspace_installations(id) ON DELETE SET NULL,
    embedded_installation_entity_id varchar(64) REFERENCES ai_workspace_installations(id) ON DELETE SET NULL,
    delivery_mode varchar(16) NOT NULL CHECK (delivery_mode IN ('standalone','embedded','both')),
    offer_id varchar(64) NOT NULL REFERENCES platform_customer_offers(id),
    target_profile_id varchar(64) NOT NULL,
    human_support_enabled boolean NOT NULL DEFAULT false,
    operation_key varchar(128) NOT NULL,
    request_fingerprint varchar(64) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    revision bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_customer_experience_kit_operation UNIQUE (customer_id, operation_key),
    CONSTRAINT uq_customer_experience_kit_deployment UNIQUE (deployment_id),
    CONSTRAINT uq_customer_experience_kit_installation UNIQUE (installation_entity_id)
);
CREATE INDEX ix_customer_experience_kits ON platform_customer_experience_kits(customer_id, created_at);
