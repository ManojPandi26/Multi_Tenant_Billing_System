-- V6: Create usage_records table in tenant schema
CREATE TABLE IF NOT EXISTS usage_records (
    id                      BIGSERIAL       PRIMARY KEY,
    deleted                 BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT          NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by              BIGINT,
    updated_by              BIGINT,
    subscription_id         BIGINT          NOT NULL REFERENCES subscriptions(id),
    metric_type             VARCHAR(50)     NOT NULL,
    quantity                BIGINT          NOT NULL DEFAULT 0,
    recorded_at             TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    billing_period_start    TIMESTAMPTZ     NOT NULL,
    billing_period_end      TIMESTAMPTZ     NOT NULL
    );

CREATE INDEX IF NOT EXISTS idx_usage_records_subscription_id ON usage_records (subscription_id);
CREATE INDEX IF NOT EXISTS idx_usage_records_metric_type     ON usage_records (metric_type);
CREATE INDEX IF NOT EXISTS idx_usage_records_billing_period  ON usage_records (billing_period_start, billing_period_end);
