package com.mtbs.billing.dto.pricing;

import com.mtbs.shared.enums.billing.BillingCycle;
import com.mtbs.tenant.entity.Plan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Result of a pricing computation — the single source of truth for plan pricing
 * during invoice generation.
 * <p>
 * Encapsulates all pricing information for a plan change (upgrade, downgrade, cycle change,
 * renewal, trial conversion) so that plan, amount, credits, and period remain consistent
 * throughout the billing flow.
 * </p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingResult {

    /**
     * The plan this pricing applies to (target plan for upgrades, current plan for renewals).
     */
    private Plan targetPlan;

    /**
     * Billing cycle for the new period.
     */
    private BillingCycle billingCycle;

    /**
     * Final amount to charge after applying proration credits.
     */
    private BigDecimal chargeAmount;

    /**
     * Proration credit applied from unused days on the current paid plan.
     * Null when no credit applies.
     */
    private BigDecimal creditAmount;

    /**
     * Full price of the target plan for the selected billing cycle (before credits).
     */
    private BigDecimal fullCyclePrice;

    /**
     * Start of the new billing period.
     */
    private Instant periodStart;

    /**
     * End of the new billing period.
     */
    private Instant periodEnd;

    /**
     * Currency code for the pricing (e.g., "INR", "USD").
     */
    private String currency;

    /**
     * Returns true if payment is required (chargeAmount > 0).
     */
    public boolean isPaymentRequired() {
        return chargeAmount != null && chargeAmount.compareTo(BigDecimal.ZERO) > 0;
    }

}