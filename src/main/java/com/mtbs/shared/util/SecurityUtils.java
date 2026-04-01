package com.mtbs.shared.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.mtbs.auth.security.UserPrincipal;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UserPrincipal getCurrentUserPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) authentication.getPrincipal();
        }
        return null;
    }

    public static Long getCurrentUserId() {
        UserPrincipal principal = getCurrentUserPrincipal();
        return principal != null ? principal.getId() : null;
    }

    public static Long getCurrentTenantId() {
        UserPrincipal principal = getCurrentUserPrincipal();
        return principal != null ? principal.getTenantId() : null;
    }

    public static String getCurrentSchemaName() {
        UserPrincipal principal = getCurrentUserPrincipal();
        return principal != null ? principal.getSchemaName() : null;
    }

    public static String getCurrentRole() {
        UserPrincipal principal = getCurrentUserPrincipal();
        return principal != null ? principal.getRole() : null;
    }
}
