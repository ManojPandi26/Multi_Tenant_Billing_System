package com.mtbs.tenant.controller.platform;


import com.mtbs.shared.dto.common.ApiResponse;
import com.mtbs.tenant.dto.plan.CreatePlanRequest;
import com.mtbs.tenant.dto.plan.PlanResponse;
import com.mtbs.tenant.dto.plan.UpdatePlanRequest;
import com.mtbs.tenant.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
@Tag(name = "Plans", description = "Plan management endpoints")
public class PlanController {

    private final PlanService planService;

    @GetMapping
    @Operation(summary = "Get all public plans", description = "Returns all active and public plans. No authentication required.")
    public ResponseEntity<ApiResponse<List<PlanResponse>>> getPublicPlans() {
        return ResponseEntity.ok(ApiResponse.success(planService.getAllPublicPlans()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get plan by ID", description = "Returns a specific plan by ID. No authentication required.")
    public ResponseEntity<ApiResponse<PlanResponse>> getPlanById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(planService.getPlanByIdAsResponse(id)));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @Operation(summary = "Get all plans including inactive", description = "Admin endpoint to get all plans.")
    public ResponseEntity<ApiResponse<List<PlanResponse>>> getAllPlans() {
        return ResponseEntity.ok(ApiResponse.success(planService.getAllPlans()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @Operation(summary = "Create a new plan", description = "Creates a new pricing plan.")
    public ResponseEntity<ApiResponse<PlanResponse>> createPlan(@Valid @RequestBody CreatePlanRequest request) {
        PlanResponse response = planService.createPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Plan created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @Operation(summary = "Update a plan", description = "Updates an existing plan.")
    public ResponseEntity<ApiResponse<PlanResponse>> updatePlan(@PathVariable Long id,
                                                                  @Valid @RequestBody UpdatePlanRequest request) {
        return ResponseEntity.ok(ApiResponse.success(planService.updatePlan(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @Operation(summary = "Deactivate a plan", description = "Soft-deletes and deactivates a plan.")
    public ResponseEntity<ApiResponse<Void>> deactivatePlan(@PathVariable Long id) {
        planService.deactivatePlan(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Plan deactivated successfully"));
    }
}
