package com.mtbs.billing.service;

import com.mtbs.billing.dto.UsageResponse;
import com.mtbs.billing.dto.TenantBillingDashboard;
import com.mtbs.billing.entity.Invoice;
import com.mtbs.billing.entity.Subscription;
import com.mtbs.shared.enums.billing.InvoiceStatus;
import com.mtbs.shared.enums.billing.PaymentStatus;
import com.mtbs.billing.repository.InvoiceRepository;
import com.mtbs.billing.repository.PaymentRepository;
import com.mtbs.tenant.service.PlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Assembles the tenant billing dashboard.
 *
 * Fix applied here: UsageService.getCurrentUsage() returns UsageResponse objects
 * with current + limit populated but remaining and percentUsed left null.
 * We compute and set both fields here before returning the dashboard so the
 * frontend always has the full picture.
 *
 * Computation:
 *   remaining   = max(limit - current, 0)   — never goes negative
 *   percentUsed = (current / limit) * 100   — null when limit == -1 (unlimited)
 *                                           — 100.0 when current >= limit
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantBillingDashboardService {

    private final SubscriptionService subscriptionService;
    private final PlanService planService;
    private final UsageService usageService;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public TenantBillingDashboard getDashboard() {
        log.debug("Building tenant billing dashboard");

        // ── Subscription summary ──────────────────────────────────────────────
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

        // ── Usage — with remaining + percentUsed computed ─────────────────────
        List<UsageResponse> rawMetrics = usageService.getCurrentUsage();
        enrichUsageMetrics(rawMetrics);

        TenantBillingDashboard.UsageSummarySection usageSummary =
                TenantBillingDashboard.UsageSummarySection.builder()
                        .metrics(rawMetrics)
                        .build();

        // ── Most recent non-void invoice ──────────────────────────────────────
        TenantBillingDashboard.InvoiceSummary recentInvoice = null;
        List<Invoice> openInvoices = invoiceRepository.findByStatus(InvoiceStatus.OPEN);
        if (!openInvoices.isEmpty()) {
            Invoice latest = openInvoices.get(0);
            recentInvoice = TenantBillingDashboard.InvoiceSummary.builder()
                    .invoiceId(latest.getId())
                    .invoiceNumber(latest.getInvoiceNumber())
                    .totalAmount(latest.getTotalAmount())
                    .status(latest.getStatus().name())
                    .build();
        } else {
            // Fall back to most recent PAID invoice if no OPEN ones
            List<Invoice> paidInvoices = invoiceRepository.findByStatus(InvoiceStatus.PAID);
            if (!paidInvoices.isEmpty()) {
                Invoice latest = paidInvoices.get(0);
                recentInvoice = TenantBillingDashboard.InvoiceSummary.builder()
                        .invoiceId(latest.getId())
                        .invoiceNumber(latest.getInvoiceNumber())
                        .totalAmount(latest.getTotalAmount())
                        .status(latest.getStatus().name())
                        .build();
            }
        }

        // ── Payment summary ───────────────────────────────────────────────────
        long total      = paymentRepository.count();
        long succeeded  = paymentRepository.countByStatus(PaymentStatus.SUCCEEDED);
        long failed     = paymentRepository.countByStatus(PaymentStatus.FAILED);

        TenantBillingDashboard.PaymentSummary paymentSummary =
                TenantBillingDashboard.PaymentSummary.builder()
                        .totalPayments(total)
                        .successfulPayments(succeeded)
                        .failedPayments(failed)
                        .build();

        return TenantBillingDashboard.builder()
                .subscription(subscriptionSummary)
                .usage(usageSummary)
                .recentInvoice(recentInvoice)
                .paymentSummary(paymentSummary)
                .build();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Mutates each UsageResponse in-place, setting:
     *   remaining   = max(limit - current, 0)
     *   percentUsed = (current * 100.0) / limit  — null when limit == -1 (unlimited)
     *
     * Convention: limit == -1 means unlimited (ENTERPRISE plan). In that case:
     *   remaining   = null  (unlimited — no ceiling to count down from)
     *   percentUsed = null  (no meaningful percentage)
     */
    private void enrichUsageMetrics(List<UsageResponse> metrics) {
        if (metrics == null) return;

        for (UsageResponse m : metrics) {
            long current = m.getCurrent() != null ? m.getCurrent() : 0L;
            long limit   = m.getLimit()   != null ? m.getLimit()   : 0L;

            if (limit == -1) {
                // Unlimited plan — leave both null
                m.setRemaining(null);
                m.setPercentUsed(null);
            } else if (limit == 0) {
                // Degenerate case — treat as fully used
                m.setRemaining(0L);
                m.setPercentUsed(100.0);
            } else {
                long remaining = Math.max(limit - current, 0L);
                double pct     = Math.min((current * 100.0) / limit, 100.0);
                // Round to 1 decimal place
                pct = Math.round(pct * 10.0) / 10.0;

                m.setRemaining(remaining);
                m.setPercentUsed(pct);
            }
        }
    }
}