package com.mtbs.notification.service;

import com.mtbs.notification.config.EmailTemplateConfig;
import com.mtbs.shared.event.auth.AuthNotificationEvent;
import com.mtbs.shared.event.billing.BillingEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final EmailTemplateConfig templateConfig;

    @Value("${app.mail.from-address}")
    private String fromAddress;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a z")
                    .withZone(ZoneId.of("UTC"));

    // ─── Billing Events ────────────────────────────────────────────────────

    @Async
    @EventListener
    public void handleBillingEvent(BillingEvent event) {
        log.info("Handling billing notification: type={}, tenant={}", event.getEventType(), event.getTenantId());
        try {
            EmailTemplateConfig.TemplateDefinition def = templateConfig.getTemplate(event.getEventType());
            String subject = resolveSubject(def.getSubject(), event);
            Context ctx = buildBillingContext(event);
            String htmlContent = renderTemplate("emails/" + def.getTemplateName(), ctx);

            // Extract PDF attachment from extra map if present
            byte[] pdfBytes = null;
            if (event.getExtra() != null && event.getExtra().get("pdfAttachment") instanceof byte[]) {
                pdfBytes = (byte[]) event.getExtra().get("pdfAttachment");
            }

            // Extract filename from invoiceNumber in extra map or direct field
            String invoiceNumber = event.getExtra() != null && event.getExtra().get("invoiceNumber") != null
                    ? String.valueOf(event.getExtra().get("invoiceNumber"))
                    : event.getInvoiceNumber();
            String pdfFileName = invoiceNumber != null ? invoiceNumber + ".pdf" : "invoice.pdf";

            sendEmail(event.getRecipientEmail(), event.getRecipientName(), subject,
                    htmlContent, pdfBytes, pdfFileName);

        } catch (Exception ex) {
            log.error("Failed to send billing notification: type={}, recipient={}, error={}",
                    event.getEventType(), event.getRecipientEmail(), ex.getMessage(), ex);
        }
    }

    // ─── Auth Events ───────────────────────────────────────────────────────

    @Async
    @EventListener
    public void handleAuthEvent(AuthNotificationEvent event) {
        log.info("Handling auth notification: type={}, recipient={}", event.getEventType(), event.getRecipientEmail());
        try {
            EmailTemplateConfig.TemplateDefinition def = templateConfig.getTemplate(event.getEventType());
            Context ctx = buildAuthContext(event);
            String htmlContent = renderTemplate("emails/" + def.getTemplateName(), ctx);
            sendEmail(event.getRecipientEmail(), event.getRecipientName(),
                    def.getSubject(), htmlContent, null, null);
        } catch (Exception ex) {
            log.error("Failed to send auth notification: type={}, recipient={}, error={}",
                    event.getEventType(), event.getRecipientEmail(), ex.getMessage(), ex);
        }
    }

    // ─── Core Send ─────────────────────────────────────────────────────────

    /**
     * Sends an HTML email with optional PDF attachment.
     *
     * @param pdfBytes  PDF byte array — null means no attachment
     * @param pdfFileName  filename for the attachment (e.g. "BINV-1-202603-0001.pdf")
     */
    private void sendEmail(String toEmail, String toName, String subject,
                           String htmlContent, byte[] pdfBytes, String pdfFileName)
            throws MessagingException, UnsupportedEncodingException {

        MimeMessage message = mailSender.createMimeMessage();
        // multipart=true required for both HTML body and attachments
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress, fromName);
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);

        // Attach PDF if present
        if (pdfBytes != null && pdfBytes.length > 0) {
            helper.addAttachment(pdfFileName,
                    new ByteArrayResource(pdfBytes),
                    "application/pdf");
            log.debug("PDF attached: filename={}, bytes={}", pdfFileName, pdfBytes.length);
        }

        mailSender.send(message);
        log.info("Email sent: to={}, subject={}", toEmail, subject);
    }

    // ─── Template rendering ────────────────────────────────────────────────

    private String renderTemplate(String templatePath, Context ctx) {
        // Add globals available in every template
        ctx.setVariable("frontendUrl", frontendUrl);
        ctx.setVariable("currentYear", java.time.Year.now().getValue());
        ctx.setVariable("companyName", fromName);
        return templateEngine.process(templatePath, ctx);
    }

    // ─── Context Builders ──────────────────────────────────────────────────

    private Context buildBillingContext(BillingEvent event) {
        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("recipientName", event.getRecipientName());
        ctx.setVariable("tenantName",    event.getTenantName());

        // Plan / subscription
        ctx.setVariable("planName",           event.getPlanName());
        ctx.setVariable("oldPlanName",        event.getOldPlanName());
        ctx.setVariable("planPrice",          event.getPlanPrice());
        ctx.setVariable("billingCycle",       event.getBillingCycle());
        ctx.setVariable("trialEndsAt",        formatInstant(event.getTrialEndsAt()));
        ctx.setVariable("subscriptionEndsAt", formatInstant(event.getSubscriptionEndsAt()));
        ctx.setVariable("nextBillingDate",    formatInstant(event.getNextBillingDate()));

        // Invoice (direct fields — used by platform billing events)
        ctx.setVariable("invoiceNumber",  event.getInvoiceNumber());
        ctx.setVariable("invoiceAmount",  event.getInvoiceAmount());
        ctx.setVariable("invoiceDueDate", formatInstant(event.getInvoiceDueDate()));

        // Currency — fall back to "INR" so it is NEVER null in templates
        String currency = event.getCurrency() != null ? event.getCurrency() : "INR";
        ctx.setVariable("currency", currency);

        // Payment
        ctx.setVariable("paymentId",     event.getPaymentId());
        ctx.setVariable("paymentAmount", event.getPaymentAmount());
        ctx.setVariable("paymentMethod", event.getPaymentMethod());
        ctx.setVariable("retryAttempt",  event.getRetryAttempt());
        ctx.setVariable("maxRetries",    event.getMaxRetries());

        // Usage
        ctx.setVariable("metricName",   event.getMetricName());
        ctx.setVariable("currentUsage", event.getCurrentUsage());
        ctx.setVariable("usageLimit",   event.getUsageLimit());
        ctx.setVariable("usagePercent", event.getUsagePercent());

        // Spread extra map last — allows business billing events to override
        // direct fields with richer values (invoiceTotal, dueDate, pdfAttachment, etc.)
        // pdfAttachment is intentionally included in context spread; templates
        // will never render byte[] as text (no ${pdfAttachment} in any template).
        if (event.getExtra() != null) {
            event.getExtra().forEach((k, v) -> {
                // Skip the PDF bytes — no template variable needs it
                if (!"pdfAttachment".equals(k)) {
                    ctx.setVariable(k, v);
                }
                // Override currency from extra if explicitly set (e.g. "USD")
                if ("currency".equals(k) && v != null) {
                    ctx.setVariable("currency", v.toString());
                }
            });
        }

        return ctx;
    }

    private Context buildAuthContext(AuthNotificationEvent event) {
        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("recipientName", event.getRecipientName());
        ctx.setVariable("tenantName",    event.getTenantName());
        ctx.setVariable("ipAddress",     event.getIpAddress());
        ctx.setVariable("deviceInfo",    event.getDeviceInfo());
        ctx.setVariable("eventTime",     formatInstant(event.getEventTime()));
        ctx.setVariable("resetLink",     event.getResetLink());
        return ctx;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private String formatInstant(java.time.Instant instant) {
        if (instant == null) return null;
        return DATE_FORMATTER.format(instant);
    }

    private String resolveSubject(String subjectTemplate, BillingEvent event) {
        // Pull invoiceNumber from extra first (business billing), then direct field
        String invoiceNumber = event.getExtra() != null && event.getExtra().get("invoiceNumber") != null
                ? String.valueOf(event.getExtra().get("invoiceNumber"))
                : (event.getInvoiceNumber() != null ? event.getInvoiceNumber() : "");

        return subjectTemplate
                .replace("{{invoiceNumber}}", invoiceNumber)
                .replace("{{retryAttempt}}", event.getRetryAttempt() != null
                        ? event.getRetryAttempt().toString() : "")
                .replace("{{usagePercent}}", event.getUsagePercent() != null
                        ? event.getUsagePercent().toString() : "")
                .replace("{{metricName}}", event.getMetricName() != null
                        ? event.getMetricName() : "")
                .replace("{{tenantName}}", event.getTenantName() != null
                        ? event.getTenantName() : "");
    }
}