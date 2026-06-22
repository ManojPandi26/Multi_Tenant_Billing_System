package com.mtbs.billing.controller.webhook;

import com.mtbs.billing.webhook.RazorpayWebhookOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin REST controller for Razorpay webhook events.
 * All processing logic is delegated to {@link RazorpayWebhookOrchestrator}.
 * <p>
 * Security model:
 * <ul>
 *   <li>This endpoint is PUBLIC (no JWT) — Razorpay calls it from their servers.</li>
 *   <li>Authentication is via HMAC-SHA256 signature verification inside the orchestrator.</li>
 *   <li>Always returns 200 OK to prevent Razorpay retries.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/${api.version}/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhooks", description = "Razorpay inbound webhook receiver")
public class RazorpayWebhookController {

    private final RazorpayWebhookOrchestrator webhookOrchestrator;

    @PostMapping("/razorpay")
    @Operation(
            summary = "Razorpay webhook receiver",
            description = "Receives and processes Razorpay webhook events. " +
                    "Authenticated via HMAC-SHA256 signature in the X-Razorpay-Signature header. " +
                    "Always returns 200 OK on signature validation success, regardless of event type."
    )
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String razorpaySignature) {

        webhookOrchestrator.process(payload, razorpaySignature);
        return ResponseEntity.ok("ok");
    }
}
