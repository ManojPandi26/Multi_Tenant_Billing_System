package com.mtbs.billing.controller;

import com.mtbs.admin.dto.AdminMetrics;
import com.mtbs.billing.dto.TenantBillingDashboard;
import com.mtbs.shared.dto.common.ApiResponse;
import com.mtbs.admin.service.AdminMetricsService;
import com.mtbs.billing.service.TenantBillingDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Billing dashboard endpoints")
public class DashboardController {

    private final TenantBillingDashboardService dashboardService;
    private final AdminMetricsService adminMetricsService;

    @GetMapping("/billing")
    @Operation(summary = "Get tenant billing dashboard", description = "Returns billing overview for the current tenant.")
    public ResponseEntity<ApiResponse<TenantBillingDashboard>> getBillingDashboard() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getDashboard()));
    }

    @GetMapping("/admin/metrics")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @Operation(summary = "Get platform admin metrics", description = "Returns platform-wide admin metrics.")
    public ResponseEntity<ApiResponse<AdminMetrics>> getAdminMetrics() {
        return ResponseEntity.ok(ApiResponse.success(adminMetricsService.getMetrics()));
    }
}
