package com.example.gradproj.EduNest.flow;

import com.example.gradproj.EduNest.config.integrations.StripeProperties;
import com.example.gradproj.EduNest.entity.mentorship.Commission;
import com.example.gradproj.EduNest.entity.mentorship.Enrollment;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.payment.PaymentStatus;
import com.example.gradproj.EduNest.entity.payment.PaymentTransaction;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.mentorShip.CommissionRepository;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.payment.PaymentTransactionRepository;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.service.integration.stripe.StripeCheckoutRequest;
import com.example.gradproj.EduNest.service.integration.stripe.StripeCheckoutSession;
import com.example.gradproj.EduNest.service.integration.stripe.StripeEventEnvelope;
import com.example.gradproj.EduNest.service.integration.stripe.StripePaymentGateway;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EB-108 / P4-18 (D-28) — Stripe-backed join flow, full prod-chain integration
 * (H2, no Docker, no network: the StripePaymentGateway bean is mocked, so no
 * real Stripe API call ever happens here — the REAL SDK HMAC verification is
 * pinned separately in StripePaymentGatewayContractTest).
 *
 * Pinned contracts:
 * 1. Free Tier Bypass (Omar, 2026-11-12): effective price 0 -> direct legacy
 *    enrollment, the gateway is NEVER invoked.
 * 2. Paid join (Stripe enabled): /join returns ONLY a checkoutUrl; a PENDING
 *    transaction snapshots the server-computed amount; NO enrollment exists.
 * 3. The webhook (public route, no auth header) finalizes PAID -> enrollment
 *    created from the PAID amount snapshot; replays are idempotent.
 * 4. Fail-closed webhook: gateway signature rejection -> 400.
 * 5. No stacked pending checkouts per (student, mentorship): 409.
 * 6. Refund record-only: transaction flips REFUNDED, enrollment access stays (F).
 */
@Tag("critical")
@Tag("business-flow")
@TestPropertySource(properties = {
        "app.stripe.enabled=true",
        "app.stripe.secret-key=sk_test_placeholder",
        "app.stripe.webhook-secret=whsec_test_placeholder",
        "app.stripe.success-url=https://app.example.org/pay/success",
        "app.stripe.cancel-url=https://app.example.org/pay/cancel"
})
class StripeEnrollmentFlowIntegrationTest extends ProdChainIntegrationTestBase {

    @Autowired private AuthFixture auth;
    @Autowired private MentorShipRepository mentorships;
    @Autowired private EnrollmentRepository enrollments;
    @Autowired private PaymentTransactionRepository transactions;
    @Autowired private CommissionRepository commissions;
    @Autowired private StripeProperties stripeProperties;    @MockitoBean private StripePaymentGateway stripePaymentGateway;

    private String unique;
    private Student student;
    private Mentor mentor;
    private String studentToken;

    @BeforeEach
    void seed() {
        if (commissions.count() == 0) {
            commissions.saveAndFlush(Commission.builder().rate(10.0).build());
        }
        unique = uniqueSuffix();
        student = auth.student("stripe-student-" + unique + "@example.org");
        mentor = auth.mentor("stripe-mentor-" + unique + "@example.org");
        studentToken = auth.authToken(student.getEmail(), AuthFixture.STUDENT);
    }

    private static String uniqueSuffix() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private MentorShip activeMentorship(double price, int discount) {
        return mentorships.saveAndFlush(MentorShip.builder()
                .title("Stripe Flow " + unique)
                .subtitle("payment flow")
                .description("Stripe-backed enrollment")
                .category("backEnd")
                .price(price)
                .discountPercentage(discount)
                .duration(4.0)
                .status(Status.ACTIVE)
                .mentor(mentor)
                .build());
    }

    // ---------- 1. Free Tier Bypass ----------

    @Test
    @DisplayName("Free Tier Bypass: $0 mentorship joins directly, Stripe never invoked")
    void freeTierBypass_zeroPriceJoinsDirectly() throws Exception {
        MentorShip free = activeMentorship(0.0, 0);

        mockMvc.perform(post("/api/v1/mentorship/{id}/join", free.getPublicId())
                        .header("Authorization", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.message").value("Mentorship joined successfully"));

        assertThat(enrollments.existsByMentorShip_IdAndStudent_Id(free.getId(), student.getId())).isTrue();
        // no checkout attempt for THIS mentorship (shared in-memory H2: scope to own rows)
        assertThat(transactions.findAll().stream()
                .noneMatch(t -> t.getMentorShip() != null && free.getId().equals(t.getMentorShip().getId())))
                .isTrue();
        verify(stripePaymentGateway, never()).createCheckoutSession(any());
    }

    @Test
    @DisplayName("Free Tier Bypass: 100% discount -> effective price 0 -> direct join")
    void freeTierBypass_fullDiscountJoinsDirectly() throws Exception {
        MentorShip discounted = activeMentorship(200.0, 100);

        mockMvc.perform(post("/api/v1/mentorship/{id}/join", discounted.getPublicId())
                        .header("Authorization", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.message").value("Mentorship joined successfully"));

        assertThat(enrollments.existsByMentorShip_IdAndStudent_Id(discounted.getId(), student.getId())).isTrue();
        verify(stripePaymentGateway, never()).createCheckoutSession(any());
    }

    // ---------- 2. Paid join -> checkout ----------

    @Test
    @DisplayName("Paid join: 200 with checkoutUrl, PENDING transaction snapshots the server amount, NO enrollment")
    void paidJoin_returnsCheckoutUrlAndPendingTransaction() throws Exception {
        MentorShip paid = activeMentorship(200.0, 10);
        String sessionId = "cs_test_" + unique;

        when(stripePaymentGateway.createCheckoutSession(any()))
                .thenReturn(new StripeCheckoutSession(sessionId, "https://checkout.stripe.com/c/pay/" + sessionId));

        mockMvc.perform(post("/api/v1/mentorship/{id}/join", paid.getPublicId())
                        .header("Authorization", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.checkoutUrl")
                        .value("https://checkout.stripe.com/c/pay/" + sessionId));

        // D-28: the enrollment does NOT exist until the webhook confirms payment
        assertThat(enrollments.existsByMentorShip_IdAndStudent_Id(paid.getId(), student.getId())).isFalse();

        List<PaymentTransaction> pending = transactions.findAll().stream()
                .filter(t -> sessionId.equals(t.getStripeSessionId())).toList();
        assertThat(pending).hasSize(1);
        PaymentTransaction tx = pending.getFirst();
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(tx.getAmount()).isEqualTo(180.0); // server-computed: 200 - 10%
        assertThat(tx.getCurrency()).isEqualTo("USD");

        // server-side amounts only: cents from the mentorship record, never FE input
        ArgumentCaptor<StripeCheckoutRequest> req = ArgumentCaptor.forClass(StripeCheckoutRequest.class);
        verify(stripePaymentGateway).createCheckoutSession(req.capture());
        assertThat(req.getValue().amountCents()).isEqualTo(18000L);
        assertThat(req.getValue().studentEmail()).isEqualTo(student.getEmail());
        assertThat(req.getValue().mentorshipPublicId()).isEqualTo(paid.getPublicId().toString());
        assertThat(req.getValue().transactionPublicId()).isEqualTo(tx.getPublicId().toString());
    }

    @Test
    @DisplayName("No stacked checkouts: second paid join while one is PENDING -> 409")
    void secondPaidJoinWhilePending_conflict() throws Exception {
        MentorShip paid = activeMentorship(200.0, 10);
        String sessionId = "cs_conflict_" + unique;

        when(stripePaymentGateway.createCheckoutSession(any()))
                .thenReturn(new StripeCheckoutSession(sessionId, "https://checkout.stripe.com/c/pay/" + sessionId));

        mockMvc.perform(post("/api/v1/mentorship/{id}/join", paid.getPublicId())
                        .header("Authorization", studentToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/mentorship/{id}/join", paid.getPublicId())
                        .header("Authorization", studentToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ERR_DUPLICATE_RESOURCE"));
    }

    // ---------- 3+4. Webhook finalization (public route) ----------

    @Test
    @DisplayName("Webhook completed: public route (no auth header), PAID -> enrollment created from the snapshot; replay idempotent")
    void webhookCompleted_finalizesEnrollment_idempotent() throws Exception {
        MentorShip paid = activeMentorship(200.0, 10);
        PaymentTransaction tx = seedPendingTransaction(student, paid, 180.0, "cs_hook_1");

        when(stripePaymentGateway.verifyAndParseWebhook(any(), any())).thenReturn(new StripeEventEnvelope(
                "evt_hook_1", "checkout.session.completed", "cs_hook_1", "pi_hook_1", null));

        // NO Authorization header: the webhook is a public route
        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType("application/json")
                        .content("{\"id\":\"evt_hook_1\",\"object\":\"event\"}"))
                .andExpect(status().isOk());

        Enrollment enrollment = enrollments.findAll().stream()
                .filter(e -> e.getMentorShip().getId().equals(paid.getId())
                        && e.getStudent().getId().equals(student.getId()))
                .findFirst().orElseThrow();
        // PAID amount snapshot is what counts, even if the price changed since
        assertThat(enrollment.getPrice()).isEqualTo(180.0);
        assertThat(enrollment.getPlatformProfit()).isEqualTo(18.0); // 10% commission

        PaymentTransaction paidTx = transactions.findById(tx.getId()).orElseThrow();
        assertThat(paidTx.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paidTx.getStripePaymentIntentId()).isEqualTo("pi_hook_1");
        assertThat(paidTx.getPaidAt()).isNotNull();

        // replay of the same event: idempotent — still exactly one enrollment
        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType("application/json")
                        .content("{\"id\":\"evt_hook_1\",\"object\":\"event\"}"))
                .andExpect(status().isOk());

        assertThat(enrollments.findAll().stream()
                .filter(e -> e.getMentorShip().getId().equals(paid.getId())
                        && e.getStudent().getId().equals(student.getId()))
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Webhook fail-closed: gateway signature rejection -> 400, state untouched")
    void webhookBadSignature_rejected() throws Exception {
        MentorShip paid = activeMentorship(200.0, 10);
        seedPendingTransaction(student, paid, 180.0, "cs_hook_2");

        when(stripePaymentGateway.verifyAndParseWebhook(any(), any()))
                .thenThrow(new IllegalArgumentException("Stripe webhook signature verification failed"));

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());

        // fail closed: THIS pending transaction was never mutated
        assertThat(transactions.findAll().stream()
                .filter(t -> "cs_hook_2".equals(t.getStripeSessionId()))
                .allMatch(t -> t.getStatus() == PaymentStatus.PENDING)).isTrue();
        assertThat(enrollments.findAll().stream()
                .noneMatch(e -> e.getMentorShip() != null && paid.getId().equals(e.getMentorShip().getId())))
                .isTrue();
    }

    // ---------- 6. Refund record-only ----------

    @Test
    @DisplayName("Refund: PAID -> REFUNDED record-only; the enrollment access STAYS (F decision)")
    void refund_recordOnly_accessStays() throws Exception {
        MentorShip paid = activeMentorship(200.0, 10);
        PaymentTransaction tx = seedPendingTransaction(student, paid, 180.0, "cs_hook_3");
        tx.setStatus(PaymentStatus.PAID);
        tx.setStripePaymentIntentId("pi_hook_3");
        transactions.saveAndFlush(tx);
        // enrollment exists (paid and finalized earlier)
        enrollments.saveAndFlush(Enrollment.builder()
                .student(student).mentorShip(paid).price(180.0).platformProfit(18.0)
                .joinedAt(java.time.LocalDateTime.now()).build());

        when(stripePaymentGateway.verifyAndParseWebhook(any(), any())).thenReturn(new StripeEventEnvelope(
                "evt_refund_1", "charge.refunded", null, "pi_hook_3", "refunded"));

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        PaymentTransaction refunded = transactions.findById(tx.getId()).orElseThrow();
        assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        // F decision: record-only — access stays
        assertThat(enrollments.existsByMentorShip_IdAndStudent_Id(paid.getId(), student.getId())).isTrue();
    }

    private PaymentTransaction seedPendingTransaction(Student s, MentorShip ms, double amount, String sessionId) {
        return transactions.saveAndFlush(PaymentTransaction.builder()
                .student(s).mentorShip(ms)
                .amount(amount)
                .currency(stripeProperties.getCurrency())
                .status(PaymentStatus.PENDING)
                .stripeSessionId(sessionId)
                .build());
    }
}
