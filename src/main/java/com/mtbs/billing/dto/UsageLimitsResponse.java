package com.mtbs.billing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Combines plan limits with current usage counts.
 * Used by GET /api/usage/limits to let the frontend
 * render progress bars and show remaining quota.
 *
 * usagePercent: 0–100. -1 means unlimited.
 * limit: -1 means unlimited.
 * current: actual count for the current billing period.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsageLimitsResponse {

    private boolean unlimited;
    private List<MetricLimit> metrics;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricLimit {
        private String metric;       // e.g. "ACTIVE_USERS", "API_CALLS"
        private String displayName;  // e.g. "Active users"
        private long current;        // current usage count
        private long limit;          // plan limit (-1 = unlimited)
        private int usagePercent;    // 0–100, or -1 if unlimited
        private boolean exceeded;    // true if current >= limit and not unlimited
        private boolean warning;     // true if usagePercent >= 80
    }
}