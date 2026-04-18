package com.mtbs.billing.service;

import com.mtbs.billing.dto.TenantBillingDashboard;
import com.mtbs.billing.dto.UsageLimitsResponse;
import com.mtbs.billing.entity.Invoice;
import com.mtbs.billing.entity.Payment;
import com.mtbs.billing.entity.Subscription;
import com.mtbs.billing.repository.InvoiceRepository;
import com.mtbs.billing.repository.PaymentRepository;
import com.mtbs.shared.enums.billing.AlertSeverity;
import com.mtbs.shared.enums.billing.AlertType;
import com.mtbs.shared.enums.billing.InvoiceStatus;
import com.mtbs.shared.enums.billing.PaymentStatus;
import com.mtbs.shared.enums.billing.SubscriptionStatus;
import com.mtbs.shared.util.SecurityUtils;
import com.mtbs.tenant.entity.Plan;
import com.mtbs.tenant.service.PlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantBillingDashboardService {

    private final SubscriptionService subscriptionService;
    private final PlanService planService;
    private final UsageService usageService;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;

    @Cacheable(value = "dashboard", key = "'tenant:' + #root.targetClass.name + ':' + T(com.mtbs.shared.util.SecurityUtils).getCurrentTenantId()")
    @Transactional(readOnly = true)
    public TenantBillingDashboard getDashboard() {
        log.debug("Building tenant billing dashboard");

        Long tenantId = SecurityUtils.getCurrentTenantId();

        Subscription sub = subscriptionService.getCurrentSubscriptionEntity();
        TenantBillingDashboard.SubscriptionSummary subscriptionSummary = null;

        if (sub != null) {
            String planName = null;
            try {
                planName = planService.getPlanById(sub.getPlanId()).getDisplayName();
            } catch (Exception e) {
                log.warn("Could not load plan name for planId={}", sub.getPlanId());
            }

            subscriptionSummary = TenantBillingDashboard.SubscriptionSummary.builder()
                    .subscriptionId(sub.getId())
                    .planName(planName)
                    .status(sub.getStatus())
                    .currentPeriodEnd(sub.getCurrentPeriodEnd())
                    .build();
        }

        TenantBillingDashboard.CostSummary costSummary = null;
        Plan plan = null;
        if (sub != null) {
            try {
                plan = planService.getPlanById(sub.getPlanId());
                if (plan != null) {
                    Integer trialDaysRemaining = null;
                    if (sub.getStatus() == SubscriptionStatus.TRIALING && sub.getTrialEnd() != null) {
                        long days = java.time.Duration.between(Instant.now(), sub.getTrialEnd()).toDays();
                        trialDaysRemaining = days > 0 ? (int) days : 0;
                    }

                    BigDecimal currentPeriodCost = sub.getStatus() == SubscriptionStatus.ACTIVE
                            ? plan.getPriceMonthly() : BigDecimal.ZERO;

                    costSummary = TenantBillingDashboard.CostSummary.builder()
                            .planPrice(plan.getPriceMonthly())
                            .currency(plan.getCurrency() != null ? plan.getCurrency() : "INR")
                            .billingCycle(sub.getBillingCycle() != null ? sub.getBillingCycle().name() : null)
                            .currentPeriodCost(currentPeriodCost)
                            .nextInvoiceEstimate(plan.getPriceMonthly())
                            .trialRemainingDays(trialDaysRemaining)
                            .build();
                }
            } catch (Exception e) {
                log.warn("Could not load cost summary for planId={}: {}", sub.getPlanId(), e.getMessage());
            }
        }

        TenantBillingDashboard.UpcomingBilling upcomingBilling = null;
        if (sub != null && (sub.getStatus() == SubscriptionStatus.ACTIVE || sub.getStatus() == SubscriptionStatus.TRIALING)) {
            BigDecimal amount = plan != null ? plan.getPriceMonthly() : BigDecimal.ZERO;
            upcomingBilling = TenantBillingDashboard.UpcomingBilling.builder()
                    .nextBillingDate(sub.getCurrentPeriodEnd())
                    .estimatedAmount(amount)
                    .currency(plan != null && plan.getCurrency() != null ? plan.getCurrency() : "INR")
                    .autoChargeEnabled(true)
                    .build();
        }

        UsageLimitsResponse usageLimits = usageService.getLimitsForCurrentPeriod(tenantId);
        usageLimits.setUsageTrends(usageService.getUsageTrends(tenantId, 30));

        List<TenantBillingDashboard.Alert> alerts = buildAlerts(usageLimits, 
                sub != null ? sub.getStatus() : null, 
                costSummary != null ? costSummary.getTrialRemainingDays() : null);

        TenantBillingDashboard.InvoiceSummary recentInvoice = null;
        if (sub != null) {
            Optional<Invoice> latestInvoice = invoiceRepository.findTopBySubscriptionIdOrderByCreatedAtDesc(sub.getId());
            if (latestInvoice.isPresent()) {
                Invoice invoice = latestInvoice.get();
                recentInvoice = TenantBillingDashboard.InvoiceSummary.builder()
                        .invoiceId(invoice.getId())
                        .invoiceNumber(invoice.getInvoiceNumber())
                        .amount(invoice.getTotalAmount())
                        .currency(invoice.getCurrency())
                        .status(invoice.getStatus().name())
                        .dueDate(invoice.getDueDate())
                        .paidAt(invoice.getPaidAt())
                        .build();
            }
        }

        long total = sub != null 
                ? paymentRepository.countByInvoiceIdIn(
                    invoiceRepository.findBySubscriptionIdOrderByCreatedAtDesc(sub.getId()).stream()
                        .map(Invoice::getId).toList()) 
                : 0;
        
        List<Invoice> subInvoices = sub != null 
                ? invoiceRepository.findBySubscriptionIdOrderByCreatedAtDesc(sub.getId()) 
                : List.of();
        
        long succeeded = subInvoices.isEmpty() ? 0 
                : paymentRepository.countByInvoiceIdAndStatus(subInvoices.get(0).getId(), PaymentStatus.SUCCEEDED);
        long failed = subInvoices.isEmpty() ? 0 
                : paymentRepository.countByInvoiceIdAndStatus(subInvoices.get(0).getId(), PaymentStatus.FAILED);

        BigDecimal totalAmountPaid = sub != null 
                ? paymentRepository.sumAmountBySubscriptionIdAndStatus(sub.getId(), PaymentStatus.SUCCEEDED)
                : BigDecimal.ZERO;

        Instant lastPaymentDate = null;
        String lastPaymentStatus = null;
        if (!subInvoices.isEmpty()) {
            List<Long> invoiceIds = subInvoices.stream().map(Invoice::getId).toList();
            Optional<Payment> lastPayment = paymentRepository.findTopByInvoiceIdInOrderByPaidAtDesc(invoiceIds);
            if (lastPayment.isPresent()) {
                lastPaymentDate = lastPayment.get().getPaidAt();
                lastPaymentStatus = lastPayment.get().getStatus().name();
            }
        }

        TenantBillingDashboard.PaymentSummary paymentSummary = TenantBillingDashboard.PaymentSummary.builder()
                .totalPayments(total)
                .successfulPayments(succeeded)
                .failedPayments(failed)
                .totalAmountPaid(totalAmountPaid)
                .lastPaymentDate(lastPaymentDate)
                .lastPaymentStatus(lastPaymentStatus)
                .build();

        return TenantBillingDashboard.builder()
                .subscription(subscriptionSummary)
                .costSummary(costSummary)
                .usage(usageLimits)
                .alerts(alerts)
                .upcomingBilling(upcomingBilling)
                .recentInvoice(recentInvoice)
                .paymentSummary(paymentSummary)
                .tenantHealth(computeTenantHealth(usageLimits, failed, 
                        costSummary != null ? costSummary.getTrialRemainingDays() : null,
                        sub != null ? sub.getStatus() : null))
                .build();
    }

    private TenantBillingDashboard.TenantHealth computeTenantHealth(
            UsageLimitsResponse usage, long failedPayments,
            Integer trialDaysRemaining, SubscriptionStatus status) {
        
        int riskScore = 0;
        
        if (usage.getApiCalls() != null) {
            double percent = usage.getApiCalls().getUsagePercent();
            if (percent >= 90) riskScore += 3;
            else if (percent >= 70) riskScore += 1;
        }
        
        if (usage.getUsers() != null) {
            double percent = usage.getUsers().getUsagePercent();
            if (percent >= 90) riskScore += 2;
        }
        
        if (usage.getStorage() != null) {
            double percent = usage.getStorage().getUsagePercent();
            if (percent >= 90) riskScore += 2;
        }
        
        if (failedPayments >= 3) riskScore += 3;
        else if (failedPayments >= 2) riskScore += 2;
        
        if (status == SubscriptionStatus.TRIALING && trialDaysRemaining != null) {
            if (trialDaysRemaining <= 2) riskScore += 2;
            else if (trialDaysRemaining <= 5) riskScore += 1;
        }
        
        String riskLevel, statusEnum;
        if (riskScore >= 5) {
            riskLevel = "HIGH";
            statusEnum = "CRITICAL";
        } else if (riskScore >= 2) {
            riskLevel = "MEDIUM";
            statusEnum = "WARNING";
        } else {
            riskLevel = "LOW";
            statusEnum = "HEALTHY";
        }
        
        return TenantBillingDashboard.TenantHealth.builder()
                .status(statusEnum)
                .riskLevel(riskLevel)
                .build();
    }

    private List<TenantBillingDashboard.Alert> buildAlerts(UsageLimitsResponse usage,
            SubscriptionStatus status, Integer trialDaysRemaining) {
        List<TenantBillingDashboard.Alert> alerts = new ArrayList<>();

        if (usage.getApiCalls() != null) {
            double percent = usage.getApiCalls().getUsagePercent();
            if (percent >= 90) {
                alerts.add(TenantBillingDashboard.Alert.builder()
                        .type(AlertType.USAGE_WARNING.name())
                        .message("API calls at " + (int) percent + "% of limit")
                        .severity(AlertSeverity.HIGH.name())
                        .build());
            } else if (percent >= 70) {
                alerts.add(TenantBillingDashboard.Alert.builder()
                        .type(AlertType.USAGE_WARNING.name())
                        .message("API calls at " + (int) percent + "% of limit")
                        .severity(AlertSeverity.MEDIUM.name())
                        .build());
            }
        }

        if (usage.getUsers() != null) {
            double percent = usage.getUsers().getUsagePercent();
            if (percent >= 90) {
                alerts.add(TenantBillingDashboard.Alert.builder()
                        .type(AlertType.USAGE_WARNING.name())
                        .message("User seats at " + (int) percent + "% of limit")
                        .severity(AlertSeverity.HIGH.name())
                        .build());
            } else if (percent >= 70) {
                alerts.add(TenantBillingDashboard.Alert.builder()
                        .type(AlertType.USAGE_WARNING.name())
                        .message("User seats at " + (int) percent + "% of limit")
                        .severity(AlertSeverity.MEDIUM.name())
                        .build());
            }
        }

        if (usage.getStorage() != null) {
            double percent = usage.getStorage().getUsagePercent();
            if (percent >= 90) {
                alerts.add(TenantBillingDashboard.Alert.builder()
                        .type(AlertType.USAGE_WARNING.name())
                        .message("Storage at " + (int) percent + "% of limit")
                        .severity(AlertSeverity.HIGH.name())
                        .build());
            } else if (percent >= 70) {
                alerts.add(TenantBillingDashboard.Alert.builder()
                        .type(AlertType.USAGE_WARNING.name())
                        .message("Storage at " + (int) percent + "% of limit")
                        .severity(AlertSeverity.MEDIUM.name())
                        .build());
            }
        }

        if (status == SubscriptionStatus.TRIALING && trialDaysRemaining != null && trialDaysRemaining <= 5) {
            AlertSeverity severity = trialDaysRemaining <= 2 ? AlertSeverity.HIGH : AlertSeverity.MEDIUM;
            alerts.add(TenantBillingDashboard.Alert.builder()
                    .type(AlertType.TRIAL_ENDING.name())
                    .message("Your trial ends in " + trialDaysRemaining + " days")
                    .severity(severity.name())
                    .build());
        }

        return alerts;
    }
}
