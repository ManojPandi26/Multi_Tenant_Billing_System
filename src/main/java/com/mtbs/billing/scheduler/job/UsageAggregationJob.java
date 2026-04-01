package com.mtbs.billing.scheduler.job;

import com.mtbs.tenant.entity.Tenant;
import com.mtbs.shared.enums.auth.Status;
import com.mtbs.shared.enums.billing.SubscriptionStatus;
import com.mtbs.shared.multitenancy.TenantContext;
import com.mtbs.billing.repository.SubscriptionRepository;
import com.mtbs.tenant.repository.TenantRepository;
import com.mtbs.billing.service.UsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UsageAggregationJob implements Job {

    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UsageService usageService;

    @Override
    public void execute(JobExecutionContext context) {
        log.info("Starting UsageAggregationJob");
        List<Tenant> tenants = tenantRepository.findAllByStatus(Status.ACTIVE);

        for (Tenant tenant : tenants) {
            try {
                TenantContext.setTenantId(tenant.getId());
                TenantContext.setCurrentSchema(tenant.getSchemaName());

                subscriptionRepository.findFirstByStatusIn(
                        List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING)).ifPresent(sub -> {
                            try {
                                usageService.aggregateUsageForBilling(sub.getId());
                                log.debug("Aggregated usage for subscription {} in tenant {}", sub.getId(),
                                        tenant.getSchemaName());
                            } catch (Exception e) {
                                log.error("Error aggregating usage for subscription {} in tenant {}",
                                        sub.getId(), tenant.getSchemaName(), e);
                            }
                        });
            } finally {
                TenantContext.clear();
            }
        }
        log.info("UsageAggregationJob completed");
    }
}
