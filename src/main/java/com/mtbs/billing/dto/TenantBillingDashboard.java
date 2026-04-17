package com.mtbs.billing.dto;

import com.mtbs.shared.enums.billing.SubscriptionStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantBillingDashboard {

    private SubscriptionSummary subscription;
    private UsageLimitsResponse usage;
    private InvoiceSummary recentInvoice;
    private PaymentSummary paymentSummary;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriptionSummary {
        private Long subscriptionId;
        private String planName;
        private SubscriptionStatus status;
        private Instant currentPeriodEnd;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvoiceSummary {
        private Long invoiceId;
        private String invoiceNumber;
        private BigDecimal totalAmount;
        private String status;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSummary {
        private long totalPayments;
        private long successfulPayments;
        private long failedPayments;
    }
}
