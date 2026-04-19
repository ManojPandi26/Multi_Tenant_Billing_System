package com.mtbs.admin.dto;

import com.mtbs.shared.enums.plan.PlanType;
import jakarta.validation.constraints.NotNull;
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
public class ChangeTenantPlanRequest {

    @NotNull(message = "Plan type is required")
    private PlanType planType;
}