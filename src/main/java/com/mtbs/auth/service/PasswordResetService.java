package com.mtbs.auth.service;

import com.mtbs.auth.entity.User;
import com.mtbs.tenant.entity.Tenant;
import com.mtbs.shared.enums.notification.NotificationEvent;
import com.mtbs.billing.event.outbox.OutboxEventPublisher;
import com.mtbs.shared.event.auth.AuthNotificationEvent;
import com.mtbs.shared.event.audit.AuditLogEvent;
import com.mtbs.shared.enums.audit.AuditAction;
import com.mtbs.shared.enums.audit.AuditEntityType;
import com.mtbs.shared.exception.AuthException;
import com.mtbs.shared.exception.TenantException;
import com.mtbs.shared.multitenancy.TenantContext;
import com.mtbs.auth.repository.UserRepository;
import com.mtbs.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Handles the forgot-password / reset-password flow.
 *
 * Token storage strategy:
 *   - Tokens are stored in Redis, NOT in the DB — no schema change needed.
 *   - Key format: "pwd_reset:{tenantId}:{userId}:{token}"
 *   - TTL: 15 minutes (configurable via app.password-reset.token-ttl-minutes)
 *   - The full key embeds tenantId + userId so on reset we can:
 *       1. Validate the token exists in Redis
 *       2. Extract tenantId and userId directly from the key
 *       3. Set TenantContext without a second DB lookup
 *
 * Security properties:
 *   - Silent on unknown email — never reveals whether an account exists
 *   - Token is single-use — deleted from Redis immediately on successful reset
 *   - Existing tokens are purged when a new request is made (prevents token accumulation)
 *
 * TenantContext threading:
 *   - forgotPassword() → public schema only (finds tenant + user email) — no TenantContext needed
 *   - resetPassword()  → must set TenantContext before the @Transactional write in
 *     updateUserPassword() — same rule as all other tenant-schema writes in this project
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final String KEY_PREFIX = "pwd_reset:";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final OutboxEventPublisher outboxEventPublisher;

    @Value("${app.password-reset.token-ttl-minutes}")
    private long tokenTtlMinutes;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // ── Forgot password ───────────────────────────────────────────────────────

    /**
     * Initiates a password reset flow for the given email within a specific tenant.
     *
     * Always returns 200 OK regardless of whether the email exists — prevents
     * email enumeration. The reset link is sent to the email if found.
     */
    public void requestPasswordReset(Long tenantId, String email) {
        log.info("Password reset requested for tenantId={}, email={}", tenantId, email);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> TenantException.notFound(tenantId));

        // Set TenantContext to query the tenant's user table
        TenantContext.setTenantId(tenant.getId());
        TenantContext.setCurrentSchema(tenant.getSchemaName());
        try {
            userRepository.findByEmail(email).ifPresent(user -> {
                // Purge any existing reset tokens for this user before issuing a new one
                purgeExistingTokens(tenantId, user.getId());

                String token = UUID.randomUUID().toString();
                String redisKey = buildKey(tenantId, user.getId(), token);

                redisTemplate.opsForValue().set(
                        redisKey,
                        email,                    // value is the email — sanity check on reset
                        tokenTtlMinutes,
                        TimeUnit.MINUTES
                );

                String resetLink = frontendUrl + "/reset-password"
                        + "?token=" + token
                        + "&tenantId=" + tenantId;

                firePasswordResetEmail(user, tenant, resetLink);

                log.info("Password reset token issued for userId={}, tenantId={}", user.getId(), tenantId);
            });
        } finally {
            TenantContext.clear();
        }

        // Always logs success — never reveals whether email was found
        log.info("Password reset flow completed for tenantId={} (email presence not disclosed)", tenantId);
    }

    // ── Reset password ────────────────────────────────────────────────────────

    /**
     * Validates the reset token and updates the user's password.
     *
     * Token format: "pwd_reset:{tenantId}:{userId}:{token}"
     * We scan Redis for keys matching "pwd_reset:{tenantId}:*:{token}" to find
     * the userId without requiring the client to pass it.
     */
    public void resetPassword(Long tenantId, String token, String newPassword, String ipAddress, String deviceInfo) {
        log.info("Password reset attempt for tenantId={}", tenantId);

        // Scan for the token across all users in this tenant
        String keyPattern = KEY_PREFIX + tenantId + ":*:" + token;
        String matchedKey = redisTemplate.keys(keyPattern)
                .stream()
                .findFirst()
                .orElseThrow(AuthException::resetTokenInvalid);

        // Validate TTL — the key exists but check it's not somehow corrupted
        String storedEmail = redisTemplate.opsForValue().get(matchedKey);
        if (storedEmail == null) {
            throw AuthException.resetTokenExpired();
        }

        // Extract userId from key: "pwd_reset:{tenantId}:{userId}:{token}"
        String[] parts = matchedKey.split(":");
        Long userId = Long.parseLong(parts[2]);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> TenantException.notFound(tenantId));

        // Token is single-use — delete before writing the password
        // (prevents replay even if the password write fails partway)
        redisTemplate.delete(matchedKey);

        // TenantContext must be set before the @Transactional write
        TenantContext.setTenantId(tenant.getId());
        TenantContext.setCurrentSchema(tenant.getSchemaName());
        try {
            updateUserPassword(userId, newPassword, tenant, ipAddress, deviceInfo);
        } finally {
            TenantContext.clear();
        }

        log.info("Password reset successful for userId={}, tenantId={}", userId, tenantId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    @Transactional
    protected void updateUserPassword(Long userId, String newPassword, Tenant tenant, String ipAddress, String deviceInfo) {
        User user = userRepository.findById(userId)
                .orElseThrow(AuthException::invalidCredentials);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        outboxEventPublisher.save(AuthNotificationEvent.builder()
                .eventType(NotificationEvent.PASSWORD_CHANGED)
                .recipientEmail(user.getEmail())
                .recipientName(user.getName())
                .ipAddress(ipAddress)
                .deviceInfo(deviceInfo)
                .tenantName(tenant.getName())
                .eventTime(Instant.now())
                .build(), "User", user.getId());

        outboxEventPublisher.save(AuditLogEvent.builder()
                .action(AuditAction.PASSWORD_CHANGED)
                .entityType(AuditEntityType.USER)
                .entityId(user.getId())
                .entityName(user.getEmail())
                .whoUserId(user.getId())
                .whoUserEmail(user.getEmail())
                .whoUserName(user.getName())
                .contextTenantId(tenant.getId())
                .contextTenantName(tenant.getName())
                .contextIpAddress(ipAddress)
                .contextUserAgent(deviceInfo)
                .description("Password reset via email link")
                .module("AUTH")
                .build(), "User", user.getId());
    }

    private void firePasswordResetEmail(User user, Tenant tenant, String resetLink) {
        try {
            outboxEventPublisher.save(AuthNotificationEvent.builder()
                    .eventType(NotificationEvent.PASSWORD_RESET_REQUESTED)
                    .recipientEmail(user.getEmail())
                    .recipientName(user.getName())
                    .tenantName(tenant.getName())
                    .resetLink(resetLink)
                    .eventTime(Instant.now())
                    .build(), "User", user.getId());
        } catch (Exception e) {
            // Notification failure must never prevent the token from being issued
            log.warn("Failed to send password reset email for userId={}: {}", user.getId(), e.getMessage());
        }
    }

    private void purgeExistingTokens(Long tenantId, Long userId) {
        String existingPattern = KEY_PREFIX + tenantId + ":" + userId + ":*";
        var existingKeys = redisTemplate.keys(existingPattern);
        if (existingKeys != null && !existingKeys.isEmpty()) {
            redisTemplate.delete(existingKeys);
            log.debug("Purged {} existing reset token(s) for userId={}", existingKeys.size(), userId);
        }
    }

    private String buildKey(Long tenantId, Long userId, String token) {
        return KEY_PREFIX + tenantId + ":" + userId + ":" + token;
    }
}