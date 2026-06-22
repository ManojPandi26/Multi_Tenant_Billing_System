package com.mtbs.billing.webhook.handler;

import com.mtbs.billing.webhook.TenantResolution;
import com.mtbs.billing.webhook.event.WebhookEvent;
import com.mtbs.business.payment.service.BusinessPaymentService;
import com.mtbs.shared.multitenancy.entity.PaymentOrderMapping;
import com.mtbs.shared.multitenancy.repository.PaymentOrderMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BusinessInvoiceWebhookHandler implements RazorpayWebhookHandler {

    private final BusinessPaymentService businessPaymentService;
    private final PaymentOrderMappingRepository mappingRepository;

    @Override
    public boolean supports(WebhookEvent event, TenantResolution resolution) {
        return "BUSINESS".equals(resolution.getDomain())
                && "payment_link.paid".equals(event.getEventType());
    }

    @Override
    public void handle(WebhookEvent event, TenantResolution resolution) {
        log.info("Handling business payment_link.paid — paymentLinkId={}, paymentId={}, orderId={}",
                event.getPaymentLinkId(), event.getPaymentId(), event.getOrderId());

        businessPaymentService.recordWebhookPayment(
                resolution.getInvoiceId(),
                event.getAmountPaise(),
                event.getCurrency(),
                event.getPaymentMethod(),
                event.getPaymentLinkId());

        // Update mapping with order_id and payment_id for audit trail
        mappingRepository.findByRazorpayPaymentLinkId(event.getPaymentLinkId())
                .ifPresent(mapping -> {
                    mapping.setRazorpayOrderId(event.getOrderId());
                    mapping.setRazorpayPaymentId(event.getPaymentId());
                    mappingRepository.save(mapping);
                    log.debug("Updated payment order mapping — paymentLinkId={}, orderId={}, paymentId={}",
                            event.getPaymentLinkId(), event.getOrderId(), event.getPaymentId());
                });
    }
}
