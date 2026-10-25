ALTER TABLE deployment_lifecycle_commands
    ADD COLUMN verification_retry_revision INTEGER NOT NULL DEFAULT 0 CHECK (verification_retry_revision >= 0);

CREATE TABLE deployment_lifecycle_verification_retry_receipts (
    command_id VARCHAR(128) NOT NULL REFERENCES deployment_lifecycle_commands(command_id),
    verification_retry_revision INTEGER NOT NULL CHECK (verification_retry_revision >= 0),
    receipt_json TEXT NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (command_id, verification_retry_revision)
);
