package com.mtbs.billing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsageLimitsResponse {

    private ApiCallsUsage apiCalls;
    private UsersUsage users;
    private StorageUsage storage;
    private UsageTrends usageTrends;
    private Instant periodStart;
    private Instant periodEnd;
    private String planName;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiCallsUsage {
        private long used;
        private long limit;
        private long remaining;
        private double usagePercent;
        private boolean unlimited;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsersUsage {
        private long used;
        private long limit;
        private long remaining;
        private double usagePercent;
        private boolean unlimited;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StorageUsage {
        private long usedBytes;
        private double usedGb;
        private double limitGb;
        private double remainingGb;
        private double usagePercent;
        private boolean unlimited;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageTrendPoint {
        private String date;
        private long count;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StorageTrendPoint {
        private String date;
        private double usedGb;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageTrends {
        private List<UsageTrendPoint> apiCalls;
        private List<UsageTrendPoint> users;
        private List<StorageTrendPoint> storage;
        private int days;
    }
}