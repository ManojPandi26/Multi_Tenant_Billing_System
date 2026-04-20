package com.mtbs.auth.service;

import com.mtbs.auth.dto.auth.AuthResponse;
import com.mtbs.auth.dto.auth.LoginRequest;
import com.mtbs.auth.dto.auth.LogoutRequest;
import com.mtbs.auth.dto.auth.RefreshTokenRequest;
import com.mtbs.auth.dto.auth.UserProfileResponse;
import com.mtbs.auth.service.SlugCacheService;
import com.mtbs.auth.service.SlugGeneratorService;
import com.mtbs.tenant.entity.Tenant;
import com.mtbs.shared.enums.auth.Status;
import com.mtbs.shared.exception.TenantException;
import com.mtbs.shared.multitenancy.TenantContext;
import com.mtbs.shared.util.SecurityUtils;
import com.mtbs.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Tenant auth orchestrator — public schema routing only.
 *
 * Responsibilities:
 *  - Look up the Tenant row in public schema via slug
 *  - Enforce tenant-level status guards
 *  - Set TenantContext BEFORE delegating to TenantAuthService
 *  - Always clear TenantContext in a finally block
 *
 * NOT responsible for account creation (SignupService)
 * or password reset (PasswordResetService).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final TenantRepository tenantRepository;
    private final TenantAuthService tenantAuthService;
    private final SlugCacheService slugCacheService;
    private final SchemaCacheService schemaCacheService;
    private final SlugGeneratorService slugGeneratorService;

    // ── Login ─────────────────────────────────────────────────────────────────

    public AuthResponse login(LoginRequest request, String ipAddress, String deviceInfo) {
        log.info("Login attempt for tenantSlug={}", request.getTenantSlug());

        // Step 1: Resolve tenantId from slug (Redis → DB)
        Long tenantId = slugCacheService.resolveTenantId(request.getTenantSlug());

        // Step 2: Load full tenant for status check
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> TenantException.notFound(tenantId));

        // Hard-block suspended / deactivated tenants only
        if (tenant.getStatus() == Status.SUSPENDED || tenant.getStatus() == Status.INACTIVE) {
            throw TenantException.suspended(tenant.getId());
        }

        // PENDING_ONBOARDING tenants are allowed — they log back in to resume the wizard.
        // The JWT is valid; frontend checks GET /api/onboarding/status and redirects.

        // Step 3: Resolve schemaName (Redis → DB)
        String schemaName = schemaCacheService.resolveSchemaName(tenantId);

        // Step 4: Set TenantContext
        TenantContext.setTenantId(tenant.getId());
        TenantContext.setCurrentSchema(schemaName);
        try {
            return tenantAuthService.loginInTenantSchema(
                    request, tenant, ipAddress, deviceInfo);
        } finally {
            TenantContext.clear();
        }
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    public AuthResponse refreshAccessToken(RefreshTokenRequest request) {
        log.info("Refreshing access token for tenantSlug={}", request.getTenantSlug());

        // Step 1: Resolve tenantId from slug
        Long tenantId = slugCacheService.resolveTenantId(request.getTenantSlug());

        // Step 2: Load tenant for status check
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> TenantException.notFound(tenantId));

        // Allow refresh for ACTIVE and PENDING_ONBOARDING tenants
        if (tenant.getStatus() == Status.SUSPENDED || tenant.getStatus() == Status.INACTIVE) {
            throw TenantException.suspended(tenant.getId());
        }

        // Step 3: Resolve schemaName
        String schemaName = schemaCacheService.resolveSchemaName(tenantId);

        // Step 4: Set TenantContext
        TenantContext.setTenantId(tenant.getId());
        TenantContext.setCurrentSchema(schemaName);
        try {
            return tenantAuthService.refreshInTenantSchema(request, tenant);
        } finally {
            TenantContext.clear();
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    public void logout(LogoutRequest request, Long tenantId, String ipAddress, String userAgent) {
        log.info("Logout for tenantId={}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> TenantException.notFound(tenantId));

        Long userId = SecurityUtils.getCurrentUserId();
        String userEmail = SecurityUtils.getCurrentUserEmail();
        String userName = SecurityUtils.getCurrentUserName();
        String role = SecurityUtils.getCurrentRole();

        TenantContext.setTenantId(tenant.getId());
        TenantContext.setCurrentSchema(tenant.getSchemaName());
        try {
            tenantAuthService.logoutInTenantSchema(
                    request.getRefreshToken(), userId, userEmail, userName, role, 
                    tenant, ipAddress, userAgent);
        } finally {
            TenantContext.clear();
        }
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    public UserProfileResponse getCurrentUserProfile(Long userId, Long tenantId) {
        log.info("Fetching profile for userId={}, tenantId={}", userId, tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> TenantException.notFound(tenantId));

        TenantContext.setTenantId(tenant.getId());
        TenantContext.setCurrentSchema(tenant.getSchemaName());
        try {
            return tenantAuthService.getCurrentUserProfile(userId, tenant);
        } finally {
            TenantContext.clear();
        }
    }

    // ── Tenant Resolution (for two-step login) ───────────────────────────────

    public List<SlugGeneratorService.TenantOption> resolveTenantsForEmail(String email) {
        return slugGeneratorService.resolveTenantsForEmail(email);
    }
}