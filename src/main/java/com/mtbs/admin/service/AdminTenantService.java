package com.mtbs.admin.service;

import com.mtbs.admin.dto.AdminTenantDetailResponse;
import com.mtbs.admin.dto.AdminTenantListResponse;
import com.mtbs.admin.dto.ChangeTenantPlanRequest;
import com.mtbs.admin.dto.ChangeTenantStatusRequest;
import com.mtbs.tenant.dto.tenant.TenantResponse;
import com.mtbs.auth.dto.user.UserResponse;
import com.mtbs.tenant.entity.Tenant;
import com.mtbs.shared.enums.plan.Plan;
import com.mtbs.shared.enums.auth.Status;
import com.mtbs.shared.event.audit.AuditLogEvent;
import com.mtbs.shared.enums.audit.AuditAction;
import com.mtbs.shared.enums.audit.AuditEntityType;
import com.mtbs.shared.exception.TenantException;
import com.mtbs.shared.multitenancy.TenantContext;
import com.mtbs.tenant.repository.TenantRepository;
import com.mtbs.billing.event.outbox.OutboxEventPublisher;
import com.mtbs.shared.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Service for system administrators to manage all tenants globally.
 * Operates on the public schema but uses JdbcTemplate/TenantContext proxy
 * to fetch granular data from specific tenant schemas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminTenantService {

    private final TenantRepository tenantRepository;
    private final AdminTenantScopedService adminTenantScopedService;
    private final JdbcTemplate jdbcTemplate;
    private final OutboxEventPublisher outboxEventPublisher;

    @Transactional(readOnly = true)
    public Page<AdminTenantListResponse> getAllTenants(Status status, Plan planType, Pageable pageable) {
        log.info("Admin fetching all tenants. Filters -> status: {}, planType: {}", status, planType);

        Page<Tenant> tenants;
        if (status != null && planType != null) {
            tenants = tenantRepository.findByPlanTypeAndStatus(planType, status, pageable);
        } else if (status != null) {
            tenants = tenantRepository.findByStatus(status, pageable);
        } else if (planType != null) {
            tenants = tenantRepository.findByPlanType(planType, pageable);
        } else {
            tenants = tenantRepository.findAll(pageable);
        }

        return tenants.map(this::mapToListResponse);
    }

    @Transactional(readOnly = true)
    public AdminTenantDetailResponse getTenantDetail(Long tenantId) {
        log.info("Admin fetching tenant details: {}", tenantId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> TenantException.notFound(tenantId));

        String schema = "\"" + tenant.getSchemaName() + "\"";

        Long userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".users WHERE deleted = false", Long.class);

        return AdminTenantDetailResponse.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .schemaName(tenant.getSchemaName())
                .planType(tenant.getPlanType())
                .status(tenant.getStatus())
                .userCount(userCount != null ? userCount : 0L)
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }

    @Transactional
    public TenantResponse changeTenantStatus(Long tenantId, ChangeTenantStatusRequest request) {
        log.info("Admin changing tenant status: {} to {}", tenantId, request.getStatus());
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> TenantException.notFound(tenantId));

        tenant.setStatus(request.getStatus());
        tenant = tenantRepository.save(tenant);

        outboxEventPublisher.save(AuditLogEvent.builder()
                .action(AuditAction.STATUS_CHANGE)
                .entityType(AuditEntityType.TENANT)
                .entityId(tenant.getId())
                .entityName(tenant.getName())
                .whoUserId(SecurityUtils.getCurrentUserId())
                .whoUserEmail(SecurityUtils.getCurrentUserEmail())
                .whoUserName(SecurityUtils.getCurrentUserName())
                .whoRole(SecurityUtils.getCurrentRole())
                .contextTenantId(tenant.getId())
                .contextTenantName(tenant.getName())
                .changesAfter(Map.of("status", request.getStatus().name()))
                .description("Admin changed tenant status to: " + request.getStatus())
                .module("ADMIN_TENANT_MANAGEMENT")
                .build(), "Tenant", tenant.getId());

        return mapToTenantResponse(tenant);
    }

    @Transactional
    public TenantResponse changeTenantPlan(Long tenantId, ChangeTenantPlanRequest request) {
        log.info("Admin changing tenant plan: {} to {}", tenantId, request.getPlanType());
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> TenantException.notFound(tenantId));

        tenant.setPlanType(request.getPlanType());
        tenant = tenantRepository.save(tenant);

        outboxEventPublisher.save(AuditLogEvent.builder()
                .action(AuditAction.UPDATE)
                .entityType(AuditEntityType.TENANT)
                .entityId(tenant.getId())
                .entityName(tenant.getName())
                .whoUserId(SecurityUtils.getCurrentUserId())
                .whoUserEmail(SecurityUtils.getCurrentUserEmail())
                .whoUserName(SecurityUtils.getCurrentUserName())
                .whoRole(SecurityUtils.getCurrentRole())
                .contextTenantId(tenant.getId())
                .contextTenantName(tenant.getName())
                .changesAfter(Map.of("planType", request.getPlanType().name()))
                .description("Admin changed tenant plan to: " + request.getPlanType())
                .module("ADMIN_TENANT_MANAGEMENT")
                .build(), "Tenant", tenant.getId());

        return mapToTenantResponse(tenant);
    }

    public Page<UserResponse> getTenantUsers(Long tenantId, Pageable pageable) {
        log.info("Admin fetching users for tenant: {}", tenantId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> TenantException.notFound(tenantId));

        TenantContext.setTenantId(tenant.getId());
        try {
            log.debug("AdminTenantService.getTenantUsers: switching to schema={}", TenantContext.getTenantId());
            return adminTenantScopedService.getUsersInTenant(pageable);
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    public void deleteTenant(Long tenantId) {
        log.info("Admin soft deleting tenant: {}", tenantId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> TenantException.notFound(tenantId));

        tenant.setDeleted(true);
        tenant.setDeletedAt(Instant.now());
        tenantRepository.save(tenant);

        outboxEventPublisher.save(AuditLogEvent.builder()
                .action(AuditAction.DELETE)
                .entityType(AuditEntityType.TENANT)
                .entityId(tenant.getId())
                .entityName(tenant.getName())
                .whoUserId(SecurityUtils.getCurrentUserId())
                .whoUserEmail(SecurityUtils.getCurrentUserEmail())
                .whoUserName(SecurityUtils.getCurrentUserName())
                .whoRole(SecurityUtils.getCurrentRole())
                .contextTenantId(tenant.getId())
                .contextTenantName(tenant.getName())
                .description("Admin deleted tenant: " + tenant.getName())
                .module("ADMIN_TENANT_MANAGEMENT")
                .severity("WARN")
                .build(), "Tenant", tenant.getId());
    }

    private AdminTenantListResponse mapToListResponse(Tenant tenant) {
        String schema = "\"" + tenant.getSchemaName() + "\"";
        Long userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".users WHERE deleted = false", Long.class);

        return AdminTenantListResponse.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .schemaName(tenant.getSchemaName())
                .planType(tenant.getPlanType())
                .status(tenant.getStatus())
                .userCount(userCount != null ? userCount : 0L)
                .createdAt(tenant.getCreatedAt())
                .build();
    }

    private TenantResponse mapToTenantResponse(Tenant tenant) {
        return TenantResponse.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .schemaName(tenant.getSchemaName())
                .planType(tenant.getPlanType())
                .status(tenant.getStatus())
                .createdAt(tenant.getCreatedAt())
                .build();
    }
}
