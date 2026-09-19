CREATE TABLE IF NOT EXISTS ai_review_dispatch (
  dispatch_id VARCHAR(120) PRIMARY KEY,
  task_id VARCHAR(120) NOT NULL,
  dispatcher_id VARCHAR(160) NOT NULL,
  attempt_number INTEGER NOT NULL,
  idempotency_key VARCHAR(200) NOT NULL UNIQUE,
  status VARCHAR(40) NOT NULL,
  external_reference VARCHAR(240) NULL,
  failure_reason VARCHAR(160) NULL,
  created_at TIMESTAMP NOT NULL,
  completed_at TIMESTAMP NULL,
  version BIGINT NOT NULL,
  CONSTRAINT fk_ai_review_dispatch_task
    FOREIGN KEY (task_id) REFERENCES ai_review_task(task_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_review_dispatch_task
  ON ai_review_dispatch (task_id, attempt_number);
