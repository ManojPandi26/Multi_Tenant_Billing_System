package com.mtbs.billing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsageLimitsResponse {

    private long apiCallsUsed;
    private long apiCallsLimit;
    private boolean apiCallsUnlimited;
    private double apiCallsUsagePercent;

    private long activeUsersCount;
    private long usersLimit;
    private boolean usersUnlimited;
    private double usersUsagePercent;

    private long storageUsedBytes;
    private double storageUsedGb;
    private double storageLimitGb;
    private boolean storageUnlimited;
    private double storageUsagePercent;

    private Instant periodStart;
    private Instant periodEnd;
    private String planName;
}
