package com.mtbs.billing.webhook.resolver;

import com.mtbs.billing.webhook.TenantResolution;
import com.mtbs.billing.webhook.event.WebhookEvent;

/**
 * Strategy interface for resolving a tenant from a parsed webhook event.
 * Each implementation handles a specific domain (platform subscription, business invoice).
 */
public interface WebhookTenantResolver {

    /**
     * Returns true if this resolver can handle the given event.
     */
    boolean supports(WebhookEvent event);

    /**
     * Resolves the tenant context from the event.
     * Only called if {@link #supports(WebhookEvent)} returned true.
     */
    TenantResolution resolve(WebhookEvent event);
}
