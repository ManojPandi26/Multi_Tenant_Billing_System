package com.mtbs.billing.repository;

import com.mtbs.billing.entity.UsageRecord;
import com.mtbs.shared.enums.billing.UsageMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface UsageRecordRepository extends JpaRepository<UsageRecord, Long> {

    List<UsageRecord> findByMetricTypeAndBillingPeriodStartAndBillingPeriodEnd(
            UsageMetric type, Instant start, Instant end);

    @Query("SELECT COALESCE(SUM(u.quantity), 0) FROM UsageRecord u " +
           "WHERE u.metricType = :metricType " +
           "AND u.billingPeriodStart = :start " +
           "AND u.billingPeriodEnd = :end " +
           "AND u.deleted = false")
    Long sumQuantityByMetricTypeAndBillingPeriodStartAndBillingPeriodEnd(
            @Param("metricType") UsageMetric metricType,
            @Param("start") Instant start,
            @Param("end") Instant end);

    List<UsageRecord> findBySubscriptionIdAndBillingPeriodStartAndBillingPeriodEnd(
            Long subscriptionId, Instant start, Instant end);
}
