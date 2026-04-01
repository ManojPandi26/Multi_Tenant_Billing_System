package com.mtbs.tenant.dto.plan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanLimits {

    private Integer maxUsers;
    private Long maxApiCallsPerMonth;
    private Integer maxStorageGb;
    private boolean unlimited;
}