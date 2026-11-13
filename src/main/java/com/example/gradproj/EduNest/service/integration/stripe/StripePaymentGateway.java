package com.example.gradproj.EduNest.service.integration.stripe;

/**
 * The Stripe seam — the only two operations the enrollment-payment flow needs.
 * Implementations: {@link SdkStripePaymentGateway} (real, active when
 * {@code app.stripe.enabled=true}) and {@link DisabledStripePaymentGateway}
 * (default when disabled — fails fast at call time, zero external calls).
 */
public interface StripePaymentGateway {

    /**
     * Create a Checkout Session for one enrollment payment. Amount and currency
     * come from the request record built server-side; the FE only ever receives
     * {@link StripeCheckoutSession#checkoutUrl()}.
     */
    StripeCheckoutSession createCheckoutSession(StripeCheckoutRequest request);

    /**
     * Verify the Stripe-Signature header against the RAW request body using the
     * configured webhook secret (com.stripe.net.Webhook.constructEvent) and
     * flatten the event to {@link StripeEventEnvelope}.
     *
     * MUST fail closed: any signature mismatch, missing header, unparsable
     * payload, or missing secret throws — the caller answers 400 and the state
     * machine is untouched.
     */
    StripeEventEnvelope verifyAndParseWebhook(String rawPayload, String signatureHeader);
}
