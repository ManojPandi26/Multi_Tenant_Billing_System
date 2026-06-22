package com.mtbs.billing.webhook;

import com.mtbs.shared.enums.billing.InvoiceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Result of tenant resolution during webhook processing.
 * Carries all the context needed to set TenantContext and dispatch to the correct handler.
 */
@Getter
@Builder
@AllArgsConstructor
public class TenantResolution {

    private final Long tenantId;
    private final String schemaName;
    private final String domain;
    private final InvoiceType invoiceType;
    private final Long invoiceId;
}
