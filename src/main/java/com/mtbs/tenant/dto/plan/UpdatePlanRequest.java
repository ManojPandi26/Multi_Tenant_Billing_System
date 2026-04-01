package com.mtbs.tenant.dto.plan;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
public class UpdatePlanRequest {

    @Size(max = 100, message = "Display name must not exceed 100 characters")
    private String displayName;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Min(value = 0, message = "Monthly price must be non-negative")
    private BigDecimal priceMonthly;

    @Min(value = 0, message = "Annual price must be non-negative")
    private BigDecimal priceAnnual;

    @Size(max = 3, message = "Currency code must be 3 characters")
    private String currency;

    @Min(value = 0, message = "Trial days must be non-negative")
    private Integer trialDays;

    @Min(value = 1, message = "Max users must be at least 1")
    private Integer maxUsers;

    @Min(value = 0, message = "Max API calls must be non-negative")
    private Long maxApiCallsPerMonth;

    @Min(value = 0, message = "Max storage must be non-negative")
    private Integer maxStorageGb;

    private Boolean isActive;
    private Boolean isPublic;
}