package com.mtbs.shared.event.billing;

import com.mtbs.shared.enums.notification.NotificationEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingEvent {

    // Core routing
    private NotificationEvent eventType;
    private Long tenantId;
    private String tenantName;
    private String recipientEmail;
    private String recipientName;

    // Auth context (login, register, password)
    private String ipAddress;
    private String deviceInfo;
    private Instant eventTime;

    // Plan / subscription context
    private String planName;
    private String oldPlanName;
    private BigDecimal planPrice;
    private String billingCycle;
    private Instant trialEndsAt;
    private Instant subscriptionEndsAt;
    private Instant nextBillingDate;

    // Invoice context
    private String invoiceNumber;
    private BigDecimal invoiceAmount;
    private String currency;
    private Instant invoiceDueDate;

    // Payment context
    private String paymentId;
    private BigDecimal paymentAmount;
    private String paymentMethod;
    private Integer retryAttempt;
    private Integer maxRetries;

    // Usage context
    private String metricName;
    private Long currentUsage;
    private Long usageLimit;
    private Integer usagePercent;

    // Extra dynamic data (fallback for edge cases)
    private Map<String, Object> extra;
}