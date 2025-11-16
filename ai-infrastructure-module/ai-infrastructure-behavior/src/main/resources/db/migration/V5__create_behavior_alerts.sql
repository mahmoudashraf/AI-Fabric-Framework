CREATE TABLE IF NOT EXISTS behavior_alerts (
    id UUID PRIMARY KEY,
    user_id UUID,
    behavior_event_id UUID,
    alert_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16),
    message TEXT,
    context JSONB,
    detected_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_behavior_alert_user ON behavior_alerts (user_id);
CREATE INDEX IF NOT EXISTS idx_behavior_alert_type ON behavior_alerts (alert_type);
