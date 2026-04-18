package com.mtbs.billing.service;

import com.mtbs.auth.repository.UserRepository;
import com.mtbs.billing.dto.UsageLimitsResponse;
import com.mtbs.billing.dto.UsageResponse;
import com.mtbs.billing.entity.Subscription;
import com.mtbs.billing.entity.UsageRecord;
import com.mtbs.billing.repository.SubscriptionRepository;
import com.mtbs.billing.repository.UsageRecordRepository;
import com.mtbs.shared.enums.auth.Status;
import com.mtbs.shared.enums.billing.SubscriptionStatus;
import com.mtbs.shared.enums.billing.UsageMetric;
import com.mtbs.tenant.entity.Plan;
import com.mtbs.tenant.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsageService {

    private final UsageRecordRepository usageRecordRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    @Transactional
    public void recordApiCall(Long tenantId, Long subscriptionId, Instant periodStart, Instant periodEnd) {
        recordUsage(tenantId, subscriptionId, UsageMetric.API_CALLS, 1L, 0L, periodStart, periodEnd);
        log.debug("API call recorded for tenantId={}, subscriptionId={}, periodStart={}", tenantId, subscriptionId, periodStart);
    }

    @Transactional
    public void recordStorageUsage(Long tenantId, Long subscriptionId, long fileSizeBytes, Instant periodStart, Instant periodEnd) {
        recordUsage(tenantId, subscriptionId, UsageMetric.STORAGE_GB, 1L, fileSizeBytes, periodStart, periodEnd);
        log.debug("Storage usage recorded for tenantId={}, subscriptionId={}, bytes={}, periodStart={}", tenantId, subscriptionId, fileSizeBytes, periodStart);
    }

    public long getActiveUserCount(Long tenantId) {
        return userRepository.countByDeletedFalseAndStatus(Status.ACTIVE);
    }

    public long getApiCallCount(Long tenantId, Instant periodStart) {
        Long count = usageRecordRepository.sumCountByTenantAndMetricSince(
                tenantId, UsageMetric.API_CALLS, periodStart);
        return count != null ? count : 0L;
    }

    public long getStorageBytes(Long tenantId, Instant periodStart) {
        Long bytes = usageRecordRepository.sumValueBytesByTenantAndMetricAndPeriod(
                tenantId, UsageMetric.STORAGE_GB, periodStart);
        return bytes != null ? bytes : 0L;
    }

    public List<UsageResponse> getCurrentUsage() {
        Long tenantId = com.mtbs.shared.util.SecurityUtils.getCurrentTenantId();
        Subscription subscription = subscriptionRepository
                .findFirstByStatusIn(List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING))
                .orElse(null);
        
        if (subscription == null) {
            return List.of();
        }
        
        return getUsageForPeriod(subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd());
    }

    public List<UsageResponse> getUsageForPeriod(Instant start, Instant end) {
        Long tenantId = com.mtbs.shared.util.SecurityUtils.getCurrentTenantId();
        if (tenantId == null) {
            return List.of();
        }
        
        List<UsageResponse> responses = new ArrayList<>();
        
        Subscription subscription = subscriptionRepository
                .findFirstByStatusIn(List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING))
                .orElse(null);
        
        Long apiCallsLimit = null;
        Long storageLimitGb = null;
        Long usersLimit = null;
        
        if (subscription != null) {
            Plan plan = planRepository.findById(subscription.getPlanId()).orElse(null);
            if (plan != null) {
                apiCallsLimit = plan.getMaxApiCallsPerMonth();
                storageLimitGb = plan.getMaxStorageGb() != null ? plan.getMaxStorageGb().longValue() : null;
                usersLimit = plan.getMaxUsers() != null ? plan.getMaxUsers().longValue() : null;
            }
        }
        
        long apiCallsCount = 0L;
        List<UsageRecord> apiCallRecords = usageRecordRepository
                .findByTenantIdAndMetricTypeAndBillingPeriodStartBetween(
                        tenantId, UsageMetric.API_CALLS, start, end);
        for (UsageRecord record : apiCallRecords) {
            apiCallsCount += record.getQuantity();
        }
        
        responses.add(buildUsageResponse(UsageMetric.API_CALLS, apiCallsCount, apiCallsLimit, start, end));
        
        long storageBytes = 0L;
        List<UsageRecord> storageRecords = usageRecordRepository
                .findByTenantIdAndMetricTypeAndBillingPeriodStartBetween(
                        tenantId, UsageMetric.STORAGE_GB, start, end);
        for (UsageRecord record : storageRecords) {
            storageBytes += record.getValueBytes();
        }
        
        long storageBytesToGb = storageBytes;
        responses.add(buildStorageUsageResponse(UsageMetric.STORAGE_GB, storageBytesToGb, storageLimitGb, start, end));
        
        return responses;
    }
    
    private UsageResponse buildUsageResponse(UsageMetric metric, long current, Long limit, Instant start, Instant end) {
        Long remaining = null;
        Double percentUsed = null;
        
        if (limit != null && limit > 0) {
            remaining = Math.max(limit - current, 0L);
            percentUsed = Math.round((current * 100.0 / limit) * 10.0) / 10.0;
        }
        
        return UsageResponse.builder()
                .metric(metric)
                .current(current)
                .limit(limit)
                .remaining(remaining)
                .percentUsed(percentUsed)
                .periodStart(start)
                .periodEnd(end)
                .build();
    }
    
    private UsageResponse buildStorageUsageResponse(UsageMetric metric, long storageBytes, Long storageLimitGb, Instant start, Instant end) {
        Long remaining = null;
        Double percentUsed = null;
        
        if (storageLimitGb != null && storageLimitGb > 0) {
            long storageGb = storageBytes / (1024 * 1024 * 1024);
            remaining = Math.max(storageLimitGb - storageGb, 0L);
            percentUsed = Math.round((storageGb * 100.0 / storageLimitGb) * 10.0) / 10.0;
        }
        
        return UsageResponse.builder()
                .metric(metric)
                .current(storageBytes)
                .limit(storageLimitGb)
                .remaining(remaining)
                .percentUsed(percentUsed)
                .periodStart(start)
                .periodEnd(end)
                .build();
    }

    public List<UsageRecord> getUnbilledUsageRecords(Long tenantId, Instant periodStart) {
        List<UsageRecord> unbilledRecords = new ArrayList<>();
        
        List<UsageRecord> apiCallRecords = usageRecordRepository
                .findByTenantIdAndMetricTypeAndBillingPeriodStartBetweenAndIsBilledFalse(
                        tenantId, UsageMetric.API_CALLS, periodStart, Instant.now());
        unbilledRecords.addAll(apiCallRecords);
        
        List<UsageRecord> storageRecords = usageRecordRepository
                .findByTenantIdAndMetricTypeAndBillingPeriodStartBetweenAndIsBilledFalse(
                        tenantId, UsageMetric.STORAGE_GB, periodStart, Instant.now());
        unbilledRecords.addAll(storageRecords);
        
        return unbilledRecords;
    }

    public void markUsageAsBilled(Long tenantId, UsageMetric metric, Instant periodStart) {
        usageRecordRepository.markAsBilled(tenantId, metric, periodStart);
        log.info("Marked {} usage as billed for tenant {} since {}", metric, tenantId, periodStart);
    }

    public UsageLimitsResponse getLimitsForCurrentPeriod(Long tenantId) {
        Subscription subscription = subscriptionRepository
                .findFirstByStatusIn(List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING))
                .orElse(null);
        if (subscription == null) {
            return UsageLimitsResponse.builder()
                    .apiCalls(UsageLimitsResponse.ApiCallsUsage.builder()
                            .used(0)
                            .limit(0)
                            .remaining(0)
                            .usagePercent(0.0)
                            .unlimited(false)
                            .build())
                    .users(UsageLimitsResponse.UsersUsage.builder()
                            .used(0)
                            .limit(0)
                            .remaining(0)
                            .usagePercent(0.0)
                            .unlimited(false)
                            .build())
                    .storage(UsageLimitsResponse.StorageUsage.builder()
                            .usedBytes(0)
                            .usedGb(0.0)
                            .limitGb(0)
                            .remainingGb(0.0)
                            .usagePercent(0.0)
                            .unlimited(false)
                            .build())
                    .build();
        }

        Plan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new IllegalStateException("Plan not found for subscription"));

        Instant periodStart = subscription.getCurrentPeriodStart();

        long apiCallsUsed = getApiCallCount(tenantId, periodStart);
        long apiCallsLimit = plan.getMaxApiCallsPerMonth() != null ? plan.getMaxApiCallsPerMonth() : -1L;
        boolean apiCallsUnlimited = apiCallsLimit == -1L;
        long apiCallsRemaining = apiCallsUnlimited ? 0 : Math.max(0, apiCallsLimit - apiCallsUsed);
        double apiCallsUsagePercent = apiCallsLimit > 0 ? (apiCallsUsed * 100.0 / apiCallsLimit) : 0.0;

        long activeUsersCount = getActiveUserCount(tenantId);
        long usersLimit = plan.getMaxUsers() != null ? plan.getMaxUsers() : -1L;
        boolean usersUnlimited = usersLimit == -1L;
        long usersRemaining = usersUnlimited ? 0 : Math.max(0, usersLimit - activeUsersCount);
        double usersUsagePercent = usersLimit > 0 ? (activeUsersCount * 100.0 / usersLimit) : 0.0;

        long storageUsedBytes = getStorageBytes(tenantId, periodStart);
        double storageUsedGb = Math.round((storageUsedBytes / (1024.0 * 1024.0 * 1024.0)) * 100.0) / 100.0;
        long storageLimitGb = plan.getMaxStorageGb() != null ? plan.getMaxStorageGb() : -1L;
        boolean storageUnlimited = storageLimitGb == -1L;
        double storageRemainingGb = storageUnlimited ? 0.0 : Math.max(0.0, storageLimitGb - storageUsedGb);
        double storageUsagePercent = storageLimitGb > 0 ? (storageUsedGb * 100.0 / storageLimitGb) : 0.0;

        return UsageLimitsResponse.builder()
                .apiCalls(UsageLimitsResponse.ApiCallsUsage.builder()
                        .used(apiCallsUsed)
                        .limit(apiCallsUnlimited ? 0 : apiCallsLimit)
                        .remaining(apiCallsRemaining)
                        .usagePercent(apiCallsUsagePercent)
                        .unlimited(apiCallsUnlimited)
                        .build())
                .users(UsageLimitsResponse.UsersUsage.builder()
                        .used(activeUsersCount)
                        .limit(usersUnlimited ? 0 : usersLimit)
                        .remaining(usersRemaining)
                        .usagePercent(usersUsagePercent)
                        .unlimited(usersUnlimited)
                        .build())
                .storage(UsageLimitsResponse.StorageUsage.builder()
                        .usedBytes(storageUsedBytes)
                        .usedGb(storageUsedGb)
                        .limitGb(storageUnlimited ? 0 : storageLimitGb)
                        .remainingGb(storageRemainingGb)
                        .usagePercent(storageUsagePercent)
                        .unlimited(storageUnlimited)
                        .build())
                .periodStart(subscription.getCurrentPeriodStart())
                .periodEnd(subscription.getCurrentPeriodEnd())
                .planName(plan.getDisplayName())
                .build();
    }

    public UsageLimitsResponse.UsageTrends getUsageTrends(Long tenantId, int days) {
        Instant startDate = Instant.now().minus(days, ChronoUnit.DAYS);

        List<UsageLimitsResponse.UsageTrendPoint> apiCalls = fetchEventTrendData(
                tenantId, "API_CALLS", startDate);
        List<UsageLimitsResponse.UsageTrendPoint> users = fetchEventTrendData(
                tenantId, "USER_CREATED", startDate);
        List<UsageLimitsResponse.StorageTrendPoint> storage = fetchStorageTrendData(
                tenantId, "STORAGE_GB", startDate);

        return UsageLimitsResponse.UsageTrends.builder()
                .apiCalls(apiCalls)
                .users(users)
                .storage(storage)
                .days(days)
                .build();
    }

    private List<UsageLimitsResponse.UsageTrendPoint> fetchEventTrendData(
            Long tenantId, String metric, Instant startDate) {
        List<Object[]> results = usageRecordRepository.countByMetricGroupedByDate(
                tenantId, metric, startDate);
        List<UsageLimitsResponse.UsageTrendPoint> points = new ArrayList<>();
        for (Object[] row : results) {
            String date = row[0] != null ? row[0].toString() : null;
            long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            if (date != null) {
                points.add(UsageLimitsResponse.UsageTrendPoint.builder()
                        .date(date)
                        .count(count)
                        .build());
            }
        }
        return points;
    }

    private List<UsageLimitsResponse.StorageTrendPoint> fetchStorageTrendData(
            Long tenantId, String metric, Instant startDate) {
        List<Object[]> results = usageRecordRepository.sumStorageByMetricGroupedByDate(
                tenantId, metric, startDate);
        List<UsageLimitsResponse.StorageTrendPoint> points = new ArrayList<>();
        for (Object[] row : results) {
            String date = row[0] != null ? row[0].toString() : null;
            long totalBytes = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            double usedGb = Math.round((totalBytes / (1024.0 * 1024.0 * 1024.0)) * 100.0) / 100.0;
            if (date != null) {
                points.add(UsageLimitsResponse.StorageTrendPoint.builder()
                        .date(date)
                        .usedGb(usedGb)
                        .build());
            }
        }
        return points;
    }

    private void recordUsage(Long tenantId, Long subscriptionId, UsageMetric metric, Long quantity, Long valueBytes,
                             Instant periodStart, Instant periodEnd) {
        try {
            usageRecordRepository
                    .findByTenantIdAndMetricTypeAndBillingPeriodStart(tenantId, metric, periodStart)
                    .ifPresentOrElse(
                            record -> {
                                record.setQuantity(record.getQuantity() + quantity);
                                if (metric == UsageMetric.STORAGE_GB) {
                                    record.setValueBytes(record.getValueBytes() + valueBytes);
                                }
                                usageRecordRepository.save(record);
                                log.debug("Updated existing UsageRecord id={} for tenantId={}", record.getId(), tenantId);
                            },
                            () -> {
                                UsageRecord record = UsageRecord.builder()
                                        .tenantId(tenantId)
                                        .subscriptionId(subscriptionId)
                                        .metricType(metric)
                                        .quantity(quantity)
                                        .valueBytes(metric == UsageMetric.STORAGE_GB ? valueBytes : 0L)
                                        .recordedAt(Instant.now())
                                        .billingPeriodStart(periodStart)
                                        .billingPeriodEnd(periodEnd)
                                        .build();
                                usageRecordRepository.save(record);
                                log.debug("Created new UsageRecord for tenantId={}, metric={}", tenantId, metric);
                            }
                    );
        } catch (Exception e) {
            log.error("Failed to record usage for tenantId={}, metric={}: {}", tenantId, metric, e.getMessage(), e);
            throw e;
        }
    }
}
