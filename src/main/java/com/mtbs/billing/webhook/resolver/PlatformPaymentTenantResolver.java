package com.mtbs.billing.webhook.resolver;

import com.mtbs.billing.webhook.TenantResolution;
import com.mtbs.billing.webhook.event.WebhookEvent;
import com.mtbs.shared.multitenancy.entity.PaymentOrderMapping;
import com.mtbs.shared.multitenancy.repository.PaymentOrderMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resolves tenant context for platform subscription payments by looking up
 * the Razorpay order ID in the cross-schema {@code payment_order_mapping} table.
 * <p>
 * This is the primary resolver for Phase 1 — all platform subscription payments
 * go through {@code PaymentService.processPayment()} which populates the mapping.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlatformPaymentTenantResolver implements WebhookTenantResolver {

    private final PaymentOrderMappingRepository mappingRepository;

    @Override
    public boolean supports(WebhookEvent event) {
        return event.getOrderId() != null && event.getPaymentLinkId() == null;
    }

    @Override
    public TenantResolution resolve(WebhookEvent event) {
        PaymentOrderMapping mapping = mappingRepository.findByRazorpayOrderId(event.getOrderId())
                .orElse(null);

        if (mapping == null) {
            log.warn("PlatformPaymentTenantResolver: no mapping for order_id={}, falling through", event.getOrderId());
            return null;
        }

        log.info("Tenant resolved from order mapping — tenantId={}, schema={}, domain={}, invoiceType={}",
                mapping.getTenantId(), mapping.getSchemaName(), mapping.getDomain(), mapping.getInvoiceType());

        return TenantResolution.builder()
                .tenantId(mapping.getTenantId())
                .schemaName(mapping.getSchemaName())
                .domain(mapping.getDomain())
                .invoiceType(mapping.getInvoiceType())
                .invoiceId(mapping.getInvoiceId())
                .build();
    }
}
