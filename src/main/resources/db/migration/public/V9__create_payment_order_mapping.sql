-- V9__create_payment_order_mapping.sql
-- Cross-schema payment order lookup table for webhook tenant resolution
-- Enables the webhook orchestrator to resolve order_id → tenant without knowing the tenant schema

CREATE TABLE public.payment_order_mapping (
    id                        BIGSERIAL PRIMARY KEY,

    -- Razorpay identifiers
    razorpay_order_id           VARCHAR(100) UNIQUE,
    razorpay_payment_link_id    VARCHAR(100) UNIQUE,
    razorpay_payment_id         VARCHAR(100),

    -- Tenant resolution
    tenant_id                 BIGINT NOT NULL,
    schema_name               VARCHAR(100) NOT NULL,

    -- Domain routing
    domain                    VARCHAR(20) NOT NULL DEFAULT 'PLATFORM',
    invoice_type              VARCHAR(30),

    -- Entity reference
    invoice_id                BIGINT,

    -- Standard audit fields (inherited from AuditableEntity)
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                BIGINT,
    updated_by                BIGINT,
    deleted                   BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at                TIMESTAMPTZ,
    version                   BIGINT NOT NULL DEFAULT 0
);

-- Index for primary lookup: order_id → tenant (PLATFORM domain)
CREATE INDEX idx_pom_order_id ON public.payment_order_mapping(razorpay_order_id);

-- Index for payment_link_id → tenant (BUSINESS domain)
CREATE INDEX idx_pom_payment_link_id ON public.payment_order_mapping(razorpay_payment_link_id);

-- Index for secondary lookup: payment_id → tenant (for idempotent re-processing)
CREATE INDEX idx_pom_payment_id ON public.payment_order_mapping(razorpay_payment_id);

-- Index for tenant-scoped queries (reporting, reconciliation)
CREATE INDEX idx_pom_tenant_id ON public.payment_order_mapping(tenant_id);

-- Comment for documentation
COMMENT ON TABLE public.payment_order_mapping IS
    'Cross-schema lookup table for webhook tenant resolution. '
    'Populated at order/payment-link creation time (when TenantContext is set via JWT), '
    'queried by the webhook orchestrator (which has no TenantContext) to resolve the tenant.';
COMMENT ON COLUMN public.payment_order_mapping.razorpay_order_id IS
    'Razorpay order ID — set for PLATFORM domain, set later for BUSINESS domain after webhook';
COMMENT ON COLUMN public.payment_order_mapping.razorpay_payment_link_id IS
    'Razorpay payment link ID — set for BUSINESS domain at payment link creation';
COMMENT ON COLUMN public.payment_order_mapping.domain IS
    'Business domain: PLATFORM (subscription) or BUSINESS (customer invoice)';
COMMENT ON COLUMN public.payment_order_mapping.invoice_type IS
    'Type of invoice: UPGRADE, RENEWAL, CUSTOMER_INVOICE, ADDON, MANUAL';
