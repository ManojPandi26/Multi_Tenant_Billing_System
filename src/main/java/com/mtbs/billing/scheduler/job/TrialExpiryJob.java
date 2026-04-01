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

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrialExpiryJob implements Job {

    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    @Override
    public void execute(JobExecutionContext context) {
        log.info("Starting TrialExpiryJob");
        List<Tenant> tenants = tenantRepository.findAllByStatus(Status.ACTIVE);

        for (Tenant tenant : tenants) {
            try {
                TenantContext.setTenantId(tenant.getId());
                TenantContext.setCurrentSchema(tenant.getSchemaName());

                List<Subscription> expiredTrials = subscriptionRepository
                        .findAllByStatusAndTrialEndBefore(SubscriptionStatus.TRIALING, Instant.now());

                for (Subscription sub : expiredTrials) {
                    try {
                        subscriptionService.expireSubscription(sub.getId());
                        log.info("Expired trial subscription {} in tenant {}", sub.getId(), tenant.getSchemaName());
                    } catch (Exception e) {
                        log.error("Error expiring trial {} in tenant {}",
                                sub.getId(), tenant.getSchemaName(), e);
                    }
                }
            } finally {
                TenantContext.clear();
            }
        }
        log.info("TrialExpiryJob completed");
    }
}
