package com.mtbs.auth.service;

import com.mtbs.auth.dto.auth.AuthResponse;
import com.mtbs.auth.dto.auth.SignupRequest;
import com.mtbs.tenant.entity.TenantOnboarding;
import com.mtbs.tenant.entity.Tenant;
import com.mtbs.shared.enums.auth.Status;
import com.mtbs.shared.enums.notification.NotificationEvent;
import com.mtbs.tenant.enums.KycStatus;
import com.mtbs.shared.enums.plan.Plan;
import com.mtbs.billing.event.outbox.OutboxEventPublisher;
import com.mtbs.shared.event.auth.AuthNotificationEvent;
import com.mtbs.shared.event.audit.AuditLogEvent;
import com.mtbs.shared.enums.audit.AuditAction;
import com.mtbs.shared.enums.audit.AuditEntityType;
import com.mtbs.shared.exception.AuthException;
import com.mtbs.shared.multitenancy.TenantContext;
import com.mtbs.tenant.repository.TenantOnboardingRepository;
import com.mtbs.tenant.repository.TenantRepository;
import com.mtbs.tenant.service.TenantFlywayMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Handles Phase 0 of the onboarding flow — account creation only.
 *
 * Responsibilities:
 *  1. Validate email uniqueness across all tenant schemas (via public lookup)
 *  2. Create Tenant row in public schema with status=PENDING_ONBOARDING
 *  3. Provision PostgreSQL schema via Flyway
 *  4. Create ROLE_OWNER user in the new schema via TenantScopedAuthService
 *  5. Create TenantOnboarding record in public schema
 *  6. Fire USER_REGISTERED notification (welcome email)
 *  7. Return JWT — tenant can now navigate the app and resume onboarding
 *
 * NOT @Transactional at this level — public tenant save and tenant-schema
 * user creation must be in separate transaction boundaries (different schemas).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignupService {

    private final TenantRepository tenantRepository;
    private final TenantOnboardingRepository onboardingRepository;
    private final TenantFlywayMigrationService tenantFlywayMigrationService;
    private final TenantAuthService tenantScopedAuthService;
    private final OutboxEventPublisher outboxEventPublisher;

    public AuthResponse signup(SignupRequest request) {
        log.info("New signup request for email={}", request.getEmail());

        // 1. Email uniqueness check in public schema lookup table
        if (tenantRepository.existsByOwnerEmail(request.getEmail())) {
            throw AuthException.emailAlreadyExists(request.getEmail());
        }

        // 2. Derive a provisional schema name from email prefix
        String provisionalSlug = deriveProvisionalSlug(request.getEmail());
        String schemaName = "schema_" + provisionalSlug + "_" + System.currentTimeMillis();

        // 3. Persist Tenant row in public schema
        Tenant tenant = saveTenant(request, schemaName, provisionalSlug);

        // 4. Provision schema + run all tenant Flyway migrations
        try {
            tenantFlywayMigrationService.createSchemaAndMigrate(schemaName);
        } catch (Exception e) {
            log.error("Schema provisioning failed for schemaName={}", schemaName, e);
            markTenantFailed(tenant.getId());
            throw e;
        }

        // 5. Set TenantContext BEFORE any @Transactional in TenantScopedAuthService
        TenantContext.setTenantId(tenant.getId());
        TenantContext.setCurrentSchema(tenant.getSchemaName());

        // 6. Create ROLE_OWNER user in the tenant schema and return JWT
        AuthResponse authResponse = tenantScopedAuthService.createOwnerUserForSignup(request, tenant);

        // 7. Create onboarding record in public schema (after JWT is issued)
        saveOnboardingRecord(tenant.getId());

        // 8. Fire welcome notification — async, never blocks signup
        fireWelcomeNotification(request, tenant);

        log.info("Signup complete for tenantId={}, schemaName={}", tenant.getId(), schemaName);
        return authResponse;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    @Transactional
    public Tenant saveTenant(SignupRequest request, String schemaName, String provisionalSlug) {
        Tenant tenant = Tenant.builder()
                .name(request.getName())
                .schemaName(schemaName)
                .slug(provisionalSlug)
                .planType(Plan.FREE)
                .status(Status.PENDING_ONBOARDING)
                .onboardingStep(0)
                .ownerEmail(request.getEmail())
                .build();
        Tenant savedTenant = tenantRepository.save(tenant);

        outboxEventPublisher.save(AuditLogEvent.builder()
                .action(AuditAction.TENANT_CREATED)
                .entityType(AuditEntityType.TENANT)
                .entityId(savedTenant.getId())
                .entityName(savedTenant.getName())
                .whoUserEmail(request.getEmail())
                .whoUserName(request.getName())
                .whoRole("OWNER")
                .contextTenantId(savedTenant.getId())
                .contextTenantName(savedTenant.getName())
                .changesAfter(Map.of("name", savedTenant.getName(), "planType", Plan.FREE.name(), "status", Status.PENDING_ONBOARDING.name()))
                .description("New tenant signup: " + savedTenant.getName())
                .module("TENANT_MANAGEMENT")
                .build(), "Tenant", savedTenant.getId());

        return savedTenant;
    }

    @Transactional
    public void saveOnboardingRecord(Long tenantId) {
        TenantOnboarding record = TenantOnboarding.builder()
                .tenantId(tenantId)
                .kycStatus(KycStatus.PENDING)
                .build();
        onboardingRepository.save(record);
    }

    @Transactional
    public void markTenantFailed(Long tenantId) {
        tenantRepository.findById(tenantId).ifPresent(t -> {
            t.setStatus(Status.ONBOARDING_ABANDONED);
            tenantRepository.save(t);

            outboxEventPublisher.save(AuditLogEvent.builder()
                    .action(AuditAction.STATUS_CHANGE)
                    .entityType(AuditEntityType.TENANT)
                    .entityId(t.getId())
                    .entityName(t.getName())
                    .changesBefore(Map.of("status", Status.PENDING_ONBOARDING.name()))
                    .changesAfter(Map.of("status", Status.ONBOARDING_ABANDONED.name()))
                    .description("Tenant onboarding failed")
                    .module("TENANT_MANAGEMENT")
                    .severity("WARN")
                    .build(), "Tenant", t.getId());
        });
    }

    /**
     * Fires USER_REGISTERED event which triggers the welcome email.
     * Uses AuthNotificationEvent — maps to auth/welcome.html template.
     * Never throws — failure is logged and ignored so signup always succeeds.
     */
    private void fireWelcomeNotification(SignupRequest request, Tenant tenant) {
        try {
            outboxEventPublisher.save(AuthNotificationEvent.builder()
                    .eventType(NotificationEvent.USER_REGISTERED)
                    .recipientEmail(request.getEmail())
                    .recipientName(request.getName())
                    .tenantName(tenant.getName())
                    .eventTime(Instant.now())
                    .build(), "Tenant", tenant.getId());
            log.debug("USER_REGISTERED notification fired for email={}", request.getEmail());
        } catch (Exception e) {
            log.warn("Failed to fire USER_REGISTERED notification for email={}: {}",
                    request.getEmail(), e.getMessage());
        }
    }

    private String deriveProvisionalSlug(String email) {
        String prefix = email.split("@")[0];
        return prefix.replaceAll("[^a-zA-Z0-9]", "")
                .toLowerCase()
                .substring(0, Math.min(prefix.length(), 20));
    }
}