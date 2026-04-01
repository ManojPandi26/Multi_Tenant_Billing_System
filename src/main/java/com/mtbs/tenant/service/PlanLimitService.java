package com.mtbs.tenant.service;

import com.mtbs.tenant.dto.plan.PlanLimits;
import com.mtbs.tenant.entity.Plan;
import com.mtbs.billing.entity.Subscription;
import com.mtbs.shared.enums.billing.SubscriptionStatus;
import com.mtbs.shared.enums.billing.UsageMetric;
import com.mtbs.shared.exception.ResourceException;
import com.mtbs.tenant.repository.PlanRepository;
import com.mtbs.billing.repository.SubscriptionRepository;
import com.mtbs.auth.repository.UserRepository;
import com.mtbs.billing.repository.UsageRecordRepository;
import com.mtbs.billing.service.SubscriptionService;
import com.mtbs.shared.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanLimitService {

    private final SubscriptionService subscriptionService;
    private final PlanService planService;
    private final UserRepository userRepository;
    private final UsageRecordRepository usageRecordRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    @Cacheable(
        value = "planLimits",
        key = "T(com.mtbs.shared.multitenancy.TenantContext).getCurrentTenant() + ':limits'"
    )
    public PlanLimits getCurrentLimits() {
        Subscription subscription = subscriptionService.getCurrentSubscriptionEntity();

        Plan plan;
        if (subscription == null) {
            // No subscription — fall back to FREE plan limits from DB, never hardcode
            log.warn("No active subscription found for tenant: {}, falling back to FREE plan limits",
                    SecurityUtils.getCurrentTenantId());
            plan = planRepository.findByName("FREE")
                    .orElseGet(() -> Plan.builder()
                            .maxUsers(1)
                            .maxApiCallsPerMonth(100L)
                            .maxStorageGb(0)
                            .build());
        } else {
            plan = planService.getPlanById(subscription.getPlanId());
        }

        boolean unlimited = Integer.valueOf(-1).equals(plan.getMaxUsers());

        return PlanLimits.builder()
                .maxUsers(plan.getMaxUsers())
                .maxApiCallsPerMonth(plan.getMaxApiCallsPerMonth())
                .maxStorageGb(plan.getMaxStorageGb())
                .unlimited(unlimited)
                .build();
    }

    private long resolveApiCallLimit() {
        Optional<Subscription> subOpt = subscriptionRepository.findFirstByStatusIn(
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING));

        if (subOpt.isPresent()) {
            Plan plan = planService.getPlanById(subOpt.get().getPlanId());
            if (plan != null && plan.getMaxApiCallsPerMonth() != null) {
                long limit = plan.getMaxApiCallsPerMonth();
                if (limit == -1L)
                    return -1L;
                if (limit > 0)
                    return limit;
            }
        }

        Optional<Plan> freePlanOpt = planRepository.findByName("FREE");
        if (freePlanOpt.isPresent()) {
            Long maxApi = freePlanOpt.get().getMaxApiCallsPerMonth();
            if (maxApi != null) {
                if (maxApi == -1L)
                    return -1L;
                if (maxApi > 0)
                    return maxApi;
            }
        }

        return 1000L;
    }

    public void checkApiCallLimit(long current) {
        long limit = resolveApiCallLimit();
        if (limit == -1L)
            return;

        if (current > limit) {
            throw ResourceException.planLimitExceeded("API_CALLS", limit, current);
        }
    }

    public void enforceLimitForMetric(UsageMetric metric) {
        if (metric == UsageMetric.API_CALLS) {
            return; // Handled dynamically in interceptor via checkApiCallLimit
        }

        Subscription subscription = subscriptionService.getCurrentSubscriptionEntity();
        if (subscription == null) {
            throw ResourceException.planLimitExceeded(metric.name(), 0, 0);
        }

        Plan plan = planService.getPlanById(subscription.getPlanId());
        long limit = getLimitForMetric(plan, metric);

        if (limit == -1)
            return; // Unlimited

        long current = getCurrentUsageForMetric(metric, subscription);
        if (current >= limit) {
            throw ResourceException.planLimitExceeded(metric.name(), limit, current);
        }
    }

    public void enforceUserLimit() {
        PlanLimits limits = getCurrentLimits();
        if (limits.isUnlimited())
            return;

        long currentUsers = userRepository.countActiveUsers();
        if (currentUsers >= limits.getMaxUsers()) {
            throw ResourceException.planLimitExceeded("USERS", limits.getMaxUsers(), currentUsers);
        }
    }


    private long getCurrentUsageForMetric(UsageMetric metric, Subscription subscription) {
        Long sum = usageRecordRepository.sumQuantityByMetricTypeAndBillingPeriodStartAndBillingPeriodEnd(
                metric, subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd());
        return sum != null ? sum : 0;
    }

    private long getLimitForMetric(Plan plan, UsageMetric metric) {
        return switch (metric) {
            case API_CALLS -> plan.getMaxApiCallsPerMonth();
            case ACTIVE_USERS -> plan.getMaxUsers();
            case STORAGE_GB -> plan.getMaxStorageGb();
        };
    }
}
