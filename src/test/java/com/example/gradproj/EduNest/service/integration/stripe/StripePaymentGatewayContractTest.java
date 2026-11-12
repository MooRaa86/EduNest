package com.example.gradproj.EduNest.service.integration.stripe;

import com.example.gradproj.EduNest.config.integrations.StripeProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EB-108 / P4-18 (D-28) — Stripe gateway seam contract, NO Spring context and
 * NO network: the real stripe-java HMAC verification is exercised against
 * locally-signed payloads (Stripe's documented scheme:
 * HMAC-SHA256(secret, "{timestamp}.{payload}") in the Stripe-Signature header).
 *
 * Pinned contracts:
 * - Disabled gateway (default): zero external calls, fail-fast naming the
 *   provisioner (mirrors DisabledZoomMeetingClient, EB-85 precedent).
 * - Enabled but unconfigured: fail-fast naming the env vars (C decision: the
 *   money path never silently bypasses).
 * - Signature verification FAILS CLOSED: bad/missing signature, empty payload
 *   -> IllegalArgumentException (the webhook controller answers 400).
 * - Verified events flatten to StripeEventEnvelope with the right join keys.
 */
class StripePaymentGatewayContractTest {

    private static final String SECRET = "whsec_test_signing_secret_0123456789";

    // ---------- disabled gateway (default posture) ----------

    @Test
    @DisplayName("Disabled gateway: session creation fails fast naming the provisioner, no network")
    void disabledGateway_failsFastOnCreate() {
        SdkStripePaymentGateway sdk = new SdkStripePaymentGateway(new StripeProperties());
        // (the disabled bean is exercised through its own class below)
        DisabledStripePaymentGateway disabled = new DisabledStripePaymentGateway(new StripeProperties());

        assertThatThrownBy(() -> disabled.createCheckoutSession(sampleRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.stripe.enabled=false")
                .hasMessageContaining("STRIPE_ENABLED=true");

        assertThatThrownBy(() -> disabled.verifyAndParseWebhook("{}", "t=1,v1=x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Stripe integration is disabled");

        // wiring regression: enabled but the disabled bean is still wired
        StripeProperties enabledProps = new StripeProperties();
        enabledProps.setEnabled(true);
        DisabledStripePaymentGateway miswired = new DisabledStripePaymentGateway(enabledProps);
        assertThatThrownBy(() -> miswired.createCheckoutSession(sampleRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("regression");
    }

    @Test
    @DisplayName("Enabled but unconfigured SDK gateway: both operations fail fast naming the env vars")
    void enabledButUnconfigured_failsFast() {
        StripeProperties props = new StripeProperties();
        props.setEnabled(true);
        // secretKey/webhookSecret/successUrl/cancelUrl all blank
        SdkStripePaymentGateway gateway = new SdkStripePaymentGateway(props);

        assertThatThrownBy(() -> gateway.createCheckoutSession(sampleRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_SECRET_KEY")
                .hasMessageContaining("STRIPE_SUCCESS_URL");

        assertThatThrownBy(() -> gateway.verifyAndParseWebhook("{}", "t=1,v1=x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_WEBHOOK_SECRET");
    }

    // ---------- signature verification (fail closed) ----------

    @Test
    @DisplayName("Bad signature fails CLOSED: IllegalArgumentException, event never parsed")
    void badSignature_failsClosed() {
        StripeProperties props = configuredProps();
        SdkStripePaymentGateway gateway = new SdkStripePaymentGateway(props);

        String payload = completedEventJson("cs_bad", "pi_bad");
        String wrongSecretSignature = sign(payload, "whsec_completely_wrong", Instant.now().getEpochSecond());

        assertThatThrownBy(() -> gateway.verifyAndParseWebhook(payload, wrongSecretSignature))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature verification failed");
    }

    @Test
    @DisplayName("Missing Stripe-Signature header fails CLOSED")
    void missingSignature_failsClosed() {
        SdkStripePaymentGateway gateway = new SdkStripePaymentGateway(configuredProps());

        assertThatThrownBy(() -> gateway.verifyAndParseWebhook(completedEventJson("cs_x", "pi_x"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Empty payload fails CLOSED")
    void emptyPayload_failsClosed() {
        SdkStripePaymentGateway gateway = new SdkStripePaymentGateway(configuredProps());

        assertThatThrownBy(() -> gateway.verifyAndParseWebhook(" ", "t=1,v1=x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload is empty");
    }

    // ---------- verified events flatten correctly ----------

    @Test
    @DisplayName("Verified checkout.session.completed flattens to session + payment intent ids")
    void verifiedCompletedEvent_flattens() throws Exception {
        SdkStripePaymentGateway gateway = new SdkStripePaymentGateway(configuredProps());

        String payload = completedEventJson("cs_test_123", "pi_test_456");
        StripeEventEnvelope envelope = gateway.verifyAndParseWebhook(payload, sign(payload, SECRET, now()));

        assertThat(envelope.eventId()).isEqualTo("evt_test_1");
        assertThat(envelope.type()).isEqualTo("checkout.session.completed");
        assertThat(envelope.sessionId()).isEqualTo("cs_test_123");
        assertThat(envelope.paymentIntentId()).isEqualTo("pi_test_456");
        assertThat(envelope.isCheckoutCompleted()).isTrue();
    }

    @Test
    @DisplayName("Verified charge.refunded flattens to the payment intent join key")
    void verifiedRefundEvent_flattens() throws Exception {
        SdkStripePaymentGateway gateway = new SdkStripePaymentGateway(configuredProps());

        String payload = """
                {"id":"evt_refund_1","object":"event","type":"charge.refunded",
                 "api_version":"%s",
                 "data":{"object":{"id":"ch_1","object":"charge","payment_intent":"pi_test_456"}}}
                """.formatted(com.stripe.Stripe.API_VERSION);
        StripeEventEnvelope envelope = gateway.verifyAndParseWebhook(payload, sign(payload, SECRET, now()));

        assertThat(envelope.isRefunded()).isTrue();
        assertThat(envelope.paymentIntentId()).isEqualTo("pi_test_456");
        assertThat(envelope.sessionId()).isNull();
        assertThat(envelope.reason()).isEqualTo("refunded");
    }

    @Test
    @DisplayName("Verified charge.dispute.created flattens with the dispute reason")
    void verifiedDisputeEvent_flattens() throws Exception {
        SdkStripePaymentGateway gateway = new SdkStripePaymentGateway(configuredProps());

        String payload = """
                {"id":"evt_dispute_1","object":"event","type":"charge.dispute.created",
                 "api_version":"%s",
                 "data":{"object":{"id":"dp_1","object":"dispute","payment_intent":"pi_test_456",
                                   "reason":"fraudulent"}}}
                """.formatted(com.stripe.Stripe.API_VERSION);
        StripeEventEnvelope envelope = gateway.verifyAndParseWebhook(payload, sign(payload, SECRET, now()));

        assertThat(envelope.isDispute()).isTrue();
        assertThat(envelope.paymentIntentId()).isEqualTo("pi_test_456");
        assertThat(envelope.reason()).isEqualTo("fraudulent");
    }

    // ---------- helpers ----------

    private StripeProperties configuredProps() {
        StripeProperties props = new StripeProperties();
        props.setEnabled(true);
        props.setSecretKey("sk_test_placeholder_not_used_for_verification");
        props.setWebhookSecret(SECRET);
        props.setSuccessUrl("https://app.example.org/pay/success");
        props.setCancelUrl("https://app.example.org/pay/cancel");
        return props;
    }

    private static String completedEventJson(String sessionId, String paymentIntentId) {
        return "{\"id\":\"evt_test_1\",\"object\":\"event\",\"type\":\"checkout.session.completed\","
                + "\"api_version\":\"" + com.stripe.Stripe.API_VERSION + "\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\",\"object\":\"checkout_session\","
                + "\"payment_intent\":\"" + paymentIntentId + "\"}}}";
    }

    /** Stripe's documented signature: HMAC-SHA256(secret, "{t}.{payload}") in `t=..,v1=..`. */
    private static String sign(String payload, String secret, long timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(sig.length * 2);
            for (byte b : sig) {
                hex.append(String.format("%02x", b));
            }
            return "t=" + timestamp + ",v1=" + hex;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }

    private static StripeCheckoutRequest sampleRequest() {
        return new StripeCheckoutRequest(
                "tx-1", "stu-1", "s@example.org", "ms-1", "Mentorship title", 18000L);
    }
}
