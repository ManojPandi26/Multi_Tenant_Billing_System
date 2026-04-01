CREATE TABLE IF NOT EXISTS plans (
                                     id                      BIGSERIAL       PRIMARY KEY,
                                     deleted                 BOOLEAN         NOT NULL DEFAULT FALSE,
                                     deleted_at              TIMESTAMPTZ,
                                     version                 BIGINT          NOT NULL DEFAULT 0,
                                     created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by              BIGINT,
    updated_by              BIGINT,
    name                    VARCHAR(50)     NOT NULL UNIQUE,
    display_name            VARCHAR(100)    NOT NULL,
    description             TEXT,
    price_monthly           NUMERIC(10,2)   NOT NULL DEFAULT 0,
    price_annual            NUMERIC(10,2)   NOT NULL DEFAULT 0,
    currency                VARCHAR(3)      NOT NULL DEFAULT 'INR',
    trial_days              INT             NOT NULL DEFAULT 0,
    max_users               INT             NOT NULL DEFAULT 3,
    max_api_calls_per_month BIGINT          NOT NULL DEFAULT 1000,
    max_storage_gb          INT             NOT NULL DEFAULT 1,
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,
    is_public               BOOLEAN         NOT NULL DEFAULT TRUE
    );

CREATE INDEX IF NOT EXISTS idx_plans_name      ON plans (name);
CREATE INDEX IF NOT EXISTS idx_plans_is_active ON plans (is_active);