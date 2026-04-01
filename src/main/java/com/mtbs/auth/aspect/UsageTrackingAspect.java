package com.mtbs.auth.aspect;

import com.mtbs.shared.annotation.TrackUsage;
import com.mtbs.shared.enums.billing.UsageMetric;
import com.mtbs.billing.service.UsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class UsageTrackingAspect {

    private final UsageService usageService;

    @Around("@annotation(trackUsage)")
    public Object trackUsage(ProceedingJoinPoint joinPoint, TrackUsage trackUsage) throws Throwable {
        UsageMetric metric = trackUsage.metric();

        // Before: check limits
        usageService.checkAndEnforceLimits(metric);

        // Execute the method
        Object result = joinPoint.proceed();

        // After success: record usage
        usageService.recordUsage(metric, 1);
        log.debug("Usage tracked for metric: {}", metric);

        return result;
    }
}
