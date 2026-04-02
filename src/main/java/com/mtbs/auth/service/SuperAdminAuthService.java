package com.mtbs.auth.service;

import com.mtbs.admin.repository.AuditLogRepository;
import com.mtbs.auth.dto.auth.AuthResponse;
import com.mtbs.auth.entity.PlatformAdmin;
import com.mtbs.shared.entity.audit.AuditLog;
import com.mtbs.shared.enums.audit.AuditAction;
import com.mtbs.shared.enums.audit.AuditEntityType;
import com.mtbs.shared.exception.AuthException;
import com.mtbs.auth.repository.PlatformAdminRepository;
import com.mtbs.auth.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminAuthService {

    private final PlatformAdminRepository platformAdminRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    private final AuditLogRepository auditLogRepository;

    public AuthResponse login(String email, String password) {
        log.info("SUPER_ADMIN login attempt for: {}", email);

        PlatformAdmin admin = platformAdminRepository.findByEmail(email)
                .orElseThrow(AuthException::invalidCredentials);

        if (!admin.isActive()) {
            throw AuthException.inactiveUser();
        }

        if (!passwordEncoder.matches(password, admin.getPassword())) {
            throw AuthException.invalidCredentials();
        }

        String accessToken = jwtTokenProvider.generateSuperAdminToken(admin);
        
        String refreshToken = java.util.UUID.randomUUID().toString();
        String redisKey = "super_admin_refresh:" + refreshToken;
        stringRedisTemplate.opsForValue().set(
                redisKey, 
                String.valueOf(admin.getId()), 
                jwtTokenProvider.getRefreshExpiration(), 
                java.util.concurrent.TimeUnit.MILLISECONDS
        );

        auditLogRepository.save(AuditLog.builder()
                .whatAction(AuditAction.LOGIN)
                .whereEntityType(AuditEntityType.USER)
                .whereEntityId(admin.getId())
                .whereEntityName(admin.getEmail())
                .whoUserId(admin.getId())
                .whoUserEmail(admin.getEmail())
                .whoUserName(admin.getName())
                .whoRole("SUPER_ADMIN")
                .contextTenantName("PLATFORM")
                .description("Super admin logged in")
                .module("ADMIN_AUTH")
                .severity("INFO")
                .build());

        log.info("SUPER_ADMIN login successful for: {}", email);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .email(admin.getEmail())
                .role("SUPER_ADMIN")
                .userId(admin.getId())
                .tenantId(null)
                .schemaName(null)
                .build();
    }

    public AuthResponse refreshAdminToken(String rawRefreshToken) {
        log.info("SUPER_ADMIN refresh token attempt.");
        
        String redisKey = "super_admin_refresh:" + rawRefreshToken;
        String adminIdStr = stringRedisTemplate.opsForValue().get(redisKey);
        
        if (adminIdStr == null) {
            throw AuthException.invalidCredentials();
        }
        
        Long adminId = Long.parseLong(adminIdStr);
        PlatformAdmin admin = platformAdminRepository.findById(adminId)
                .orElseThrow(AuthException::invalidCredentials);
                
        if (!admin.isActive()) {
            stringRedisTemplate.delete(redisKey);
            throw AuthException.inactiveUser();
        }
        
        stringRedisTemplate.delete(redisKey);
        
        String newAccessToken = jwtTokenProvider.generateSuperAdminToken(admin);
        String newRefreshToken = java.util.UUID.randomUUID().toString();
        String newRedisKey = "super_admin_refresh:" + newRefreshToken;
        
        stringRedisTemplate.opsForValue().set(
                newRedisKey, 
                String.valueOf(admin.getId()), 
                jwtTokenProvider.getRefreshExpiration(), 
                java.util.concurrent.TimeUnit.MILLISECONDS
        );
        
        log.info("SUPER_ADMIN refresh successful for: {}", admin.getEmail());
        
        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .email(admin.getEmail())
                .role("SUPER_ADMIN")
                .userId(admin.getId())
                .tenantId(null)
                .schemaName(null)
                .build();
    }
}
