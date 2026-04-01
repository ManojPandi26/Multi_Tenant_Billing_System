package com.mtbs.tenant.service;

import com.mtbs.tenant.dto.tenant.TenantResponse;
import com.mtbs.tenant.dto.tenant.TenantSchemaInfoResponse;
import com.mtbs.tenant.dto.tenant.TenantStatusResponse;
import com.mtbs.tenant.dto.tenant.UpdateTenantRequest;
import com.mtbs.tenant.entity.Tenant;
import com.mtbs.shared.enums.auth.Status;
import com.mtbs.shared.enums.billing.SubscriptionStatus;
import com.mtbs.shared.exception.ResourceException;
import com.mtbs.shared.exception.TenantException;
import com.mtbs.shared.multitenancy.TenantContext;
import com.mtbs.billing.repository.SubscriptionRepository;
import com.mtbs.tenant.repository.TenantRepository;
import com.mtbs.shared.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handles self-management operations for the currently authenticated Tenant.
 * Operates primarily on the public schema.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

        private final TenantRepository tenantRepository;
        private final SubscriptionRepository subscriptionRepository;
        private final JdbcTemplate jdbcTemplate;

        @Transactional(readOnly = true)
        public TenantResponse getTenantById(Long tenantId) {
                log.info("Fetching tenant details for id: {}", tenantId);
                Tenant tenant = tenantRepository.findById(tenantId)
                                .orElseThrow(() -> TenantException.notFound(tenantId));

                return mapToResponse(tenant);
        }

        public String fetchTenantName() {
                return tenantRepository.findById(TenantContext.getTenantId())
                        .map(Tenant::getName)
                        .orElse("Unknown");
        }

        @Transactional
        public TenantResponse updateTenant(Long tenantId, UpdateTenantRequest request) {
                log.info("Updating tenant id: {}", tenantId);
                Tenant tenant = tenantRepository.findById(tenantId)
                                .orElseThrow(() -> TenantException.notFound(tenantId));

                tenant.setName(request.getName());
                tenant = tenantRepository.save(tenant);

                return mapToResponse(tenant);
        }

        @Transactional(readOnly = true)
        public TenantSchemaInfoResponse getTenantSchemaInfo(Long tenantId) {
                log.info("Fetching schema info counts for tenant id: {}", tenantId);
                Tenant tenant = tenantRepository.findById(tenantId)
                                .orElseThrow(() -> TenantException.notFound(tenantId));

                String schema = "\"" + tenant.getSchemaName() + "\"";

                Long userCount = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM " + schema + ".users WHERE deleted = false", Long.class);

                Long roleCount = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM " + schema + ".roles WHERE deleted = false", Long.class);

                Long subscriptionCount = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM " + schema + ".subscriptions WHERE deleted = false", Long.class);

                return TenantSchemaInfoResponse.builder()
                                .schemaName(tenant.getSchemaName())
                                .userCount(userCount != null ? userCount : 0L)
                                .roleCount(roleCount != null ? roleCount : 0L)
                                .subscriptionCount(subscriptionCount != null ? subscriptionCount : 0L)
                                .createdAt(tenant.getCreatedAt())
                                .build();
        }

        public TenantStatusResponse getTenantStatus(Long tenantId) {
                log.info("Fetching operational status for tenant id: {}", tenantId);

                Tenant tenant = tenantRepository.findById(tenantId)
                                .orElseThrow(() -> TenantException.notFound(tenantId));

                TenantStatusResponse.TenantStatusResponseBuilder builder = TenantStatusResponse.builder()
                                .tenantStatus(tenant.getStatus())
                                .planName(tenant.getPlanType().name())
                                .isSuspended(tenant.getStatus() != Status.ACTIVE);

                // Fetch active subscription from the tenant's schema
                TenantContext.setTenantId(tenant.getId());
                TenantContext.setCurrentSchema(tenant.getSchemaName());
                try {
                        log.debug("TenantService.getTenantStatus: schema={}", TenantContext.getTenantId());

                        // Find any subscription that is ACTIVE or TRIALING
                        subscriptionRepository
                                        .findFirstByStatusIn(
                                                        List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING))
                                        .ifPresentOrElse(
                                                        sub -> {
                                                                builder.subscriptionStatus(sub.getStatus());
                                                                builder.currentPeriodEnd(sub.getCurrentPeriodEnd());
                                                                builder.trialEndsAt(sub.getTrialEnd());
                                                        },
                                                        () -> builder.subscriptionStatus(SubscriptionStatus.UNPAID));
                } finally {
                        TenantContext.clear();
                }

                return builder.build();
        }

        @Transactional
        public void deactivateTenant(Long tenantId) {
                if (!"OWNER".equals(SecurityUtils.getCurrentRole())) {
                        throw ResourceException.accessDenied();
                }
                log.info("Deactivating tenant id: {}", tenantId);
                Tenant tenant = tenantRepository.findById(tenantId)
                                .orElseThrow(() -> TenantException.notFound(tenantId));

                tenant.setStatus(Status.INACTIVE);
                tenantRepository.save(tenant);
        }

        @Transactional
        public void reactivateTenant(Long tenantId) {
                log.info("Reactivating tenant id: {}", tenantId);
                Tenant tenant = tenantRepository.findById(tenantId)
                                .orElseThrow(() -> TenantException.notFound(tenantId));

                tenant.setStatus(Status.ACTIVE);
                tenantRepository.save(tenant);
        }

        private TenantResponse mapToResponse(Tenant tenant) {
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
