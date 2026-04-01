package com.mtbs.shared.event.auth;

import com.mtbs.shared.enums.notification.NotificationEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthNotificationEvent {

    private NotificationEvent eventType;
    private String recipientEmail;
    private String recipientName;
    private String tenantName;
    private String ipAddress;
    private String deviceInfo;
    private Instant eventTime;

    // For password reset
    private String resetLink;
}