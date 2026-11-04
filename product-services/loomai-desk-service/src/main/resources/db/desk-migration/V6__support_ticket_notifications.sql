CREATE TABLE desk_support_notification_subscription (
  space_id VARCHAR(160) NOT NULL,
  requester_id VARCHAR(160) NOT NULL,
  enabled BOOLEAN NOT NULL,
  protected_recipient TEXT,
  recipient_hash VARCHAR(64),
  return_url VARCHAR(2048),
  consent_id VARCHAR(64) NOT NULL,
  consented_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY(space_id,requester_id),
  FOREIGN KEY(space_id,requester_id) REFERENCES desk_support_requester(space_id,requester_id)
);
CREATE TABLE desk_support_notification_outbox (
  notification_id VARCHAR(64) PRIMARY KEY,
  space_id VARCHAR(160) NOT NULL,
  request_id VARCHAR(160) NOT NULL,
  requester_id VARCHAR(160) NOT NULL,
  event_id VARCHAR(240) NOT NULL,
  recipient_hash VARCHAR(64) NOT NULL,
  consent_id VARCHAR(64) NOT NULL,
  reference VARCHAR(160) NOT NULL,
  status VARCHAR(24) NOT NULL CHECK(status IN ('PENDING','LEASED','RETRY','SENT','FAILED','CANCELLED')),
  attempt_count INTEGER NOT NULL,
  next_attempt_at TIMESTAMP NOT NULL,
  lease_owner VARCHAR(64),
  lease_until TIMESTAMP,
  failure_code VARCHAR(100),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  UNIQUE(space_id,request_id,event_id,recipient_hash),
  FOREIGN KEY(space_id,request_id) REFERENCES desk_support_request(space_id,request_id),
  FOREIGN KEY(space_id,requester_id) REFERENCES desk_support_notification_subscription(space_id,requester_id)
);
CREATE INDEX idx_support_notification_poll ON desk_support_notification_outbox(status,next_attempt_at,lease_until);
CREATE INDEX idx_support_notification_owner ON desk_support_notification_outbox(space_id,requester_id,updated_at);
