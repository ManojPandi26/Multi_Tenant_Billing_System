package com.mtbs.tenant.interceptor;

import com.mtbs.shared.util.SecurityUtils;
import com.mtbs.tenant.service.PlanLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlanLimitInterceptor implements HandlerInterceptor {

    private final PlanLimitService planLimitService;
    private final StringRedisTemplate redisTemplate;

    private static final List<String> EXCLUDED_PATHS = List.of(
            "/api/auth",
            "/api/health",
            "/api/webhooks",
            "/swagger-ui",
            "/v3/api-docs",
            "/actuator",
            "/favicon.ico");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) throws Exception {
        String path = request.getRequestURI();
        boolean excluded = EXCLUDED_PATHS.stream()
                .anyMatch(path::startsWith);
        if (excluded) {
            return true;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("SUPER_ADMIN"))) {
            return true;
        }

        Long tenantId = SecurityUtils.getCurrentTenantId();
        if (tenantId == null) {
            return true;
        }

        String yearMonth = YearMonth.now().toString();
        String key = "api:" + tenantId + ":" + yearMonth;
        Long current = 0L;

        try {
            current = redisTemplate.opsForValue().increment(key);
            if (current != null && current == 1L) {
                redisTemplate.expire(key, 32, TimeUnit.DAYS);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for API limit check, allowing request: {}", e.getMessage());
            return true;
        }

        planLimitService.checkApiCallLimit(current != null ? current : 0L);

        return true;
    }
}
