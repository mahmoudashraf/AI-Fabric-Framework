CREATE TABLE platform_customer_identity_links (
    id VARCHAR(64) PRIMARY KEY,
    issuer VARCHAR(512) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    platform_user_id VARCHAR(64) NOT NULL REFERENCES platform_users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_customer_identity_subject UNIQUE (issuer, subject),
    CONSTRAINT uq_customer_identity_user UNIQUE (platform_user_id)
);
