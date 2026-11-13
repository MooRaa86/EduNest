package com.example.gradproj.EduNest.service.integration.stripe;

/**
 * Everything the Stripe gateway needs to create one Checkout Session.
 * Built SERVER-SIDE from the mentorship/enrollment records only (D-28:
 * amounts/currency never from client input).
 *
 * @param transactionPublicId public id of the PENDING PaymentTransaction
 *                            (travelled through Checkout metadata so the
 *                            webhook can resolve it back)
 * @param studentPublicId     joining student's public id (metadata)
 * @param studentEmail        prefill for the Stripe-hosted checkout email field
 * @param mentorshipPublicId  mentorship's public id (metadata)
 * @param mentorshipTitle     displayed on the Stripe-hosted payment page
 * @param amountCents         effective price in cents (price − discount%, USD)
 */
public record StripeCheckoutRequest(
        String transactionPublicId,
        String studentPublicId,
        String studentEmail,
        String mentorshipPublicId,
        String mentorshipTitle,
        long amountCents
) {
}
