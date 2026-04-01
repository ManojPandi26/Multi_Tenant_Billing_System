package com.mtbs.billing.service;

import com.mtbs.billing.dto.UsageResponse;
import com.mtbs.tenant.entity.Plan;
import com.mtbs.billing.entity.Subscription;
import com.mtbs.billing.entity.UsageRecord;
import com.mtbs.billing.entity.UsageSummary;
import com.mtbs.shared.enums.billing.UsageMetric;
import com.mtbs.shared.enums.notification.NotificationEvent;
import com.mtbs.shared.event.billing.BillingEvent;
import com.mtbs.billing.event.BillingEventPublisher;
import com.mtbs.shared.exception.ResourceException;
import com.mtbs.billing.repository.SubscriptionRepository;
import com.mtbs.billing.repository.UsageRecordRepository;
import com.mtbs.billing.repository.UsageSummaryRepository;
import com.mtbs.tenant.service.PlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsageService {

    private static final int WARNING_THRESHOLD_PERCENT = 80;

    private final UsageRecordRepository usageRecordRepository;
    private final UsageSummaryRepository usageSummaryRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final PlanService planService;
    private final BillingEventPublisher billingEventPublisher;

    // ── Record usage — called by @TrackUsage AOP ──────────────────────────────

    /**
     * Records a single usage event for the current subscription's billing period.
     * Called by UsageTrackingAspect after a @TrackUsage-annotated method succeeds.
     * Never called directly from a controller.
     */
    @Transactional
    public void recordUsage(UsageMetric metric, long quantity) {
        Subscription subscription = subscriptionService.getCurrentSubscriptionEntity();
        if (subscription == null) {
            log.debug("No active subscription — skipping usage recording for metric={}", metric);
            return;
        }

        UsageRecord record = UsageRecord.builder()
                .subscriptionId(subscription.getId())
                .metricType(metric)
                .quantity(quantity)
                .recordedAt(Instant.now())
                .billingPeriodStart(subscription.getCurrentPeriodStart())
                .billingPeriodEnd(subscription.getCurrentPeriodEnd())
                .build();

        usageRecordRepository.save(record);
        log.debug("Usage recorded — metric={}, quantity={}", metric, quantity);
    }

    // ── Query current period usage — exposed via controller ───────────────────

    /**
     * Returns usage vs limits for all metrics in the current billing period.
     * Exposed via GET /api/usage/current.
     */
    public List<UsageResponse> getCurrentUsage() {
        Subscription subscription = subscriptionService.getCurrentSubscriptionEntity();
        if (subscription == null) {
            return List.of();
        }

        Plan plan = planService.getPlanById(subscription.getPlanId());
        List<UsageResponse> result = new ArrayList<>();

        for (UsageMetric metric : UsageMetric.values()) {
            long current = safeSum(usageRecordRepository
                    .sumQuantityByMetricTypeAndBillingPeriodStartAndBillingPeriodEnd(
                            metric,
                            subscription.getCurrentPeriodStart(),
                            subscription.getCurrentPeriodEnd()));

            long limit = getLimitForMetric(plan, metric);

            result.add(UsageResponse.builder()
                    .metric(metric)
                    .current(current)
                    .limit(limit)
                    .periodStart(subscription.getCurrentPeriodStart())
                    .periodEnd(subscription.getCurrentPeriodEnd())
                    .build());
        }

        return result;
    }

    /**
     * Returns usage for a specific date range.
     * Exposed via GET /api/usage?start=&end=.
     */
    public List<UsageResponse> getUsageForPeriod(Instant start, Instant end) {
        Subscription subscription = subscriptionService.getCurrentSubscriptionEntity();
        if (subscription == null) {
            return List.of();
        }

        Plan plan = planService.getPlanById(subscription.getPlanId());
        List<UsageResponse> result = new ArrayList<>();

        for (UsageMetric metric : UsageMetric.values()) {
            long current = safeSum(usageRecordRepository
                    .sumQuantityByMetricTypeAndBillingPeriodStartAndBillingPeriodEnd(
                            metric, start, end));

            long limit = getLimitForMetric(plan, metric);

            result.add(UsageResponse.builder()
                    .metric(metric)
                    .current(current)
                    .limit(limit)
                    .periodStart(start)
                    .periodEnd(end)
                    .build());
        }

        return result;
    }

    // ── Enforce limits — called by @TrackUsage AOP ────────────────────────────

    /**
     * Checks whether the given metric is at or over its plan limit.
     * Fires USAGE_LIMIT_WARNING at 80% and throws at 100%.
     * Called by UsageTrackingAspect before the annotated method executes.
     * Never called directly from a controller.
     */
    public void checkAndEnforceLimits(UsageMetric metric) {
        Subscription subscription = subscriptionService.getCurrentSubscriptionEntity();
        if (subscription == null) {
            return;
        }

        Plan plan = planService.getPlanById(subscription.getPlanId());
        long limit = getLimitForMetric(plan, metric);

        if (limit == -1L) {
            return; // Unlimited
        }

        long current = safeSum(usageRecordRepository
                .sumQuantityByMetricTypeAndBillingPeriodStartAndBillingPeriodEnd(
                        metric,
                        subscription.getCurrentPeriodStart(),
                        subscription.getCurrentPeriodEnd()));

        if (limit > 0) {
            int percent = (int) ((current * 100) / limit);

            // Fire warning at 80% — but only on exact crossing (current+1 will hit 80%)
            if (percent >= WARNING_THRESHOLD_PERCENT && percent < 100) {
                fireUsageEvent(NotificationEvent.USAGE_LIMIT_WARNING, metric, current, limit, percent);
            }
        }

        if (current >= limit) {
            fireUsageEvent(NotificationEvent.USAGE_LIMIT_REACHED, metric, current, limit, 100);
            throw ResourceException.planLimitExceeded(metric.name(), limit, current);
        }
    }

    // ── Aggregate — called by UsageAggregationJob (scheduler only) ───────────

    /**
     * Aggregates raw UsageRecord rows into UsageSummary per metric per billing period.
     * Called hourly by UsageAggregationJob — never from a controller.
     */
    @Transactional
    public void aggregateUsageForBilling(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElse(null);

        if (subscription == null) {
            log.warn("aggregateUsageForBilling called with unknown subscriptionId={}", subscriptionId);
            return;
        }

        Instant periodStart = subscription.getCurrentPeriodStart();
        Instant periodEnd = subscription.getCurrentPeriodEnd();

        for (UsageMetric metric : UsageMetric.values()) {
            long total = safeSum(usageRecordRepository
                    .sumQuantityByMetricTypeAndBillingPeriodStartAndBillingPeriodEnd(
                            metric, periodStart, periodEnd));

            Optional<UsageSummary> existing = usageSummaryRepository
                    .findBySubscriptionIdAndMetricTypeAndBillingPeriodStartAndBillingPeriodEnd(
                            subscriptionId, metric, periodStart, periodEnd);

            if (existing.isPresent()) {
                UsageSummary summary = existing.get();
                summary.setTotalQuantity(total);
                usageSummaryRepository.save(summary);
            } else {
                UsageSummary summary = UsageSummary.builder()
                        .subscriptionId(subscriptionId)
                        .metricType(metric)
                        .totalQuantity(total)
                        .billingPeriodStart(periodStart)
                        .billingPeriodEnd(periodEnd)
                        .isBilled(false)
                        .build();
                usageSummaryRepository.save(summary);
            }
        }

        log.debug("Usage aggregated for subscriptionId={}", subscriptionId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public long getLimitForMetric(Plan plan, UsageMetric metric) {
        return switch (metric) {
            case API_CALLS     -> plan.getMaxApiCallsPerMonth() != null ? plan.getMaxApiCallsPerMonth() : -1L;
            case ACTIVE_USERS  -> plan.getMaxUsers() != null ? plan.getMaxUsers() : -1L;
            case STORAGE_GB    -> plan.getMaxStorageGb() != null ? plan.getMaxStorageGb() : -1L;
        };
    }

    private long safeSum(Long value) {
        return value != null ? value : 0L;
    }

    private void fireUsageEvent(NotificationEvent eventType, UsageMetric metric,
                                long current, long limit, int percent) {
        try {
            billingEventPublisher.publish(BillingEvent.builder()
                    .eventType(eventType)
                    .metricName(metric.name())
                    .currentUsage(current)
                    .usageLimit(limit)
                    .usagePercent(percent)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to fire {} event for metric={}: {}", eventType, metric, e.getMessage());
        }
    }
}