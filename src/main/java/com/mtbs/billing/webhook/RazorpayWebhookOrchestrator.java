package com.mtbs.billing.webhook;

import com.mtbs.billing.webhook.event.WebhookEvent;
import com.mtbs.billing.webhook.handler.RazorpayWebhookHandler;
import com.mtbs.billing.webhook.resolver.WebhookTenantResolver;
import com.mtbs.shared.exception.PaymentException;
import com.mtbs.shared.multitenancy.TenantContext;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Central orchestrator for Razorpay webhook processing.
 * <p>
 * Responsibilities in order:
 * <ol>
 *   <li>Verify HMAC-SHA256 signature</li>
 *   <li>Parse event payload into {@link WebhookEvent}</li>
 *   <li>Resolve tenant context via {@link WebhookTenantResolver}</li>
 *   <li>Set {@link TenantContext} for the resolved tenant</li>
 *   <li>Dispatch to the correct {@link RazorpayWebhookHandler}</li>
 *   <li>Always clear {@link TenantContext} in {@code finally}</li>
 * </ol>
 * <p>
 * This replaces the pattern where individual services had to restore TenantContext,
 * making tenant resolution a first-class concern of the webhook layer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RazorpayWebhookOrchestrator {

    @Value("${razorpay.webhook-secret}")
    private String webhookSecret;

    private final List<WebhookTenantResolver> resolvers;
    private final List<RazorpayWebhookHandler> handlers;

    /**
     * Entry point: verify, resolve, set context, dispatch, cleanup.
     */
    public void process(String payload, String signature) {
        verifySignature(payload, signature);

        JSONObject root = new JSONObject(payload);
        JSONObject payloadObj = root.optJSONObject("payload");
        String eventType = root.optString("event");

        JSONObject paymentEntity = null;
        JSONObject paymentLinkEntity = null;

        if (payloadObj != null) {
            JSONObject paymentObj = payloadObj.optJSONObject("payment");
            if (paymentObj != null) {
                paymentEntity = paymentObj.optJSONObject("entity");
            }

            JSONObject paymentLinkObj = payloadObj.optJSONObject("payment_link");
            if (paymentLinkObj != null) {
                paymentLinkEntity = paymentLinkObj.optJSONObject("entity");
            }
        }

        WebhookEvent event = parseEvent(eventType, paymentEntity, paymentLinkEntity);

        TenantResolution resolution = resolveTenant(event);
        if (resolution == null) {
            log.warn("No tenant resolver found for event type={}, orderId={}, paymentLinkId={}",
                    eventType, event.getOrderId(), event.getPaymentLinkId());
            return;
        }

        try {
            TenantContext.setTenantId(resolution.getTenantId());
            TenantContext.setCurrentSchema(resolution.getSchemaName());
            log.debug("TenantContext set for webhook — tenantId={}, schema={}, domain={}",
                    resolution.getTenantId(), resolution.getSchemaName(), resolution.getDomain());

            dispatch(event, resolution);

        } finally {
            TenantContext.clear();
            log.debug("TenantContext cleared after webhook processing");
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void verifySignature(String payload, String signature) {
        try {
            boolean valid = Utils.verifyWebhookSignature(payload, signature, webhookSecret);
            if (!valid) {
                log.warn("Razorpay webhook signature verification FAILED");
                throw PaymentException.invalidSignature();
            }
        } catch (PaymentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Razorpay signature verification error: {}", e.getMessage());
            throw PaymentException.invalidSignature();
        }
    }

    private WebhookEvent parseEvent(String eventType, JSONObject paymentEntity, JSONObject paymentLinkEntity) {
        String paymentId = paymentEntity != null ? paymentEntity.optString("id", null) : null;
        String orderId = paymentEntity != null ? paymentEntity.optString("order_id", null) : null;
        String paymentMethod = paymentEntity != null ? paymentEntity.optString("method", null) : null;
        Long amountPaise = paymentEntity != null ? paymentEntity.optLong("amount", 0) : 0;
        String currency = paymentEntity != null ? paymentEntity.optString("currency", null) : null;

        String paymentLinkId = paymentLinkEntity != null ? paymentLinkEntity.optString("id", null) : null;

        String referenceId = null;
        JSONObject notes = paymentEntity != null ? paymentEntity.optJSONObject("notes") : null;
        if (notes != null) {
            referenceId = notes.optString("reference_id", null);
        }

        String failureCode = paymentEntity != null ? paymentEntity.optString("error_code", null) : null;
        String failureMessage = paymentEntity != null ? paymentEntity.optString("error_description", null) : null;

        return WebhookEvent.builder()
                .eventType(eventType)
                .paymentId(paymentId)
                .orderId(orderId)
                .paymentLinkId(paymentLinkId)
                .paymentMethod(paymentMethod)
                .referenceId(referenceId)
                .amountPaise(amountPaise)
                .currency(currency)
                .failureCode(failureCode)
                .failureMessage(failureMessage)
                .build();
    }

    private TenantResolution resolveTenant(WebhookEvent event) {
        return resolvers.stream()
                .filter(r -> r.supports(event))
                .map(r -> r.resolve(event))
                .filter(resolution -> resolution != null)
                .findFirst()
                .orElse(null);
    }

    private void dispatch(WebhookEvent event, TenantResolution resolution) {
        handlers.stream()
                .filter(h -> h.supports(event, resolution))
                .findFirst()
                .ifPresentOrElse(
                        h -> h.handle(event, resolution),
                        () -> log.warn("No handler found for event type={}, domain={}",
                                event.getEventType(), resolution.getDomain()));
    }
}
