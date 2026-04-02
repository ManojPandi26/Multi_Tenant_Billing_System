package com.mtbs.auth.service;

import com.mtbs.auth.dto.auth.*;
import com.mtbs.auth.entity.RefreshToken;
import com.mtbs.auth.entity.Role;
import com.mtbs.tenant.entity.Tenant;
import com.mtbs.auth.entity.User;
import com.mtbs.shared.enums.auth.Status;
import com.mtbs.shared.enums.notification.NotificationEvent;
import com.mtbs.billing.event.outbox.OutboxEventPublisher;
import com.mtbs.shared.event.auth.AuthNotificationEvent;
import com.mtbs.shared.event.audit.AuditLogEvent;
import com.mtbs.shared.enums.audit.AuditAction;
import com.mtbs.shared.enums.audit.AuditEntityType;
import com.mtbs.shared.exception.AuthException;
import com.mtbs.shared.exception.ResourceException;
import com.mtbs.auth.repository.RolePermissionRepository;
import com.mtbs.auth.repository.RoleRepository;
import com.mtbs.auth.repository.UserRepository;
import com.mtbs.auth.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles authentication operations that MUST run within a specific tenant
 * schema.
 * All methods are @Transactional so Spring intercepts them after the caller
 * (AuthService)
 * has already set the TenantContext.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantAuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    private final OutboxEventPublisher outboxEventPublisher;


    /**
     * Creates the ROLE_OWNER user for the new signup flow.
     * Called by SignupService AFTER TenantContext.setTenantId() has been called.
     *
     * Difference from createOwnerUser(TenantRegisterRequest, Tenant):
     *  - Takes SignupRequest (name + email + password only — no company details yet)
     *  - The user's name is a personal name at this stage, not the company name
     */
    @Transactional
    public AuthResponse createOwnerUserForSignup(SignupRequest request, Tenant tenant) {
        log.info("Creating ROLE_OWNER user for signup, tenantId={}", tenant.getId());

        Role ownerRole = roleRepository.findByName("OWNER")
                .orElseThrow(() -> ResourceException.notFound("Role", "ROLE_OWNER"));

        if (userRepository.existsByEmail(request.getEmail())) {
            throw AuthException.emailAlreadyExists(request.getEmail());
        }

        User owner = new User();
        owner.setName(request.getName());
        owner.setEmail(request.getEmail());
        owner.setPassword(passwordEncoder.encode(request.getPassword()));
        owner.setRole(ownerRole);
        owner.setStatus(Status.ACTIVE);
        User savedUser = userRepository.saveAndFlush(owner);

        // Load permissions for JWT
        List<String> permissions = rolePermissionRepository
                .findByRoleId(ownerRole.getId())
                .stream()
                .map(rp -> rp.getPermission().getName())
                .collect(Collectors.toList());

        // Generate JWT
        String accessToken = jwtTokenProvider.generateToken(
                savedUser.getId(),
                tenant.getId(),
                tenant.getSchemaName(),
                ownerRole.getName(),
                permissions
        );

        // Create refresh token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(savedUser);

        log.info("ROLE_OWNER created with userId={} for tenantId={}", savedUser.getId(), tenant.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .email(savedUser.getEmail())
                .role(ownerRole.getName())
                .userId(savedUser.getId())
                .tenantId(tenant.getId())
                .schemaName(tenant.getSchemaName())
                .build();
    }

    @Transactional
    public AuthResponse loginInTenantSchema(LoginRequest request, Tenant tenant, String ipAddress, String deviceInfo) {
        log.info("Processing login in tenant schema...");

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(AuthException::invalidCredentials);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw AuthException.invalidCredentials();
        }

        if (user.getStatus() != Status.ACTIVE) {
            throw AuthException.inactiveUser();
        }

        List<String> permissions = rolePermissionRepository.findPermissionNamesByRoleId(user.getRole().getId());

        String accessToken = jwtTokenProvider.generateToken(
                user.getId(),
                tenant.getId(),
                tenant.getSchemaName(),
                user.getRole().getName(),
                permissions);

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        outboxEventPublisher.save(AuthNotificationEvent.builder()
                .eventType(NotificationEvent.USER_LOGIN)
                .recipientEmail(user.getEmail())
                .recipientName(user.getName())
                .tenantName(tenant.getName())
                .ipAddress(ipAddress)
                .deviceInfo(deviceInfo)
                .eventTime(Instant.now())
                .build(), "User", user.getId());

        outboxEventPublisher.save(AuditLogEvent.builder()
                .action(AuditAction.LOGIN)
                .entityType(AuditEntityType.USER)
                .entityId(user.getId())
                .entityName(user.getEmail())
                .whoUserId(user.getId())
                .whoUserEmail(user.getEmail())
                .whoUserName(user.getName())
                .whoRole(user.getRole().getName())
                .contextTenantId(tenant.getId())
                .contextTenantName(tenant.getName())
                .contextIpAddress(ipAddress)
                .contextUserAgent(deviceInfo)
                .description("User logged in")
                .module("AUTH")
                .severity("INFO")
                .build(), "User", user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .email(user.getEmail())
                .role(user.getRole().getName())
                .userId(user.getId())
                .tenantId(tenant.getId())
                .schemaName(tenant.getSchemaName())
                .build();
    }

    @Transactional
    public AuthResponse refreshInTenantSchema(RefreshTokenRequest request, Tenant tenant) {
        log.info("Refreshing token in tenant schema...");

        RefreshToken validToken = refreshTokenService.validateRefreshToken(request.getRefreshToken());
        User user = validToken.getUser();

        if (user.getStatus() != Status.ACTIVE) {
            throw AuthException.inactiveUser();
        }

        List<String> permissions = rolePermissionRepository.findPermissionNamesByRoleId(user.getRole().getId());

        String accessToken = jwtTokenProvider.generateToken(
                user.getId(),
                tenant.getId(),
                tenant.getSchemaName(),
                user.getRole().getName(),
                permissions);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(validToken.getToken())
                .email(user.getEmail())
                .role(user.getRole().getName())
                .userId(user.getId())
                .tenantId(tenant.getId())
                .schemaName(tenant.getSchemaName())
                .build();
    }

    @Transactional
    public void logoutInTenantSchema(String refreshToken, Long userId, String userEmail, 
                                     String userName, String role, Tenant tenant,
                                     String ipAddress, String userAgent) {
        log.info("Logging out user in tenant schema...");
        refreshTokenService.revokeToken(refreshToken);

        outboxEventPublisher.save(AuditLogEvent.builder()
                .action(AuditAction.LOGOUT)
                .entityType(AuditEntityType.USER)
                .entityId(userId)
                .entityName(userEmail)
                .whoUserId(userId)
                .whoUserEmail(userEmail)
                .whoUserName(userName)
                .whoRole(role)
                .contextTenantId(tenant.getId())
                .contextTenantName(tenant.getName())
                .contextIpAddress(ipAddress)
                .contextUserAgent(userAgent)
                .description("User logged out")
                .module("AUTH")
                .severity("INFO")
                .build(), "User", userId);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(Long userId, Tenant tenant) {
        log.info("Fetching user profile in tenant schema...");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceException.notFound("User", String.valueOf(userId)));

        return UserProfileResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().getName())
                .tenantId(tenant.getId())
                .schemaName(tenant.getSchemaName())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
