ALTER TABLE integration_webhook_event
    ADD COLUMN IF NOT EXISTS record_key VARCHAR(500);
