package com.mtbs.tenant.mapper;

import com.mtbs.tenant.dto.plan.CreatePlanRequest;
import com.mtbs.tenant.dto.plan.PlanResponse;
import com.mtbs.tenant.dto.plan.UpdatePlanRequest;
import com.mtbs.tenant.entity.Plan;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PlanMapper {

    PlanResponse toResponse(Plan entity);

    Plan toEntity(CreatePlanRequest request);

    void updateEntity(UpdatePlanRequest request, @MappingTarget Plan entity);
}
