ALTER TABLE shopify_install_record
    ADD COLUMN IF NOT EXISTS billing_tier_key VARCHAR(64);

ALTER TABLE shopify_install_record
    ADD COLUMN IF NOT EXISTS billing_status VARCHAR(64);

ALTER TABLE shopify_install_record
    ADD COLUMN IF NOT EXISTS active_subscriptions_json TEXT;

ALTER TABLE shopify_install_record
    ADD COLUMN IF NOT EXISTS billing_checked_at TIMESTAMP WITH TIME ZONE;
