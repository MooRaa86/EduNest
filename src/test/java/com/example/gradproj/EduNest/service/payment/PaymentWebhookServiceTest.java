package com.example.gradproj.EduNest.service.payment;

import com.example.gradproj.EduNest.entity.payment.PaymentStatus;
import com.example.gradproj.EduNest.entity.payment.PaymentTransaction;
import com.example.gradproj.EduNest.repository.payment.PaymentTransactionRepository;
import com.example.gradproj.EduNest.service.integration.stripe.StripeEventEnvelope;
import com.example.gradproj.EduNest.service.mentorShip.mentorShipService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EB-108 / P4-18 (D-28) — the enrollment-payment state machine, unit-level.
 *
 * Pinned transitions:
 * - completed: PENDING -> PAID exactly once + finalizePaidEnrollment called;
 *   replays (already PAID) are NO-OPS (no double enrollment/notification).
 * - expired: PENDING -> EXPIRED only.
 * - refunded/disputed: PAID -> record-only terminal state (F decision:
 *   enrollment access untouched — the service never touches enrollments here).
 * - unknown session/intent ids: logged + ignored (never wedge Stripe retries).
 * - unhandled event types: ignored.
 */
@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceTest {

    @Mock private PaymentTransactionRepository transactions;
    @Mock private mentorShipService mentorShipService;

    @InjectMocks private PaymentWebhookService service;

    @Test
    @DisplayName("completed + PENDING -> PAID, payment intent recorded, enrollment finalized once")
    void completed_marksPaidAndFinalizes() {
        PaymentTransaction tx = pendingTx();
        when(transactions.findByStripeSessionId("cs_1")).thenReturn(Optional.of(tx));

        service.applyVerifiedEvent(new StripeEventEnvelope(
                "evt_1", "checkout.session.completed", "cs_1", "pi_1", null));

        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(tx.getStripePaymentIntentId()).isEqualTo("pi_1");
        assertThat(tx.getPaidAt()).isNotNull();
        verify(transactions).save(tx);
        verify(mentorShipService).finalizePaidEnrollment(tx);
    }

    @Test
    @DisplayName("completed replay on already-PAID transaction: no-op, no second enrollment")
    void completedReplay_isNoOp() {
        PaymentTransaction tx = pendingTx();
        tx.setStatus(PaymentStatus.PAID);
        when(transactions.findByStripeSessionId("cs_1")).thenReturn(Optional.of(tx));

        service.applyVerifiedEvent(new StripeEventEnvelope(
                "evt_1", "checkout.session.completed", "cs_1", "pi_1", null));

        // replay: the PAID transaction is terminal — nothing re-saved, no
        // second enrollment, no double notification
        verify(transactions, never()).save(any());
        verify(mentorShipService, never()).finalizePaidEnrollment(any());
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("completed for unknown session: ignored, nothing touched")
    void completed_unknownSession_ignored() {
        when(transactions.findByStripeSessionId("cs_unknown")).thenReturn(Optional.empty());

        service.applyVerifiedEvent(new StripeEventEnvelope(
                "evt_1", "checkout.session.completed", "cs_unknown", "pi_1", null));

        verify(transactions, never()).save(any());
        verify(mentorShipService, never()).finalizePaidEnrollment(any());
    }

    @Test
    @DisplayName("expired + PENDING -> EXPIRED with reason")
    void expired_marksExpired() {
        PaymentTransaction tx = pendingTx();
        when(transactions.findByStripeSessionId("cs_1")).thenReturn(Optional.of(tx));

        service.applyVerifiedEvent(new StripeEventEnvelope(
                "evt_2", "checkout.session.expired", "cs_1", null, null));

        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(tx.getTerminalReason()).contains("expired");
        verify(transactions).save(tx);
        verify(mentorShipService, never()).finalizePaidEnrollment(any());
    }

    @Test
    @DisplayName("refund on PAID -> REFUNDED record-only; enrollment access untouched (F)")
    void refund_marksRecordOnly() {
        PaymentTransaction tx = pendingTx();
        tx.setStatus(PaymentStatus.PAID);
        when(transactions.findFirstByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(tx));

        service.applyVerifiedEvent(new StripeEventEnvelope(
                "evt_3", "charge.refunded", null, "pi_1", "refunded"));

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactions).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        // F decision: no enrollment mutation of any kind in this path
        verify(mentorShipService, never()).finalizePaidEnrollment(any());
    }

    @Test
    @DisplayName("refund on PENDING (money never received): ignored")
    void refund_onPending_ignored() {
        PaymentTransaction tx = pendingTx();
        when(transactions.findFirstByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(tx));

        service.applyVerifiedEvent(new StripeEventEnvelope(
                "evt_3", "charge.refunded", null, "pi_1", "refunded"));

        verify(transactions, never()).save(any());
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("dispute on PAID -> DISPUTED record-only with reason")
    void dispute_marksRecordOnly() {
        PaymentTransaction tx = pendingTx();
        tx.setStatus(PaymentStatus.PAID);
        when(transactions.findFirstByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(tx));

        service.applyVerifiedEvent(new StripeEventEnvelope(
                "evt_4", "charge.dispute.created", null, "pi_1", "fraudulent"));

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactions).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.DISPUTED);
        assertThat(captor.getValue().getTerminalReason()).contains("fraudulent");
    }

    @Test
    @DisplayName("unhandled event type: ignored entirely")
    void unhandledType_ignored() {
        service.applyVerifiedEvent(new StripeEventEnvelope(
                "evt_9", "invoice.paid", null, null, null));

        verify(transactions, never()).findByStripeSessionId(any());
        verify(transactions, never()).save(any());
    }

    private static PaymentTransaction pendingTx() {
        return PaymentTransaction.builder()
                .student(new com.example.gradproj.EduNest.entity.users.Student())
                .mentorShip(new com.example.gradproj.EduNest.entity.mentorship.MentorShip())
                .amount(180.0)
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .stripeSessionId("cs_1")
                .build();
    }
}
