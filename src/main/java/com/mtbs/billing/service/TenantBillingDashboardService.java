package com.mtbs.billing.service;

import com.mtbs.billing.dto.TenantBillingDashboard;
import com.mtbs.billing.dto.UsageLimitsResponse;
import com.mtbs.billing.entity.Invoice;
import com.mtbs.billing.entity.Subscription;
import com.mtbs.billing.repository.InvoiceRepository;
import com.mtbs.billing.repository.PaymentRepository;
import com.mtbs.shared.enums.billing.InvoiceStatus;
import com.mtbs.shared.enums.billing.PaymentStatus;
import com.mtbs.shared.util.SecurityUtils;
import com.mtbs.tenant.service.PlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

        UsageLimitsResponse usageLimits = usageService.getLimitsForCurrentPeriod(tenantId);

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

        long total = paymentRepository.count();
        long succeeded = paymentRepository.countByStatus(PaymentStatus.SUCCEEDED);
        long failed = paymentRepository.countByStatus(PaymentStatus.FAILED);

        TenantBillingDashboard.PaymentSummary paymentSummary = TenantBillingDashboard.PaymentSummary.builder()
                .totalPayments(total)
                .successfulPayments(succeeded)
                .failedPayments(failed)
                .build();

        return TenantBillingDashboard.builder()
                .subscription(subscriptionSummary)
                .usage(usageLimits)
                .recentInvoice(recentInvoice)
                .paymentSummary(paymentSummary)
                .build();
    }
}
