package com.example.gradproj.EduNest.service.payment;

import com.example.gradproj.EduNest.entity.payment.PaymentStatus;
import com.example.gradproj.EduNest.entity.payment.PaymentTransaction;
import com.example.gradproj.EduNest.repository.payment.PaymentTransactionRepository;
import com.example.gradproj.EduNest.service.integration.stripe.StripeEventEnvelope;
import com.example.gradproj.EduNest.service.mentorShip.mentorShipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * EB-108 / P4-18 (D-28) — the enrollment-payment state machine.
 *
 * Consumes ONLY signature-verified events ({@link StripeEventEnvelope} — the
 * gateway has already failed closed on any signature/parse problem) and mutates
 * {@link PaymentTransaction} state:
 *
 * <ul>
 *   <li>checkout.session.completed → PENDING → PAID (exactly once), then the
 *       Enrollment is finalized. Replay/second-completed events are no-ops —
 *       the PAID transaction is terminal for the money path.</li>
 *   <li>checkout.session.expired → PENDING → EXPIRED.</li>
 *   <li>charge.refunded → PAID → REFUNDED (F decision: RECORD-ONLY — the
 *       enrollment access stays; revocation is deferred to a future sprint).</li>
 *   <li>charge.dispute.created → PAID → DISPUTED (record-only).</li>
 *   <li>anything else → ignored (200 to Stripe).</li>
 * </ul>
 *
 * Unknown ids (no matching transaction) are logged and answered 200: Stripe
 * retries permanent 4xx forever, and an event for a transaction that never
 * existed in this system must not wedge the webhook queue. The webhook is the
 * SOLE source of truth for PAID — nothing here trusts the FE or the redirect
 * landing page.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookService {

    private final PaymentTransactionRepository transactionRepository;
    private final mentorShipService mentorShipService;

    @Transactional
    public void applyVerifiedEvent(StripeEventEnvelope event) {

        if (event.isCheckoutCompleted()) {
            if (event.sessionId() == null) {
                log.warn("Stripe webhook: completed event {} carries no session id - ignored", event.eventId());
                return;
            }
            transactionRepository.findByStripeSessionId(event.sessionId())
                    .ifPresentOrElse(
                            tx -> markPaid(tx, event),
                            () -> log.warn("Stripe webhook: completed event {} for unknown session {} - ignored",
                                    event.eventId(), event.sessionId()));

        } else if (event.isCheckoutExpired()) {
            if (event.sessionId() == null) {
                return;
            }
            transactionRepository.findByStripeSessionId(event.sessionId())
                    .ifPresentOrElse(
                            this::markExpired,
                            () -> log.warn("Stripe webhook: expired event {} for unknown session {} - ignored",
                                    event.eventId(), event.sessionId()));

        } else if (event.isRefunded()) {
            if (event.paymentIntentId() == null) {
                return;
            }
            transactionRepository.findFirstByStripePaymentIntentId(event.paymentIntentId())
                    .ifPresentOrElse(
                            tx -> markRefunded(tx, event),
                            () -> log.warn("Stripe webhook: refund event {} for unknown payment intent {} - ignored",
                                    event.eventId(), event.paymentIntentId()));

        } else if (event.isDispute()) {
            if (event.paymentIntentId() == null) {
                return;
            }
            transactionRepository.findFirstByStripePaymentIntentId(event.paymentIntentId())
                    .ifPresentOrElse(
                            tx -> markDisputed(tx, event),
                            () -> log.warn("Stripe webhook: dispute event {} for unknown payment intent {} - ignored",
                                    event.eventId(), event.paymentIntentId()));

        } else {
            log.debug("Stripe webhook: unhandled event type {} - ignored", event.type());
        }
    }

    private void markPaid(PaymentTransaction tx, StripeEventEnvelope event) {
        if (tx.getStatus() != PaymentStatus.PENDING) {
            // Idempotent replay (or already terminal): PAID is one-way and set
            // exactly once. No double enrollment, no double notification.
            log.info("Stripe webhook: transaction {} already {} - completed event replay ignored",
                    tx.getPublicId(), tx.getStatus());
            return;
        }

        tx.setStatus(PaymentStatus.PAID);
        tx.setStripePaymentIntentId(event.paymentIntentId());
        tx.setPaidAt(LocalDateTime.now());
        transactionRepository.save(tx);

        // Same call the legacy free-join uses; already-enrolled pairs no-op.
        mentorShipService.finalizePaidEnrollment(tx);

        log.info("Stripe webhook: transaction {} PAID (amount {} {}) - enrollment finalized",
                tx.getPublicId(), tx.getAmount(), tx.getCurrency());
    }

    private void markExpired(PaymentTransaction tx) {
        if (tx.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        tx.setStatus(PaymentStatus.EXPIRED);
        tx.setTerminalReason("Checkout session expired unpaid");
        transactionRepository.save(tx);
        log.info("Stripe webhook: transaction {} EXPIRED", tx.getPublicId());
    }

    private void markRefunded(PaymentTransaction tx, StripeEventEnvelope event) {
        if (tx.getStatus() != PaymentStatus.PAID) {
            // PENDING/EXPIRED/CANCELED refunds of money never received: ignore.
            return;
        }
        // F decision (Omar, 2026-11-12): record-only. Enrollment access stays;
        // revocation is deferred to a future business-logic sprint.
        tx.setStatus(PaymentStatus.REFUNDED);
        tx.setTerminalReason("Charge refunded by Stripe event " + event.eventId());
        transactionRepository.save(tx);
        log.info("Stripe webhook: transaction {} REFUNDED (record-only; enrollment access stays)", tx.getPublicId());
    }

    private void markDisputed(PaymentTransaction tx, StripeEventEnvelope event) {
        if (tx.getStatus() != PaymentStatus.PAID) {
            return;
        }
        tx.setStatus(PaymentStatus.DISPUTED);
        tx.setTerminalReason(truncate("Card dispute opened: " + event.reason()));
        transactionRepository.save(tx);
        log.info("Stripe webhook: transaction {} DISPUTED (record-only)", tx.getPublicId());
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= 255) {
            return value;
        }
        return value.substring(0, 255);
    }
}
