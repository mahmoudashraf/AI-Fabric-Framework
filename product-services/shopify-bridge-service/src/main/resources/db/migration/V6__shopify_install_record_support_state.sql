ALTER TABLE shopify_install_record
    ADD COLUMN app_scopes_update_webhook_ready BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE shopify_install_record
    ADD COLUMN app_scopes_update_webhook_checked_at TIMESTAMP;
