package com.mtbs.billing.dto.invoice;

import com.mtbs.billing.dto.pricing.PricingResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Request object for invoice generation.
 * <p>
 * Single request object that encapsulates all data needed to generate an invoice.
 * Using a single request object (instead of overloaded methods) keeps the API
 * extensible for future billing features: GST, discounts, coupons, credits,
 * enterprise pricing, add-ons, etc.
 * </p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceGenerationRequest {

    /**
     * The subscription this invoice belongs to.
     */
    private Long subscriptionId;

    /**
     * Start of the billing period.
     */
    private Instant periodStart;

    /**
     * End of the billing period.
     */
    private Instant periodEnd;

    /**
     * The pricing result — single source of truth for plan + amount.
     */
    private PricingResult pricing;
}