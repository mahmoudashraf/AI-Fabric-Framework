ALTER TABLE platform_customer_entitlement_commands
    DROP CONSTRAINT ck_customer_entitlement_command_type;

ALTER TABLE platform_customer_entitlement_commands
    ADD CONSTRAINT ck_customer_entitlement_command_type
    CHECK (command_type IN (
        'INVITE',
        'ACCEPT',
        'REVOKE',
        'BOOTSTRAP_CONFIG',
        'BIND_S3',
        'REVOKE_S3',
        'PUBLISH_VERSION',
        'APPLY_STAGING_VERSION',
        'CREATE_STAGING_CONSUMER'
    ));
