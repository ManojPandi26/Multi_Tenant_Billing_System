-- V2: Create users table in tenant schema
CREATE TABLE IF NOT EXISTS users (
    id           BIGSERIAL PRIMARY KEY,
    deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at   TIMESTAMPTZ,
    version      BIGINT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   BIGINT,
    updated_by   BIGINT,

    name         VARCHAR(255) NOT NULL,
    email        VARCHAR(255) NOT NULL UNIQUE,
    password     VARCHAR(255) NOT NULL,
    role_id      BIGINT REFERENCES roles(id),
    status       VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
