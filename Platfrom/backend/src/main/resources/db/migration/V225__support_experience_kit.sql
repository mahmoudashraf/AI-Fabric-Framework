-- Additive configuration only. No ticket history, document bindings or live releases are changed.
ALTER TABLE platform_customer_experience_kits
    ADD COLUMN ticketing_enabled boolean NOT NULL DEFAULT false,
    ADD COLUMN ticket_provider varchar(64),
    ADD CONSTRAINT ck_experience_kit_ticket_provider CHECK (
        (NOT ticketing_enabled OR ticket_provider IS NOT NULL)
        AND (kit_code = 'ai-support-experience' OR (NOT ticketing_enabled AND ticket_provider IS NULL))
    );

INSERT INTO platform_experience_kit_definitions
    (code, version, definition_json, template_plugin_ids_json, sort_order)
VALUES ('ai-support-experience', '1.0.0',
'{"code":"ai-support-experience","version":"1.0.0","name":"AI Support Experience","summary":"Help customers resolve problems and keep every request connected to the conversation.","description":"Technical Support preset with your approved knowledge, tracked requests and optional human support. Services and supported identity are configured separately before activation.","experiencePackCode":"support","defaultWorkspaceName":"Your support AI","features":["Approved support knowledge","Tracked requests","Optional human handover"],"demoUrl":null}',
'["mkp-template-ai-support-workspace"]', 30);
