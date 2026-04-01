package com.mtbs.auth.security;

import com.mtbs.shared.constant.SecurityConstants;
import com.mtbs.shared.multitenancy.TenantContext;
import com.mtbs.shared.util.CookieUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.http.HttpHeaders;
import io.jsonwebtoken.Claims;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CookieUtils cookieUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                Claims claims = jwtTokenProvider.getClaimFromToken(jwt, Function.identity());

                if (jwtTokenProvider.isSuperAdminToken(claims)) {
                    UserPrincipal principal = new UserPrincipal(
                            Long.parseLong(claims.getSubject()),
                            claims.get("email", String.class),
                            null, null, null,
                            SecurityConstants.SUPER_ADMIN_ROLE,
                            List.of(SecurityConstants.SUPER_ADMIN_ROLE));

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    principal, null, principal.getAuthorities());
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // Super admin has no tenant â€” skip TenantContext entirely,
                    // proceed directly to chain, finally block will still clear (no-op)
                    filterChain.doFilter(request, response);
                    return;
                }

                Long userId      = jwtTokenProvider.getUserIdFromToken(jwt);
                Long tenantId    = jwtTokenProvider.getTenantIdFromToken(jwt);
                String schema    = jwtTokenProvider.getSchemaNameFromToken(jwt);
                String role      = jwtTokenProvider.getRoleFromToken(jwt);
                List<String> permissions = jwtTokenProvider.getPermissionsFromToken(jwt);

                // Set BEFORE chain.doFilter() â€” Hibernate reads this at connection
                // acquisition time, which happens inside the downstream @Transactional
                TenantContext.setTenantId(tenantId);
                TenantContext.setCurrentSchema(schema);
                log.debug("TenantContext set as” tenantId={}, schema={}", tenantId, schema);

                UserPrincipal userPrincipal = new UserPrincipal(
                        userId, null, null, tenantId, schema, role, permissions);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userPrincipal, null, userPrincipal.getAuthorities());
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                filterChain.doFilter(request, response); // chain runs HERE with context set

            } else {
                // No token or invalid pass through for Spring Security to reject downstream
                filterChain.doFilter(request, response);
            }

        } finally {
            // ALWAYS runs , after the entire downstream chain completes,
            // including controller + service + repository + response write
            TenantContext.clear();
            log.debug("TenantContext cleared");
        }
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        // 1. Try cookie first
        Optional<String> cookieToken = cookieUtils.extractAccessToken(request);
        if (cookieToken.isPresent()) {
            return cookieToken.get();
        }
        
        // 2. Fallback to Authorization header
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(SecurityConstants.BEARER_PREFIX)) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
