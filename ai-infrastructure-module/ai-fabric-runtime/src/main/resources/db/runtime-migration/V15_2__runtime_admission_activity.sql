CREATE TABLE runtime_admission_activity_mutex (id INTEGER PRIMARY KEY CHECK (id = 1));
INSERT INTO runtime_admission_activity_mutex(id) VALUES (1);
CREATE TABLE runtime_admission_activity (
    deployment_id VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    target_profile_id VARCHAR(128) NOT NULL,
    release_id VARCHAR(128) NOT NULL,
    activity_kind VARCHAR(40) NOT NULL,
    sequence BIGINT NOT NULL CHECK (sequence > 0),
    accepted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (deployment_id,target_profile_id,release_id,activity_kind)
);
