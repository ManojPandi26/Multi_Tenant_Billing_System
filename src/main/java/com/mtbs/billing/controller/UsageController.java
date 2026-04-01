package com.mtbs.billing.controller;

import com.mtbs.billing.dto.UsageLimitsResponse;
import com.mtbs.billing.dto.UsageResponse;
import com.mtbs.shared.dto.common.ApiResponse;
import com.mtbs.tenant.dto.plan.PlanLimits;
import com.mtbs.billing.service.UsageService;
import com.mtbs.tenant.service.PlanLimitService;
import com.mtbs.shared.enums.billing.UsageMetric;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/usage")
@RequiredArgsConstructor
@Tag(name = "Usage", description = "Usage tracking and plan limit enforcement")
@SecurityRequirement(name = "bearerAuth")
public class UsageController {

    private final UsageService usageService;
    private final PlanLimitService planLimitService;

    // ── GET /api/usage/current ────────────────────────────────────────────────

    @GetMapping("/current")
    @Operation(
            summary = "Get current period usage",
            description = "Returns usage counts for all metrics (API_CALLS, ACTIVE_USERS, " +
                    "STORAGE_GB) for the current billing period, " +
                    "along with the plan limit for each. " +
                    "Returns an empty list if no active subscription exists."
    )
    public ResponseEntity<ApiResponse<List<UsageResponse>>> getCurrentUsage() {
        List<UsageResponse> response = usageService.getCurrentUsage();
        return ResponseEntity.ok(ApiResponse.success(response, "Current usage fetched successfully"));
    }

    // ── GET /api/usage ────────────────────────────────────────────────────────

    @GetMapping
    @Operation(
            summary = "Get usage for a specific period",
            description = "Returns aggregated usage counts for a custom date range. " +
                    "Both start and end must be ISO-8601 instants (e.g. 2026-01-01T00:00:00Z). " +
                    "Returns an empty list if no active subscription exists."
    )
    public ResponseEntity<ApiResponse<List<UsageResponse>>> getUsageForPeriod(
            @Parameter(description = "Period start (ISO-8601, e.g. 2026-01-01T00:00:00Z)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,

            @Parameter(description = "Period end (ISO-8601, e.g. 2026-01-31T23:59:59Z)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {

        if (start.isAfter(end)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("start must be before end"));
        }

        List<UsageResponse> response = usageService.getUsageForPeriod(start, end);
        return ResponseEntity.ok(ApiResponse.success(response, "Usage for period fetched successfully"));
    }

    // ── GET /api/usage/limits ─────────────────────────────────────────────────

    @GetMapping("/limits")
    @Operation(
            summary = "Get plan limits with current usage",
            description = "Returns the current plan's limits for each metric alongside " +
                    "the current usage count and usage percentage. " +
                    "Used by the frontend to render quota progress bars. " +
                    "usagePercent=-1 and limit=-1 means the metric is unlimited. " +
                    "warning=true when usagePercent >= 80. " +
                    "exceeded=true when current >= limit (and not unlimited)."
    )
    public ResponseEntity<ApiResponse<UsageLimitsResponse>> getUsageLimits() {
        PlanLimits limits = planLimitService.getCurrentLimits();
        List<UsageResponse> currentUsage = usageService.getCurrentUsage();

        List<UsageLimitsResponse.MetricLimit> metrics = new ArrayList<>();

        for (UsageResponse usage : currentUsage) {
            long limit = usage.getLimit();
            long current = usage.getCurrent();

            int percent = (limit == -1L) ? -1 : (limit > 0 ? (int) ((current * 100) / limit) : 0);
            boolean exceeded = limit != -1L && current >= limit;
            boolean warning = percent >= 80 && percent < 100;

            metrics.add(UsageLimitsResponse.MetricLimit.builder()
                    .metric(usage.getMetric().name())
                    .displayName(toDisplayName(usage.getMetric()))
                    .current(current)
                    .limit(limit)
                    .usagePercent(percent)
                    .exceeded(exceeded)
                    .warning(warning)
                    .build());
        }

        UsageLimitsResponse response = UsageLimitsResponse.builder()
                .unlimited(limits.isUnlimited())
                .metrics(metrics)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "Usage limits fetched successfully"));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String toDisplayName(UsageMetric metric) {
        return switch (metric) {
            case API_CALLS     -> "API calls this month";
            case ACTIVE_USERS  -> "Active users";
            case STORAGE_GB    -> "Storage used (GB)";
        };
    }
}