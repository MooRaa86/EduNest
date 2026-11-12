package com.example.gradproj.EduNest.controller.payment;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.service.integration.stripe.StripeEventEnvelope;
import com.example.gradproj.EduNest.service.integration.stripe.StripePaymentGateway;
import com.example.gradproj.EduNest.service.payment.PaymentWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * EB-108 / P4-18 (D-28) — Stripe webhook ingestion.
 *
 * Security posture: this is a PUBLIC route (PublicRoutesPolicy) because Stripe
 * cannot authenticate — but it is NOT anonymous access to application state:
 * every request must carry a valid Stripe-Signature header over the RAW body
 * (HMAC per Stripe's scheme), verified inside the gateway with
 * fail-closed semantics. Missing/invalid signature or an unsafe payload →
 * 400 and the payment state machine is never touched. CSRF is disabled in the
 * security chains (stateless token auth) and this endpoint has no cookies.
 *
 * The RAW body is mandatory: @RequestBody String preserves the exact bytes
 * Stripe signed. Never re-serialize before verification.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
@Tag(
        name = "Payments webhook",
        description = "Stripe webhook ingestion for enrollment payments (signature-verified, public route)"
)
public class PaymentWebhookController {

    private final StripePaymentGateway stripePaymentGateway;
    private final PaymentWebhookService paymentWebhookService;

    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Stripe webhook endpoint (public, signature-verified)")
    public ResponseEntity<SimpleResponse> handleStripeWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature) {

        try {
            // Fail-closed verification: bad/missing signature, unparsable
            // payload, or missing secret throws -> 400, state untouched.
            StripeEventEnvelope event =
                    stripePaymentGateway.verifyAndParseWebhook(rawPayload, stripeSignature);

            paymentWebhookService.applyVerifiedEvent(event);

            SimpleResponse response = new SimpleResponse();
            response.addMessage("received", true);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            // Stripe retries on non-2xx; a 4xx tells it this delivery will
            // never be acceptable (forged/tampered/misconfigured).
            SimpleResponse response = new SimpleResponse();
            response.addMessage("error", "Stripe webhook rejected: signature or payload verification failed");
            return ResponseEntity.badRequest().body(response);
        }
    }
}
