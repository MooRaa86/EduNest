package com.example.gradproj.EduNest.repository.payment;

import com.example.gradproj.EduNest.entity.payment.PaymentStatus;
import com.example.gradproj.EduNest.entity.payment.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * EB-108 / P4-18 (D-28) — Stripe-backed enrollment payment attempts.
 *
 * {@code findByStripeSessionId} is the webhook's join key (unique per
 * uk_payment_tx_stripe_session); {@code findFirstByStripePaymentIntentId}
 * resolves refund/dispute events, which carry the PaymentIntent (filled from
 * the completed event) rather than the session id.
 */
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByStripeSessionId(String stripeSessionId);

    Optional<PaymentTransaction> findFirstByStripePaymentIntentId(String stripePaymentIntentId);

    /** Blocks stacking new checkout sessions while one is still unpaid. */
    boolean existsByStudent_IdAndMentorShip_IdAndStatus(Long studentId, Long mentorshipId, PaymentStatus status);
}
