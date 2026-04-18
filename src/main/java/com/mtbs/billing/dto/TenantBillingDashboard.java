package com.mtbs.billing.dto;

import com.mtbs.shared.enums.billing.SubscriptionStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantBillingDashboard {

    private SubscriptionSummary subscription;
    private CostSummary costSummary;
    private UsageLimitsResponse usage;
    private List<Alert> alerts;
    private UpcomingBilling upcomingBilling;
    private TenantHealth tenantHealth;
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
    public static class CostSummary {
        private BigDecimal planPrice;
        private String currency;
        private String billingCycle;
        private BigDecimal currentPeriodCost;
        private BigDecimal nextInvoiceEstimate;
        private Integer trialRemainingDays;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvoiceSummary {
        private Long invoiceId;
        private String invoiceNumber;
        private BigDecimal amount;
        private String currency;
        private String status;
        private Instant dueDate;
        private Instant paidAt;
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
        private BigDecimal totalAmountPaid;
        private Instant lastPaymentDate;
        private String lastPaymentStatus;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Alert {
        private String type;
        private String message;
        private String severity;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpcomingBilling {
        private Instant nextBillingDate;
        private BigDecimal estimatedAmount;
        private String currency;
        private boolean autoChargeEnabled;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantHealth {
        private String status;
        private String riskLevel;
    }
}
