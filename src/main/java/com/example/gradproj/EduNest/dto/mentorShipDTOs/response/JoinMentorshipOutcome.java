package com.example.gradproj.EduNest.dto.mentorShipDTOs.response;

/**
 * EB-108 / P4-18 (D-28) — the two possible outcomes of a student join attempt.
 *
 * {@code enrolledDirectly=true}: free-tier bypass (amount == 0) or
 * STRIPE_ENABLED=false — the enrollment was created immediately (legacy
 * behavior) and the FE shows the success state.
 *
 * {@code enrolledDirectly=false}: a Stripe Checkout Session was created and
 * {@code checkoutUrl} is the Stripe-hosted payment page the FE must redirect
 * to. The enrollment does NOT exist yet — only the webhook flipping the
 * transaction to PAID creates it. The FE must not treat this response as a
 * successful join.
 *
 * The checkout URL is the only payment-related field ever handed to the FE;
 * no amount, currency, or client-side payment parameters are exposed (D-28).
 */
public record JoinMentorshipOutcome(
        boolean enrolledDirectly,
        String checkoutUrl
) {
    public static JoinMentorshipOutcome directEnrollment() {
        return new JoinMentorshipOutcome(true, null);
    }

    public static JoinMentorshipOutcome checkoutRequired(String checkoutUrl) {
        return new JoinMentorshipOutcome(false, checkoutUrl);
    }
}
