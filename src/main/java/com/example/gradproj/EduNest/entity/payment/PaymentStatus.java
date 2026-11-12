package com.example.gradproj.EduNest.entity.payment;

/**
 * EB-108 / P4-18 (D-28) — lifecycle of a Stripe-backed enrollment payment.
 *
 * Legal transitions (enforced in code, one-way only):
 *
 *   PENDING ──checkout.session.completed──▶ PAID
 *   PENDING ──checkout.session.expired────▶ EXPIRED
 *   PENDING ──checkout session canceled───▶ CANCELED
 *   PAID ────charge.refunded──────────────▶ REFUNDED      (record-only: enrollment access stays — F decision)
 *   PAID ────charge.dispute.*─────────────▶ DISPUTED     (record-only for this cycle)
 *
 * PAID is the sole source of truth for enrollment creation and is terminal for
 * the money path: only a PENDING transaction may ever flip to PAID (webhook
 * idempotency backstop; replays of checkout.session.completed are no-ops).
 */
public enum PaymentStatus {
    /** Checkout session created, awaiting student payment. */
    PENDING,
    /** Webhook-confirmed payment — enrollment is (or is being) finalized. */
    PAID,
    /** Payment returned to the student (record-only for this cycle). */
    REFUNDED,
    /** Card dispute opened (record-only for this cycle). */
    DISPUTED,
    /** Checkout session expired unpaid. */
    EXPIRED,
    /** Checkout abandoned/canceled by the student before payment. */
    CANCELED
}
