package com.mtbs.auth.service;

import com.mtbs.auth.dto.auth.AuthResponse;
import com.mtbs.auth.entity.PlatformAdmin;
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
        
        // Generate new refresh token and store in Redis
        String refreshToken = java.util.UUID.randomUUID().toString();
        String redisKey = "super_admin_refresh:" + refreshToken;
        stringRedisTemplate.opsForValue().set(
                redisKey, 
                String.valueOf(admin.getId()), 
                jwtTokenProvider.getRefreshExpiration(), 
                java.util.concurrent.TimeUnit.MILLISECONDS
        );

        log.info("SUPER_ADMIN login successful for: {}", email);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .email(admin.getEmail())
                .role("SUPER_ADMIN")
                .userId(admin.getId())
                .tenantId(null) // no tenant
                .schemaName(null) // no schema
                .build();
    }

    public AuthResponse refreshAdminToken(String rawRefreshToken) {
        log.info("SUPER_ADMIN refresh token attempt.");
        
        String redisKey = "super_admin_refresh:" + rawRefreshToken;
        String adminIdStr = stringRedisTemplate.opsForValue().get(redisKey);
        
        if (adminIdStr == null) {
            throw AuthException.invalidCredentials(); // or a specific token exception
        }
        
        Long adminId = Long.parseLong(adminIdStr);
        PlatformAdmin admin = platformAdminRepository.findById(adminId)
                .orElseThrow(AuthException::invalidCredentials);
                
        if (!admin.isActive()) {
            stringRedisTemplate.delete(redisKey);
            throw AuthException.inactiveUser();
        }
        
        // Revoke old token
        stringRedisTemplate.delete(redisKey);
        
        // Generate new tokens
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
