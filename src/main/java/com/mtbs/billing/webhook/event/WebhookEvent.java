package com.mtbs.billing.webhook.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Parsed representation of a Razorpay webhook event payload.
 * Created by the orchestrator after signature verification and JSON parsing,
 * before tenant resolution and handler dispatch.
 */
@Getter
@Builder
@AllArgsConstructor
public class WebhookEvent {

    private final String eventType;
    private final String paymentId;
    private final String orderId;
    private final String paymentLinkId;
    private final String paymentMethod;
    private final String referenceId;
    private final Long amountPaise;
    private final String currency;
    private final String failureCode;
    private final String failureMessage;
}
