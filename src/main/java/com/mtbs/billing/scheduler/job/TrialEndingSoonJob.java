package com.mtbs.billing.scheduler.job;

import com.mtbs.tenant.entity.Plan;
import com.mtbs.billing.entity.Subscription;
import com.mtbs.tenant.entity.Tenant;
import com.mtbs.shared.enums.billing.SubscriptionStatus;
import com.mtbs.shared.enums.notification.NotificationEvent;
import com.mtbs.shared.event.billing.BillingEvent;
import com.mtbs.billing.event.outbox.OutboxEventPublisher;
import com.mtbs.shared.multitenancy.TenantContext;
import com.mtbs.tenant.repository.PlanRepository;
import com.mtbs.billing.repository.SubscriptionRepository;
import com.mtbs.tenant.repository.TenantRepository;
import com.mtbs.shared.enums.auth.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Runs daily at 08:00 UTC.
 *
 * Finds all TRIALING subscriptions whose trial ends in the next
 * 3-day window and fires a TRIAL_ENDING_SOON notification for each.
 *
 * Window: trialEnd is between now and now + 3 days (exclusive upper bound
 * to avoid double-firing with TrialExpiryJob which handles trialEnd < now).
 *
 * Each tenant is processed in isolation — TenantContext is set and cleared
 * per tenant so schema routing works correctly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrialEndingSoonJob implements Job {

    // How many days ahead to look for expiring trials
    private static final int DAYS_BEFORE_EXPIRY = 3;

    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    @Override
    public void execute(JobExecutionContext context) {
        log.info("TrialEndingSoonJob started at {}", Instant.now());

        List<Tenant> activeTenants = tenantRepository.findAllByStatus(Status.ACTIVE);
        Instant now = Instant.now();
        Instant windowEnd = now.plus(DAYS_BEFORE_EXPIRY, ChronoUnit.DAYS);

        int notified = 0;

        for (Tenant tenant : activeTenants) {
            try {
                TenantContext.setTenantId(tenant.getId());
                TenantContext.setCurrentSchema(tenant.getSchemaName());

                // Find TRIALING subscription whose trial ends within the 3-day window
                subscriptionRepository
                        .findFirstByStatusIn(List.of(SubscriptionStatus.TRIALING))
                        .ifPresent(sub -> {
                            if (isTrialEndingWithinWindow(sub, now, windowEnd)) {
                                fireNotification(tenant, sub);
                            }
                        });

                notified++;
            } catch (Exception e) {
                log.error("TrialEndingSoonJob failed for tenantId={}: {}",
                        tenant.getId(), e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }

        log.info("TrialEndingSoonJob completed — processed {} tenants", notified);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns true if the trial ends AFTER now and BEFORE (or at) windowEnd.
     * Excludes already-expired trials (trialEnd < now) — those are handled
     * by TrialExpiryJob.
     */
    private boolean isTrialEndingWithinWindow(Subscription sub, Instant now, Instant windowEnd) {
        Instant trialEnd = sub.getTrialEnd();
        return trialEnd != null
                && trialEnd.isAfter(now)
                && !trialEnd.isAfter(windowEnd);
    }

    private void fireNotification(Tenant tenant, Subscription sub) {
        try {
            String planDisplayName = planRepository.findById(sub.getPlanId())
                    .map(Plan::getDisplayName)
                    .orElse("your plan");

            long daysLeft = ChronoUnit.DAYS.between(Instant.now(), sub.getTrialEnd());

            outboxEventPublisher.save(BillingEvent.builder()
                    .eventType(NotificationEvent.TRIAL_ENDING_SOON)
                    .tenantId(tenant.getId())
                    .tenantName(tenant.getName())
                    .recipientEmail(tenant.getOwnerEmail())
                    .recipientName(tenant.getName())
                    .planName(planDisplayName)
                    .trialEndsAt(sub.getTrialEnd())
                    .build(), "Subscription", sub.getId());

            log.info("TRIAL_ENDING_SOON fired — tenantId={}, daysLeft={}, trialEnd={}",
                    tenant.getId(), daysLeft, sub.getTrialEnd());

        } catch (Exception e) {
            log.warn("Failed to fire TRIAL_ENDING_SOON for tenantId={}: {}",
                    tenant.getId(), e.getMessage());
        }
    }
}