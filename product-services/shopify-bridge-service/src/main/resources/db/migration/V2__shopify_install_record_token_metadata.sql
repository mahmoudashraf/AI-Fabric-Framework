ALTER TABLE shopify_install_record
    ADD COLUMN refresh_token_secret_ref VARCHAR(255);

ALTER TABLE shopify_install_record
    ADD COLUMN access_token_expires_at TIMESTAMP;

ALTER TABLE shopify_install_record
    ADD COLUMN refresh_token_expires_at TIMESTAMP;
