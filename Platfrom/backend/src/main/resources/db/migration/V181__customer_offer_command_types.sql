ALTER TABLE platform_customer_entitlement_commands
    DROP CONSTRAINT platform_customer_entitlement_commands_command_type_check;

ALTER TABLE platform_customer_entitlement_commands
    ALTER COLUMN command_type TYPE VARCHAR(32);

ALTER TABLE platform_customer_entitlement_commands
    ADD CONSTRAINT ck_customer_entitlement_command_type
    CHECK (command_type IN (
        'INVITE',
        'ACCEPT',
        'REVOKE',
        'BOOTSTRAP_CONFIG',
        'BIND_S3',
        'REVOKE_S3',
        'PUBLISH_VERSION'
    ));
