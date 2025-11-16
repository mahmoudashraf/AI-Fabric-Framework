CREATE TABLE IF NOT EXISTS behavior_metrics (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    metric_date DATE NOT NULL,
    view_count INTEGER DEFAULT 0,
    click_count INTEGER DEFAULT 0,
    search_count INTEGER DEFAULT 0,
    add_to_cart_count INTEGER DEFAULT 0,
    purchase_count INTEGER DEFAULT 0,
    feedback_count INTEGER DEFAULT 0,
    session_count INTEGER DEFAULT 0,
    avg_session_duration_seconds INTEGER DEFAULT 0,
    conversion_rate DOUBLE PRECISION DEFAULT 0,
    total_revenue DOUBLE PRECISION DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_behavior_metrics_user_date ON behavior_metrics (user_id, metric_date DESC);
CREATE INDEX IF NOT EXISTS idx_behavior_metrics_date ON behavior_metrics (metric_date DESC);
