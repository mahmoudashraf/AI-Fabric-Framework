ALTER TABLE loomai_document_source ADD COLUMN preparation_json TEXT NOT NULL DEFAULT '{}';
ALTER TABLE loomai_document_manifest ADD COLUMN preparation_json TEXT NOT NULL DEFAULT '{}';
ALTER TABLE loomai_document_source ADD CONSTRAINT uk_document_evaluation_owner UNIQUE(id, tenant_id, customer_id, deployment_id, target_profile_id);
CREATE TABLE loomai_document_evaluation (
    id VARCHAR(64) PRIMARY KEY,
    source_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    customer_id VARCHAR(128) NOT NULL,
    deployment_id VARCHAR(128) NOT NULL,
    target_profile_id VARCHAR(128) NOT NULL,
    release_id VARCHAR(128) NOT NULL,
    result_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_document_evaluation_owner FOREIGN KEY (source_id, tenant_id, customer_id, deployment_id, target_profile_id)
      REFERENCES loomai_document_source(id, tenant_id, customer_id, deployment_id, target_profile_id)
);
CREATE INDEX idx_document_evaluation_source ON loomai_document_evaluation(source_id, created_at DESC);
CREATE INDEX idx_document_evaluation_status ON loomai_document_evaluation(status, created_at);
CREATE INDEX idx_document_evaluation_retention ON loomai_document_evaluation(created_at);
