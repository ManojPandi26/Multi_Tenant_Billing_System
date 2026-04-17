package com.mtbs.auth.aspect;

import com.mtbs.billing.entity.Subscription;
import com.mtbs.billing.repository.SubscriptionRepository;
import com.mtbs.billing.service.UsageService;
import com.mtbs.shared.annotation.TrackUsage;
import com.mtbs.shared.enums.billing.SubscriptionStatus;
import com.mtbs.shared.enums.billing.UsageMetric;
import com.mtbs.shared.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class UsageTrackingAspect {

    private final UsageService usageService;
    private final SubscriptionRepository subscriptionRepository;

    @Around("@annotation(trackUsage)")
    public Object trackUsage(ProceedingJoinPoint joinPoint, TrackUsage trackUsage) {
        UsageMetric metric = trackUsage.metric();

        if (metric == UsageMetric.ACTIVE_USERS) {
            log.warn("@TrackUsage with ACTIVE_USERS detected — users are counted live, skipping");
            return proceedSafely(joinPoint);
        }

        if (metric == UsageMetric.STORAGE_GB) {
            return proceedSafely(joinPoint);
        }

        Long tenantId = SecurityUtils.getCurrentTenantId();
        if (tenantId == null) {
            log.warn("No tenant context — skipping usage tracking");
            return proceedSafely(joinPoint);
        }

        Subscription subscription = subscriptionRepository
                .findFirstByStatusIn(List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING))
                .orElse(null);

        if (subscription == null) {
            log.warn("No active subscription for tenantId={} — skipping API call recording", tenantId);
            return proceedSafely(joinPoint);
        }

        if (metric == UsageMetric.API_CALLS) {
            recordApiCallAsync(tenantId, subscription.getId(), subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd());
        }

        return proceedSafely(joinPoint);
    }

    private Object proceedSafely(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            log.warn("Method threw exception during usage tracking", t);
            throw new RuntimeException(t);
        }
    }

    @Async
    public void recordApiCallAsync(Long tenantId, Long subscriptionId, Instant periodStart, Instant periodEnd) {
        try {
            log.debug("Recording API call async for tenantId={}, subscriptionId={}", tenantId, subscriptionId);
            usageService.recordApiCall(tenantId, subscriptionId, periodStart, periodEnd);
            log.debug("API call recorded successfully for tenantId={}", tenantId);
        } catch (Exception e) {
            log.error("Failed to record API call usage for tenantId={}: {}", tenantId, e.getMessage(), e);
        }
    }
}
