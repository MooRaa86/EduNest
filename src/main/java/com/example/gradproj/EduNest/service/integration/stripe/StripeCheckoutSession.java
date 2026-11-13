package com.example.gradproj.EduNest.service.integration.stripe;

/**
 * The two things the join flow needs from a created Checkout Session.
 *
 * @param sessionId   Stripe Checkout Session id ({@code cs_...}); the
 *                    PaymentTransaction stores it as its webhook join key
 * @param checkoutUrl Stripe-hosted payment page URL the FE redirects to
 */
public record StripeCheckoutSession(
        String sessionId,
        String checkoutUrl
) {
}
