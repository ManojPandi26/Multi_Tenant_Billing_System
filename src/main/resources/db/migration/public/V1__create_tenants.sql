-- V1: Create tenants table in public schema
CREATE TABLE IF NOT EXISTS tenants (
    id              BIGSERIAL       PRIMARY KEY,
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by      BIGINT,
    updated_by      BIGINT,
    name            VARCHAR(255)    NOT NULL,
    schema_name     VARCHAR(63)     NOT NULL UNIQUE,
    plan_type       VARCHAR(50)     NOT NULL DEFAULT 'FREE',
    status          VARCHAR(50)     NOT NULL DEFAULT 'ACTIVE',
    owner_email     VARCHAR(255),
    slug            VARCHAR(63)     UNIQUE,
    onboarding_step INT             NOT NULL DEFAULT 0
    );

CREATE INDEX IF NOT EXISTS idx_tenants_schema_name ON tenants (schema_name);
CREATE INDEX IF NOT EXISTS idx_tenants_status      ON tenants (status);
CREATE INDEX IF NOT EXISTS idx_tenants_slug        ON tenants (slug) WHERE slug IS NOT NULL;

