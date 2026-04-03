package com.mtbs.auth.controller.auth;

import com.mtbs.shared.util.CookieUtils;
import com.mtbs.shared.util.SecurityUtils;
import com.mtbs.auth.dto.auth.AuthResponse;
import com.mtbs.auth.dto.auth.SuperAdminLoginRequest;
import com.mtbs.shared.dto.common.ApiResponse;
import com.mtbs.shared.exception.AuthException;
import com.mtbs.auth.security.UserPrincipal;
import com.mtbs.auth.service.SuperAdminAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
@Tag(name = "Super Admin Auth", description = "Platform administrator authentication")
public class AdminAuthController {

    private final SuperAdminAuthService superAdminAuthService;
    private final CookieUtils cookieUtils;

    @PostMapping("/login")
    @Operation(summary = "Platform admin login â€” issues SUPER_ADMIN JWT with no tenantId")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody SuperAdminLoginRequest request,
            HttpServletResponse response) {
        AuthResponse result = superAdminAuthService.login(
                request.getEmail(), request.getPassword());
        
        cookieUtils.addAdminAuthCookies(response, result.getAccessToken(), result.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success((result), "Platform admin login successful"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Platform admin refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshAdminToken(
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        
        String cookieToken = cookieUtils.extractRefreshToken(httpRequest).orElse(null);
        if (!org.springframework.util.StringUtils.hasText(cookieToken)) {
            // we could fall back to body, but admin refresh endpoint is entirely cookie driven per spec for admin refresh
            throw AuthException.invalidCredentials();
        }

        AuthResponse result = superAdminAuthService.refreshAdminToken(cookieToken);
        cookieUtils.addAdminAuthCookies(response, result.getAccessToken(), result.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success((result), "Admin token refreshed successfully"));
    }

    @PostMapping("/logout")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @Operation(summary = "Platform admin logout")
    public ResponseEntity<ApiResponse<Void>> logout(jakarta.servlet.http.HttpServletResponse response) {
        cookieUtils.clearAuthCookies(response);
        return ResponseEntity.ok(ApiResponse.success(null, "Admin logged out successfully"));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @Operation(summary = "Get platform admin profile from JWT context")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProfile() {
        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal();
        Map<String, Object> profile = Map.of(
                "userId", principal.getId(),
                "email", principal.getEmail(),
                "role", principal.getRole());
        return ResponseEntity.ok(ApiResponse.success(profile, "Profile retrieved"));
    }
}
