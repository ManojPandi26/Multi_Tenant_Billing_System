package com.mtbs.billing.scheduler.job;

import com.mtbs.billing.entity.Subscription;
import com.mtbs.tenant.entity.Tenant;
import com.mtbs.shared.enums.auth.Status;
import com.mtbs.shared.enums.billing.SubscriptionStatus;
import com.mtbs.shared.multitenancy.TenantContext;
import com.mtbs.billing.repository.SubscriptionRepository;
import com.mtbs.tenant.repository.TenantRepository;
import com.mtbs.billing.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpiryJob implements Job {

    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    @Override
    public void execute(JobExecutionContext context) {
        log.info("Starting SubscriptionExpiryJob");
        List<Tenant> tenants = tenantRepository.findAllByStatus(Status.ACTIVE);

        for (Tenant tenant : tenants) {
            try {
                TenantContext.setTenantId(tenant.getId());
                TenantContext.setCurrentSchema(tenant.getSchemaName());

                // Find PAST_DUE subscriptions past 7-day grace period
                Instant gracePeriodEnd = Instant.now().minus(Duration.ofDays(7));
                List<Subscription> pastDueSubscriptions = subscriptionRepository
                        .findAllByStatusAndCurrentPeriodEndBefore(SubscriptionStatus.PAST_DUE, gracePeriodEnd);

                for (Subscription sub : pastDueSubscriptions) {
                    try {
                        subscriptionService.expireSubscription(sub.getId());
                        // Update tenant status to SUSPENDED
                        tenant.setStatus(Status.SUSPENDED);
                        tenantRepository.save(tenant);
                        log.info("Expired subscription {} and suspended tenant {}", sub.getId(), tenant.getName());
                    } catch (Exception e) {
                        log.error("Error expiring subscription {} in tenant {}",
                                sub.getId(), tenant.getSchemaName(), e);
                    }
                }
            } finally {
                TenantContext.clear();
            }
        }
        log.info("SubscriptionExpiryJob completed");
    }
}
