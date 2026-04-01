package com.mtbs.tenant.dto.plan;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlanResponse {

    private Long id;
    private String name;
    private String displayName;
    private String description;
    private BigDecimal priceMonthly;
    private BigDecimal priceAnnual;
    private String currency;
    private Integer trialDays;
    private Integer maxUsers;
    private Long maxApiCallsPerMonth;
    private Integer maxStorageGb;
    private Boolean isActive;
    private Boolean isPublic;
}