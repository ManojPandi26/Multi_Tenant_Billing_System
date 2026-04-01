package com.mtbs.tenant.dto.plan;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreatePlanRequest {

    @NotBlank(message = "Plan name is required")
    @Size(max = 50, message = "Plan name must not exceed 50 characters")
    private String name;

    @NotBlank(message = "Display name is required")
    @Size(max = 100, message = "Display name must not exceed 100 characters")
    private String displayName;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Monthly price is required")
    @Min(value = 0, message = "Monthly price must be non-negative")
    private BigDecimal priceMonthly;

    @NotNull(message = "Annual price is required")
    @Min(value = 0, message = "Annual price must be non-negative")
    private BigDecimal priceAnnual;

    @Size(max = 3, message = "Currency code must be 3 characters")
    private String currency;

    @Min(value = 0, message = "Trial days must be non-negative")
    private Integer trialDays;

    @NotNull(message = "Max users is required")
    @Min(value = 1, message = "Max users must be at least 1")
    private Integer maxUsers;

    @Min(value = 0, message = "Max API calls must be non-negative")
    private Long maxApiCallsPerMonth;

    @Min(value = 0, message = "Max storage must be non-negative")
    private Integer maxStorageGb;

    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private Boolean isPublic = true;
}