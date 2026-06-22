package com.mtbs.billing.webhook.resolver;

import com.mtbs.billing.webhook.TenantResolution;
import com.mtbs.billing.webhook.event.WebhookEvent;
import com.mtbs.shared.multitenancy.entity.PaymentOrderMapping;
import com.mtbs.shared.multitenancy.repository.PaymentOrderMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BusinessInvoiceTenantResolver implements WebhookTenantResolver {

    private final PaymentOrderMappingRepository mappingRepository;

    @Override
    public boolean supports(WebhookEvent event) {
        return event.getPaymentLinkId() != null;
    }

    @Override
    public TenantResolution resolve(WebhookEvent event) {
        PaymentOrderMapping mapping = mappingRepository.findByRazorpayPaymentLinkId(event.getPaymentLinkId())
                .orElseThrow(() -> new IllegalStateException(
                        "No payment order mapping found for payment_link_id=" + event.getPaymentLinkId()));

        log.info("Tenant resolved from payment link mapping — tenantId={}, schema={}, domain={}, invoiceType={}",
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
