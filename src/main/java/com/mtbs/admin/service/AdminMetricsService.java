package com.mtbs.admin.service;

import com.mtbs.admin.dto.AdminMetrics;
import com.mtbs.tenant.entity.Tenant;
import com.mtbs.shared.enums.auth.Status;
import com.mtbs.shared.enums.billing.InvoiceStatus;
import com.mtbs.shared.enums.billing.PaymentStatus;
import com.mtbs.shared.enums.billing.SubscriptionStatus;
import com.mtbs.shared.multitenancy.TenantContext;
import com.mtbs.billing.repository.InvoiceRepository;
import com.mtbs.billing.repository.PaymentRepository;
import com.mtbs.billing.repository.SubscriptionRepository;
import com.mtbs.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMetricsService {

    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;

    @Cacheable(value = "admin-metrics")
    public AdminMetrics getMetrics() {
        long totalTenants = tenantRepository.count();
        long activeTenants = tenantRepository.findAllByStatus(Status.ACTIVE).size();
        long suspendedTenants = tenantRepository.findAllByStatus(Status.SUSPENDED).size();

        // Aggregate across all tenants
        long activeSubscriptions = 0;
        long trialingSubscriptions = 0;
        long pastDueSubscriptions = 0;
        long totalInvoices = 0;
        long paidInvoices = 0;
        long openInvoices = 0;
        BigDecimal totalPaymentsAmount = BigDecimal.ZERO;
        long failedPayments = 0;

        List<Tenant> tenants = tenantRepository.findAllByStatus(Status.ACTIVE);
        Map<String, Long> tenantsByPlan = new HashMap<>();

        for (Tenant tenant : tenants) {
            try {
                TenantContext.setTenantId(tenant.getId());
                TenantContext.setCurrentSchema(tenant.getSchemaName());

                activeSubscriptions += subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE);
                trialingSubscriptions += subscriptionRepository.countByStatus(SubscriptionStatus.TRIALING);
                pastDueSubscriptions += subscriptionRepository.countByStatus(SubscriptionStatus.PAST_DUE);

                totalInvoices += invoiceRepository.count();
                paidInvoices += invoiceRepository.countByStatus(InvoiceStatus.PAID);
                openInvoices += invoiceRepository.countByStatus(InvoiceStatus.OPEN);

                var payments = paymentRepository.findAll();
                for (var payment : payments) {
                    if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                        totalPaymentsAmount = totalPaymentsAmount.add(payment.getAmount());
                    }
                    if (payment.getStatus() == PaymentStatus.FAILED) {
                        failedPayments++;
                    }
                }

                // Plan distribution
                String planCode = tenant.getPlan() != null ? tenant.getPlan().getCode() : null;
                tenantsByPlan.merge(planCode != null ? planCode : "NONE", 1L, Long::sum);

            } finally {
                TenantContext.clear();
            }
        }

        return AdminMetrics.builder()
                .totalTenants(totalTenants)
                .activeTenants(activeTenants)
                .suspendedTenants(suspendedTenants)
                .tenantsByPlan(tenantsByPlan)
                .totalRevenue(totalPaymentsAmount)
                .monthlyRecurringRevenue(BigDecimal.ZERO) // Simplified â€” would need more logic
                .activeSubscriptions(activeSubscriptions)
                .trialingSubscriptions(trialingSubscriptions)
                .pastDueSubscriptions(pastDueSubscriptions)
                .totalInvoices(totalInvoices)
                .paidInvoices(paidInvoices)
                .openInvoices(openInvoices)
                .totalPaymentsAmount(totalPaymentsAmount)
                .failedPayments(failedPayments)
                .build();
    }
}
