package com.example.gradproj.EduNest.entity.payment;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.users.Student;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * EB-108 / P4-18 (D-28) — a Stripe-backed enrollment payment attempt.
 *
 * One row per created Checkout Session (one per student/mentorship join attempt).
 * The {@code uk_payment_tx_stripe_session} unique constraint is the webhook
 * idempotency backstop at the schema level: every Stripe event resolves to at
 * most one transaction.
 *
 * Amount/currency are SNAPSHOTS captured server-side at session creation from
 * {@code MentorShip.price − discountPercentage} (D-28: never client input). If
 * the mentor later changes the price, a paid session still records the amount
 * actually agreed at checkout time.
 *
 * The Enrollment (and mentor notification / TotalPoints) is created ONLY when
 * the webhook flips PENDING → PAID; see {@code PaymentStatus} for the
 * transition matrix and the refund/dispute record-only disposition (F).
 */
@Entity
@Table(name = "payment_transactions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_tx_stripe_session",
                columnNames = {"stripe_session_id"})
}, indexes = {
        @Index(name = "idx_payment_tx_student", columnList = "student_id"),
        @Index(name = "idx_payment_tx_mentorship", columnList = "mentorship_id"),
        @Index(name = "idx_payment_tx_status", columnList = "status"),
        @Index(name = "idx_payment_tx_payment_intent", columnList = "stripe_payment_intent_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PaymentTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_payment_tx_student"))
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentorship_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_payment_tx_mentorship"))
    private MentorShip mentorShip;

    /** Server-computed effective amount at session creation (USD, whole dollars). */
    @Column(nullable = false)
    private Double amount;

    /** ISO-4217 currency — hardcoded USD for this phase (E decision). */
    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    /** Stripe Checkout Session id ({@code cs_...}) — unique, the webhook join key. */
    @Column(name = "stripe_session_id", nullable = false, length = 255)
    private String stripeSessionId;

    /** Stripe PaymentIntent id ({@code pi_...}) — filled from webhook events, may stay null. */
    @Column(name = "stripe_payment_intent_id", length = 255)
    private String stripePaymentIntentId;

    /** When the webhook confirmed payment (null until PAID). */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** Human-readable reason for a terminal non-success state (refund/dispute/cancel). */
    @Column(name = "terminal_reason", length = 255)
    private String terminalReason;
}
