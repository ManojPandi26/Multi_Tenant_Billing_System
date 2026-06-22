package com.mtbs.billing.webhook.handler;

import com.mtbs.billing.service.PaymentService;
import com.mtbs.billing.service.SubscriptionService;
import com.mtbs.billing.webhook.TenantResolution;
import com.mtbs.billing.webhook.event.WebhookEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlatformPaymentWebhookHandler implements RazorpayWebhookHandler {

    private final PaymentService paymentService;
    private final SubscriptionService subscriptionService;

    @Override
    public boolean supports(WebhookEvent event, TenantResolution resolution) {
        return "PLATFORM".equals(resolution.getDomain())
                && ("payment.captured".equals(event.getEventType())
                || "payment.failed".equals(event.getEventType()));
    }

    @Override
    public void handle(WebhookEvent event, TenantResolution resolution) {
        switch (event.getEventType()) {
            case "payment.captured" -> {
                log.info("Handling platform payment.captured — paymentId={}, orderId={}",
                        event.getPaymentId(), event.getOrderId());
                Long invoiceId = paymentService.handlePaymentSuccess(event.getPaymentId(), event.getOrderId());
                if (invoiceId != null) {
                    subscriptionService.activatePendingUpgradeIfPresent(invoiceId);
                }
            }
            case "payment.failed" -> {
                log.info("Handling platform payment.failed — paymentId={}, code={}",
                        event.getPaymentId(), event.getFailureCode());
                paymentService.handlePaymentFailure(
                        event.getPaymentId(), event.getFailureCode(), event.getFailureMessage());
            }
            default -> log.warn("Unsupported event type '{}' for platform domain", event.getEventType());
        }
    }
}
