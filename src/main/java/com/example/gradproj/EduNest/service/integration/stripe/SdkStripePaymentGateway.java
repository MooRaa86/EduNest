package com.example.gradproj.EduNest.service.integration.stripe;

import com.example.gradproj.EduNest.config.integrations.StripeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Real Stripe gateway — the {@link StripePaymentGateway} bean while
 * {@code app.stripe.enabled=true} (test keys first per D-28).
 *
 * Uses the official {@code stripe-java} SDK (Omar's D decision, 2026-11-12:
 * for financial cryptography the official SDK is non-negotiable):
 * <ul>
 *   <li>Sessions: {@link StripeClient} (one-time {@code Mode.PAYMENT} —
 *       D-28 scopes this cycle to one-time enrollment payments only;
 *       subscriptions are deferred).</li>
 *   <li>Webhooks: {@link Webhook#constructEvent} HMAC verification —
 *       FAIL CLOSED: a bad/missing signature, unparsable payload, or missing
 *       secret throws and the payment state machine is never touched.</li>
 * </ul>
 * Amounts/currency are taken ONLY from the request record, which the join flow
 * builds from server-side enrollment records (D-28). Metadata carries the
 * server-side ids so no webhook ever needs to trust request data.
 */
@Component
@ConditionalOnProperty(prefix = "app.stripe", name = "enabled", havingValue = "true")
public class SdkStripePaymentGateway implements StripePaymentGateway {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final StripeProperties props;

    @Autowired
    public SdkStripePaymentGateway(StripeProperties props) {
        this.props = props;
    }

    @Override
    public StripeCheckoutSession createCheckoutSession(StripeCheckoutRequest request) {
        guard("checkout session create");

        // Fresh StripeClient per call keeps bring-up safe when the key arrives
        // via env refresh; creation itself is a single stateless API call.
        StripeClient client = new StripeClient(props.getSecretKey());

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(props.getSuccessUrl())
                .setCancelUrl(props.getCancelUrl())
                .setCustomerEmail(request.studentEmail())
                .setClientReferenceId(request.transactionPublicId())
                .putMetadata("transaction_public_id", request.transactionPublicId())
                .putMetadata("mentorship_public_id", request.mentorshipPublicId())
                .putMetadata("student_public_id", request.studentPublicId())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                // E decision: USD (currency lowercase per ISO-4217 Stripe format)
                                .setCurrency(props.getCurrency().toLowerCase())
                                .setUnitAmount(request.amountCents())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(request.mentorshipTitle())
                                        .build())
                                .build())
                        .build())
                .build();

        try {
            Session session = client.checkout().sessions().create(params);
            return new StripeCheckoutSession(session.getId(), session.getUrl());
        } catch (StripeException e) {
            // Money path must fail loudly — never fall back to a free join.
            throw new IllegalStateException("Stripe Checkout Session creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public StripeEventEnvelope verifyAndParseWebhook(String rawPayload, String signatureHeader) {
        if (rawPayload == null || rawPayload.isBlank()) {
            throw new IllegalArgumentException("Stripe webhook payload is empty");
        }
        // Fail closed BEFORE the SDK: a missing/blank signature header must not
        // turn into a raw NPE (observed: Webhook$Signature.getTimestamp NPEs) —
        // it is a rejected delivery, not a 500 Stripe would retry forever.
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new IllegalArgumentException("Stripe-Signature header is missing");
        }
        guard("webhook verification");

        final Event event;
        try {
            // HMAC signature verification with the configured whsec_ secret.
            // Throws on ANY mismatch — fail closed.
            event = Webhook.constructEvent(rawPayload, signatureHeader, props.getWebhookSecret());
        } catch (com.stripe.exception.SignatureVerificationException e) {
            throw new IllegalArgumentException("Stripe webhook signature verification failed", e);
        }

        // Flatten from the RAW data object JSON (SDK 34.x / basil): the typed
        // model path is unreliable across payload-shape drift (verified live in
        // the contract test: getObject() yields StripeRawJsonObject wrappers),
        // while getRawJson() always exposes the signature-verified data object.
        // Fail closed: missing/unparsable raw JSON -> rejected delivery (400),
        // never a 500 Stripe would retry forever.
        String rawJson;
        try {
            rawJson = event.getDataObjectDeserializer().getRawJson();
        } catch (Exception e) {
            throw new IllegalArgumentException("Stripe webhook payload could not be safely deserialized", e);
        }
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalArgumentException("Stripe webhook payload could not be safely deserialized");
        }

        final JsonNode dataObject;
        try {
            dataObject = JSON.readTree(rawJson);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Stripe webhook payload could not be safely deserialized", e);
        }

        String type = event.getType();
        String sessionId = null;
        String paymentIntentId = null;
        String reason = null;

        if (type != null && type.startsWith("checkout.session.")) {
            sessionId = textOrNull(dataObject, "id");
            paymentIntentId = textOrNull(dataObject, "payment_intent");
        } else if ("charge.refunded".equals(type)
                || (type != null && type.startsWith("charge.dispute."))) {
            // refund/dispute events carry the PaymentIntent as their join key
            // (the transaction stores it from the completed event)
            paymentIntentId = textOrNull(dataObject, "payment_intent");
            reason = "charge.dispute.created".equals(type)
                    ? truncate(textOrNull(dataObject, "reason"))
                    : "refunded";
        }

        return new StripeEventEnvelope(event.getId(), type, sessionId, paymentIntentId, reason);
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private void guard(String operation) {
        if (!props.isEnabled()) {
            throw new IllegalStateException(
                    "Stripe integration is disabled (app.stripe.enabled=false). The legacy "
                            + "direct-join path applies; the payment gateway must not be called. "
                            + "(attempted operation: stripe " + operation + ")");
        }
        if (!props.isConfigured()) {
            throw new IllegalStateException(
                    "Stripe integration is ENABLED but its configuration is incomplete — set "
                            + "STRIPE_SECRET_KEY / STRIPE_WEBHOOK_SECRET / STRIPE_SUCCESS_URL / "
                            + "STRIPE_CANCEL_URL in the env store before any Stripe call. "
                            + "(attempted operation: stripe " + operation + ")");
        }
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= 255) {
            return value;
        }
        return value.substring(0, 255);
    }
}
