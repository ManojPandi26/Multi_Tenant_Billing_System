# Transactional Outbox Pattern - Implementation Guide

## Overview

The **Transactional Outbox Pattern** ensures **at-least-once delivery** of domain events by writing events to an `outbox_events` table atomically in the same transaction as business data. A separate processor polls the outbox and publishes events to Spring's `ApplicationEventPublisher`.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        SERVICE LAYER                                  │
│  BusinessInvoiceService.send()                                        │
│       │                                                              │
│       ▼                                                              │
│  ┌─────────────────┐     ┌──────────────────────────────────────┐    │
│  │   OUTBOX TX    │────▶│ 1. Business data changes (saved)     │    │
│  │                │     │ 2. OutboxEvent INSERT                 │    │
│  └─────────────────┘     └──────────────────────────────────────┘    │
│                                   │                                   │
└───────────────────────────────────┼───────────────────────────────────┘
                                    ▼
                    ┌───────────────────────────────┐
                    │     OUTBOX_EVENTS TABLE       │
                    │  (tenant_schema.outbox_events)│
                    └───────────────────────────────┘
                                    │
                                    ▼
                    ┌───────────────────────────────┐
                    │   OutboxEventProcessor        │
                    │   (Scheduled: every 5 sec)    │
                    │                               │
                    │   1. For each ACTIVE tenant   │
                    │   2. Set TenantContext        │
                    │   3. TransactionTemplate      │
                    │   4. SELECT PENDING events     │
                    │   5. Deserialize + Publish    │
                    │   6. Mark PROCESSED            │
                    └───────────────────────────────┘
                                    │
                                    ▼
                    ┌───────────────────────────────┐
                    │   NotificationService         │
                    │   (@Async @EventListener)     │
                    │   Sends emails                 │
                    └───────────────────────────────┘
```

## Why Outbox Pattern?

### Problem: Fire-and-Forget (Before)
```
Service.method()
  → Business logic (saved)
  → EventPublisher.publish()  ← Can fail silently!
  → Transaction commits

⚠️ If publish() fails → event lost → email never sent
```

### Solution: Outbox Pattern (After)
```
Service.method()
  → Business logic (saved)
  → OutboxEvent INSERT  ← Atomic with business data
  → Transaction commits

✅ Event is PERSISTED even if processor fails
  ↓
OutboxEventProcessor (every 5 sec)
  → Reads PENDING events
  → Publishes to Spring
  → Marks as PROCESSED

⚡ At-least-once delivery guaranteed
```

---

## Files Created

### 1. Core Domain

| File | Path | Description |
|------|------|-------------|
| `OutboxStatus.java` | `shared/event/outbox/` | Enum: PENDING, PROCESSING, PROCESSED, FAILED |
| `OutboxEvent.java` | `shared/event/outbox/` | JPA Entity with JSONB payload, distributed locking |

### 2. Persistence Layer

| File | Path | Description |
|------|------|-------------|
| `OutboxEventRepository.java` | `billing/repository/` | JPA Repository with polling queries and cleanup |
| `V20__create_outbox_events.sql` | `resources/db/migration/tenant/` | Flyway migration for tenant schema |

### 3. Event Publishing

| File | Path | Description |
|------|------|-------------|
| `OutboxEventPublisher.java` | `billing/event/outbox/` | Atomic event persistence (same transaction as business data) |

### 4. Event Processing

| File | Path | Description |
|------|------|-------------|
| `OutboxEventProcessor.java` | `billing/scheduler/outbox/` | Scheduled processor that polls and publishes events |

### 5. Configuration

| File | Path | Description |
|------|------|-------------|
| `AsyncConfig.java` | `app/config/` | Added `TransactionTemplate` bean for programmatic transactions |

---

## Files Modified

### Domain Events (Implemented DomainEvent Interface)

| File | Path | Change |
|------|------|--------|
| `BillingEvent.java` | `shared/event/billing/` | Added `implements DomainEvent` + `getEventType()` method |
| `AuthNotificationEvent.java` | `shared/event/auth/` | Added `implements DomainEvent` + `getEventType()` method |

### Email Configuration

| File | Path | Change |
|------|------|--------|
| `EmailTemplateConfig.java` | `notification/config/` | Added `getTemplate(String)` overload for String-based lookup |

### Billing Services (Replaced Publishers with OutboxEventPublisher)

| File | Path | Change |
|------|------|--------|
| `SubscriptionService.java` | `billing/service/` | `SubscriptionEventPublisher` → `OutboxEventPublisher` |
| `PaymentService.java` | `billing/service/` | `BillingEventPublisher` → `OutboxEventPublisher` |
| `InvoiceService.java` | `billing/service/` | `BillingEventPublisher` → `OutboxEventPublisher` |
| `UsageService.java` | `billing/service/` | `BillingEventPublisher` → `OutboxEventPublisher` |

### Business Services

| File | Path | Change |
|------|------|--------|
| `BusinessInvoiceService.java` | `business/invoice/service/` | `BusinessEventPublisher` → `OutboxEventPublisher` |
| `BusinessPaymentService.java` | `business/payment/service/` | `BusinessEventPublisher` → `OutboxEventPublisher` |

### Auth Services

| File | Path | Change |
|------|------|--------|
| `SignupService.java` | `auth/service/` | `AuthEventPublisher` → `OutboxEventPublisher` |
| `TenantAuthService.java` | `auth/service/` | `AuthEventPublisher` → `OutboxEventPublisher` |
| `UserService.java` | `auth/service/` | `AuthEventPublisher` → `OutboxEventPublisher` |
| `PasswordResetService.java` | `auth/service/` | `AuthEventPublisher` → `OutboxEventPublisher` |

### Scheduler Jobs

| File | Path | Change |
|------|------|--------|
| `TrialEndingSoonJob.java` | `billing/scheduler/job/` | `SubscriptionEventPublisher` → `OutboxEventPublisher` |
| `OnboardingCompletionService.java` | `tenant/service/onboarding/` | `SubscriptionEventPublisher` → `OutboxEventPublisher` |

### Configuration

| File | Path | Change |
|------|------|--------|
| `application.yaml` | `resources/` | Added `app.outbox.*` configuration section |

---

## Database Schema

```sql
CREATE TABLE outbox_events (
    -- Base entity fields
    id                      BIGSERIAL       PRIMARY KEY,
    deleted                 BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT          NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by              BIGINT,
    updated_by              BIGINT,

    -- Event identification
    event_type              VARCHAR(100)     NOT NULL,
    aggregate_type          VARCHAR(100),
    aggregate_id            VARCHAR(100),

    -- Event payload (JSON)
    payload                 TEXT            NOT NULL,
    event_class            VARCHAR(255)    NOT NULL,

    -- Processing state
    status                  VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                                            CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED')),
    retry_count             INTEGER         NOT NULL DEFAULT 0,
    max_retries            INTEGER         NOT NULL DEFAULT 3,
    last_error             TEXT,

    -- Processing timestamps
    processed_at           TIMESTAMPTZ,

    -- Distributed lock
    locked_until            TIMESTAMPTZ
);

-- Indexes
CREATE INDEX idx_outbox_status_locked ON outbox_events (status, locked_until)
    WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX idx_outbox_event_type ON outbox_events (event_type);
CREATE INDEX idx_outbox_created_at ON outbox_events (created_at);
CREATE INDEX idx_outbox_failed ON outbox_events (status) WHERE status = 'FAILED';
CREATE INDEX idx_outbox_processed_at ON outbox_events (processed_at) WHERE status = 'PROCESSED';
```

---

## Configuration

### application.yaml

```yaml
app:
  outbox:
    poll-interval-ms: 5000          # How often processor checks for new events
    batch-size: 100                 # Max events per poll cycle
    lock-duration-seconds: 30      # How long to hold lock during processing
    max-retries: 3                 # Max publish attempts before FAILED
    cleanup-cron: "0 0 3 * * ?"    # Daily at 3 AM UTC - cleanup processed events
```

---

## Multi-Tenant Support

The outbox pattern supports **schema-per-tenant** architecture:

1. **Table Location:** `outbox_events` table exists in **each tenant schema**
2. **Processor Pattern:** Follows same pattern as other scheduler jobs (`SubscriptionExpiryJob`)

### Processor Flow

```java
@Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
public void processOutbox() {
    // 1. Get all active tenants from public schema
    List<Tenant> tenants = tenantRepository.findAllByStatus(Status.ACTIVE);

    // 2. Iterate through each tenant
    for (Tenant tenant : tenants) {
        try {
            // 3. Set tenant context to THIS tenant's schema
            TenantContext.setTenantId(tenant.getId());
            TenantContext.setCurrentSchema(tenant.getSchemaName());

            // 4. Process THIS tenant's outbox
            processOutboxForTenant();

        } finally {
            TenantContext.clear();
        }
    }
}

public void processOutboxForTenant() {
    transactionTemplate.executeWithoutResult(status -> {
        // All DB operations run in a transaction
        List<OutboxEvent> events = outboxRepository.findPendingEvents(...);
        for (OutboxEvent event : events) {
            processEvent(event);
        }
    });
}
```

---

## Transaction Management

### Problem: AOP Self-Invocation

```java
// WRONG - @Transactional doesn't work due to self-invocation
@Scheduled
public void processOutbox() {
    processOutboxForTenant();  // ← Called from same class - proxy bypassed!
}

@Transactional
public void processOutboxForTenant() {  // ← Never runs in transaction!
    // ...
}
```

### Solution: TransactionTemplate

```java
@Scheduled
public void processOutbox() {
    // ...
}

public void processOutboxForTenant() {
    transactionTemplate.executeWithoutResult(status -> {
        // Now runs in a real transaction!
        List<OutboxEvent> events = outboxRepository.findPendingEvents(...);
        // ...
    });
}
```

---

## Event Processing Flow

```
1. BUSINESS ACTION
   └── BusinessInvoiceService.send()
       └── outboxEventPublisher.save(event)

2. ATOMIC PERSIST (same transaction)
   └── outbox_events INSERT (status = PENDING)
   └── Transaction commits

3. POLLING (every 5 seconds)
   └── OutboxEventProcessor.processOutbox()
       └── For each tenant:
           └── TransactionTemplate begins TX
           └── SELECT * FROM outbox_events WHERE status = 'PENDING'
           └── For each event:
               └── markProcessing() → UPDATE status = 'PROCESSING'
               └── deserialize(event) → BillingEvent/AuthNotificationEvent
               └── eventPublisher.publishEvent(event)
               └── markProcessed() → UPDATE status = 'PROCESSED'
           └── TransactionTemplate commits TX

4. NOTIFICATION (@Async listener)
   └── NotificationService.handleBillingEvent(BillingEvent)
       └── Load email template
       └── Build context
       └── Send email via JavaMailSender

5. MONITORING
   └── OutboxEventProcessor.cleanupProcessedEvents() (daily at 3 AM)
       └── DELETE FROM outbox_events WHERE status = 'PROCESSED' AND processed_at < 30 days ago
```

---

## Retry Strategy

### Exponential Backoff

| Retry | Delay |
|-------|-------|
| 1 | 1 minute (60s) |
| 2 | 5 minutes (300s) |
| 3 | 15 minutes (900s) |
| 4+ | FAILED status |

### Failure Handling

```java
private void handleFailure(OutboxEvent outboxEvent, Exception e) {
    outboxEvent.markFailed(e.getMessage());
    outboxRepository.save(outboxEvent);

    if (outboxEvent.getStatus() == OutboxStatus.FAILED) {
        log.error("Outbox event FAILED after {} retries: type={}, error={}",
                outboxEvent.getRetryCount(), outboxEvent.getEventType(), e.getMessage());
        // TODO: Send alert to admin
    } else {
        log.warn("Outbox event retry scheduled: type={}, retry={}/{}, backoff={}s",
                outboxEvent.getEventType(),
                outboxEvent.getRetryCount(),
                outboxEvent.getMaxRetries(),
                getBackoffSeconds(outboxEvent.getRetryCount()));
    }
}
```

---

## Recovery from Crashes

### Stale Processing Recovery

If the processor crashes mid-processing, events may be stuck in `PROCESSING` status:

```java
// Run at start of each poll cycle
int recovered = outboxRepository.resetStaleEvents(now.minus(Duration.ofSeconds(30)));
```

This resets any events that have been in `PROCESSING` status for more than 30 seconds back to `PENDING`.

---

## Cleanup Strategy

Processed events are automatically cleaned up daily:

```sql
-- Runs daily at 3 AM
DELETE FROM outbox_events
WHERE status = 'PROCESSED'
  AND processed_at < NOW() - INTERVAL '30 days'
```

---

## Class Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        OutboxEvent                               │
├─────────────────────────────────────────────────────────────────┤
│ - id: Long                                                       │
│ - status: OutboxStatus                                           │
│ - eventType: String                                              │
│ - aggregateType: String                                          │
│ - aggregateId: String                                            │
│ - payload: String (JSON)                                        │
│ - eventClass: String                                             │
│ - retryCount: Integer                                            │
│ - maxRetries: Integer                                            │
│ - lastError: String                                              │
│ - processedAt: Instant                                           │
│ - lockedUntil: Instant                                           │
├─────────────────────────────────────────────────────────────────┤
│ + markProcessed()                                                │
│ + markProcessing()                                               │
│ + markFailed(error)                                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ extends
                              ▼
                    ┌─────────────────────┐
                    │  AuditableEntity     │
                    │  (Base fields)       │
                    └─────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                   OutboxEventProcessor                           │
├─────────────────────────────────────────────────────────────────┤
│ - outboxRepository: OutboxEventRepository                        │
│ - tenantRepository: TenantRepository                             │
│ - objectMapper: ObjectMapper                                     │
│ - eventPublisher: ApplicationEventPublisher                      │
│ - transactionTemplate: TransactionTemplate                       │
├─────────────────────────────────────────────────────────────────┤
│ + processOutbox() [@Scheduled]                                   │
│ + processOutboxForTenant()                                       │
│ + cleanupProcessedEvents() [@Scheduled]                          │
│ - processEvent(OutboxEvent)                                     │
│ - handleFailure(OutboxEvent, Exception)                         │
│ - deserialize(OutboxEvent): Object                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ uses
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   OutboxEventPublisher                           │
├─────────────────────────────────────────────────────────────────┤
│ - outboxRepository: OutboxEventRepository                        │
│ - objectMapper: ObjectMapper                                     │
├─────────────────────────────────────────────────────────────────┤
│ + save(DomainEvent)                                             │
│ + save(DomainEvent, String aggregateType, Object aggregateId)    │
│ - serialize(Object): String                                     │
└─────────────────────────────────────────────────────────────────┘
```

---

## Testing Checklist

- [ ] Events are saved to `outbox_events` table when business action occurs
- [ ] `OutboxEventProcessor` runs every 5 seconds
- [ ] Events transition: `PENDING` → `PROCESSING` → `PROCESSED`
- [ ] `NotificationService` receives events
- [ ] Emails are sent successfully
- [ ] Multi-tenant isolation works correctly
- [ ] Stale events are recovered after crash
- [ ] Failed events retry with backoff
- [ ] Failed events eventually reach `FAILED` status after max retries
- [ ] Cleanup job removes old processed events

---

## Monitoring Queries

```sql
-- Count events by status
SELECT status, COUNT(*) FROM outbox_events GROUP BY status;

-- Find failed events
SELECT * FROM outbox_events WHERE status = 'FAILED';

-- Find stale processing events
SELECT * FROM outbox_events 
WHERE status = 'PROCESSING' 
  AND locked_until < NOW();

-- Event count by type
SELECT event_type, COUNT(*) FROM outbox_events GROUP BY event_type;
```

---

## Future Enhancements

1. **Dead Letter Queue Alerting** - Send alert when events reach `FAILED` status
2. **Metrics/Actuator Endpoint** - Expose outbox stats via `/actuator/metrics`
3. **UI for Manual Retry** - Admin interface to retry failed events
4. **Event Versioning** - Handle schema evolution of event payloads
