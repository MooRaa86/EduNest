package com.example.gradproj.EduNest.service.integration.stripe;

/**
 * A verified, signature-checked Stripe webhook event, flattened to the fields
 * the enrollment-payment state machine needs. The SDK types never leak past
 * the gateway seam — payment services program against this record (and the
 * {@link StripePaymentGateway} interface), keeping the webhook flow testable
 * with fakes.
 *
 * @param eventId         Stripe event id ({@code evt_...})
 * @param type            Stripe event type, e.g. checkout.session.completed
 * @param sessionId       Checkout Session id when the event carries one
 * @param paymentIntentId PaymentIntent id when the event carries one
 * @param reason          short reason for refund/dispute events, else null
 */
public record StripeEventEnvelope(
        String eventId,
        String type,
        String sessionId,
        String paymentIntentId,
        String reason
) {
    /** Event type: payment confirmed — the ONLY trigger for PENDING → PAID. */
    public boolean isCheckoutCompleted() {
        return "checkout.session.completed".equals(type);
    }

    /** Event type: unpaid checkout session expired. */
    public boolean isCheckoutExpired() {
        return "checkout.session.expired".equals(type);
    }

    /** Event type: payment returned to the student (F decision: record-only). */
    public boolean isRefunded() {
        return "charge.refunded".equals(type);
    }

    /** Event type: card dispute opened (F decision: record-only). */
    public boolean isDispute() {
        return "charge.dispute.created".equals(type);
    }
}
