package com.mtbs.auth.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private TokenInfo tokens;
    private UserInfo user;
    private TenantInfo tenant;
    private SessionInfo session;
    private FlagsInfo flags;

    @JsonIgnore
    public String getAccessToken() {
        return tokens != null ? tokens.getAccessToken() : null;
    }

    @JsonIgnore
    public String getRefreshToken() {
        return null;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenInfo {
        private String accessToken;
        private String tokenType;
        private long expiresIn;
        private Instant expiresAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long userId;
        private String email;
        private String role;
        private List<String> permissions;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantInfo {
        private Long tenantId;
        private String tenantName;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionInfo {
        private Instant issuedAt;
        private Instant expiresAt;
        private boolean isFirstLogin;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlagsInfo {
        private boolean isTrial;
        private boolean requiresOnboarding;
    }

    public static AuthResponse forTenantUser(
            String accessToken,
            long expiresIn,
            Instant issuedAt,
            Long userId,
            String email,
            String role,
            List<String> permissions,
            Long tenantId,
            String tenantName,
            boolean isFirstLogin,
            boolean isTrial,
            boolean requiresOnboarding) {

        Instant expiresAt = issuedAt.plusSeconds(expiresIn);

        return AuthResponse.builder()
                .tokens(TokenInfo.builder()
                        .accessToken(accessToken)
                        .tokenType("Bearer")
                        .expiresIn(expiresIn)
                        .expiresAt(expiresAt)
                        .build())
                .user(UserInfo.builder()
                        .userId(userId)
                        .email(email)
                        .role(role)
                        .permissions(permissions)
                        .build())
                .tenant(TenantInfo.builder()
                        .tenantId(tenantId)
                        .tenantName(tenantName)
                        .build())
                .session(SessionInfo.builder()
                        .issuedAt(issuedAt)
                        .expiresAt(expiresAt)
                        .isFirstLogin(isFirstLogin)
                        .build())
                .flags(FlagsInfo.builder()
                        .isTrial(isTrial)
                        .requiresOnboarding(requiresOnboarding)
                        .build())
                .build();
    }

    public static AuthResponse forSuperAdmin(
            String accessToken,
            long expiresIn,
            Instant issuedAt,
            Long userId,
            String email,
            boolean isFirstLogin) {

        Instant expiresAt = issuedAt.plusSeconds(expiresIn);

        return AuthResponse.builder()
                .tokens(TokenInfo.builder()
                        .accessToken(accessToken)
                        .tokenType("Bearer")
                        .expiresIn(expiresIn)
                        .expiresAt(expiresAt)
                        .build())
                .user(UserInfo.builder()
                        .userId(userId)
                        .email(email)
                        .role("SUPER_ADMIN")
                        .permissions(null)
                        .build())
                .tenant(null)
                .session(SessionInfo.builder()
                        .issuedAt(issuedAt)
                        .expiresAt(expiresAt)
                        .isFirstLogin(isFirstLogin)
                        .build())
                .flags(null)
                .build();
    }
}
