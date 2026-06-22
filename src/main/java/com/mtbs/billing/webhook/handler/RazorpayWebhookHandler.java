package com.mtbs.billing.webhook.handler;

import com.mtbs.billing.webhook.TenantResolution;
import com.mtbs.billing.webhook.event.WebhookEvent;

/**
 * Strategy interface for handling a Razorpay webhook event after tenant context
 * has been resolved and set by the orchestrator.
 * <p>
 * Implementations are domain-specific:
 * <ul>
 *   <li>{@link PlatformPaymentWebhookHandler} — platform subscription payments</li>
 *   <li>BusinessInvoiceWebhookHandler — business invoice payments (Phase 2)</li>
 * </ul>
 */
public interface RazorpayWebhookHandler {

    /**
     * Returns true if this handler can process the given event for the given domain.
     */
    boolean supports(WebhookEvent event, TenantResolution resolution);

    /**
     * Handles the webhook event. TenantContext is guaranteed to be set before this call.
     */
    void handle(WebhookEvent event, TenantResolution resolution);
}
