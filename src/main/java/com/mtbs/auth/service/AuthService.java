package com.mtbs.auth.service;

import com.mtbs.auth.dto.auth.AuthResponse;
import com.mtbs.auth.dto.auth.LoginRequest;
import com.mtbs.auth.dto.auth.LogoutRequest;
import com.mtbs.auth.dto.auth.RefreshTokenRequest;
import com.mtbs.auth.dto.auth.UserProfileResponse;
import com.mtbs.tenant.entity.Tenant;
import com.mtbs.shared.enums.auth.Status;
import com.mtbs.shared.exception.TenantException;
import com.mtbs.shared.multitenancy.TenantContext;
import com.mtbs.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Tenant auth orchestrator — public schema routing only.
 *
 * Responsibilities:
 *  - Look up the Tenant row in public schema
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

    // ── Login ─────────────────────────────────────────────────────────────────

    public AuthResponse login(LoginRequest request, String ipAddress, String deviceInfo) {
        log.info("Login attempt for tenantId={}", request.getTenantId());

        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> TenantException.notFound(request.getTenantId()));

        // Hard-block suspended / deactivated tenants only
        if (tenant.getStatus() == Status.SUSPENDED || tenant.getStatus() == Status.INACTIVE) {
            throw TenantException.suspended(tenant.getId());
        }

        // PENDING_ONBOARDING tenants are allowed — they log back in to resume the wizard.
        // The JWT is valid; frontend checks GET /api/onboarding/status and redirects.

        TenantContext.setTenantId(tenant.getId());
        TenantContext.setCurrentSchema(tenant.getSchemaName());
        try {
            AuthResponse response = tenantAuthService.loginInTenantSchema(
                    request, tenant, ipAddress, deviceInfo);

            // Tell the frontend where to send the user after login
            if (tenant.getStatus() == Status.PENDING_ONBOARDING) {
                response.setOnboardingComplete(false);
                response.setOnboardingStep(tenant.getOnboardingStep());
            } else {
                response.setOnboardingComplete(true);
                response.setOnboardingStep(3);
            }

            return response;
        } finally {
            TenantContext.clear();
        }
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    public AuthResponse refreshAccessToken(RefreshTokenRequest request) {
        log.info("Refreshing access token for tenantId={}", request.getTenantId());

        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> TenantException.notFound(request.getTenantId()));

        // Allow refresh for ACTIVE and PENDING_ONBOARDING tenants
        if (tenant.getStatus() == Status.SUSPENDED || tenant.getStatus() == Status.INACTIVE) {
            throw TenantException.suspended(tenant.getId());
        }

        TenantContext.setTenantId(tenant.getId());
        TenantContext.setCurrentSchema(tenant.getSchemaName());
        try {
            return tenantAuthService.refreshInTenantSchema(request, tenant);
        } finally {
            TenantContext.clear();
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    public void logout(LogoutRequest request, Long tenantId) {
        log.info("Logout for tenantId={}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> TenantException.notFound(tenantId));

        TenantContext.setTenantId(tenant.getId());
        TenantContext.setCurrentSchema(tenant.getSchemaName());
        try {
            tenantAuthService.logoutInTenantSchema(request.getRefreshToken());
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
}