package com.example.gradproj.EduNest.service.email;

import com.example.gradproj.EduNest.dto.email.FailedEmailDetailResponse;
import com.example.gradproj.EduNest.dto.email.FailedEmailResponse;
import com.example.gradproj.EduNest.entity.email.FailedEmailAttempt;
import com.example.gradproj.EduNest.entity.email.FailedEmailStatus;
import com.example.gradproj.EduNest.entity.users.Admin;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.repository.email.FailedEmailAttemptRepository;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.service.register.EmailService;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EB-62 / INT-02: Integration test for Email Delivery Observability & Bounded Retries.
 *
 * Verifies that:
 * 1. Initial SMTP failure records a FailedEmailAttempt with status PENDING, error, and nextRetryAt.
 * 2. Background retry worker successfully sends email on recovery and updates status to DELIVERED.
 * 3. Bounded retries cap at maxAttempts (3) and transition to EXHAUSTED.
 * 4. Admin REST endpoints enforce role security and allow manual inspection, retry, and resolution.
 */
@Tag("critical")
@Tag("email-observability")
public class EmailObservabilityIntegrationTest extends ProdChainIntegrationTestBase {

    @Autowired
    private EmailService emailService;

    @Autowired
    private EmailRetryService emailRetryService;

    @Autowired
    private FailedEmailAttemptRepository failedEmailRepo;

    @Autowired
    private AuthFixture auth;

    @SpyBean
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        failedEmailRepo.deleteAll();
    }

    @Test
    @DisplayName("EB-62 / INT-02: Initial SMTP send failure records FailedEmailAttempt with PENDING status")
    void test1_initialFailureRecordedInDatabaseWithPendingStatus() {
        doThrow(new MailSendException("SMTP connection refused: localhost:3025"))
                .when(mailSender).send(any(MimeMessage.class));

        String recipient = "student-fail-" + UUID.randomUUID().toString().substring(0, 8) + "@example.org";
        String subject = "Mentorship Update";
        String body = "<h1>New content published</h1>";

        emailService.sendEmail(recipient, subject, body);

        // Await async execution
        long deadline = System.currentTimeMillis() + 5000;
        FailedEmailAttempt record = null;
        while (System.currentTimeMillis() < deadline) {
            List<FailedEmailAttempt> all = failedEmailRepo.findAll();
            if (!all.isEmpty()) {
                record = all.get(0);
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        assertThat(record).as("Failed email attempt must be persisted in database").isNotNull();
        assertThat(record.getRecipient()).isEqualTo(recipient);
        assertThat(record.getSubject()).isEqualTo(subject);
        assertThat(record.getHtmlBody()).isEqualTo(body);
        assertThat(record.getStatus()).isEqualTo(FailedEmailStatus.PENDING);
        assertThat(record.getAttemptCount()).isEqualTo(1);
        assertThat(record.getMaxAttempts()).isEqualTo(3);
        assertThat(record.getLastError()).contains("SMTP connection refused");
        assertThat(record.getNextRetryAt()).isNotNull();
    }

    @Test
    @DisplayName("EB-62 / INT-02: Retry worker delivers email upon SMTP recovery and marks DELIVERED")
    void test2_retryPendingEmails_succeedsAndTransitionsToDelivered() {
        LocalDateTime now = LocalDateTime.now();
        FailedEmailAttempt attempt = FailedEmailAttempt.builder()
                .recipient("recover-student@example.org")
                .subject("Session Reminder")
                .htmlBody("<p>Session begins soon</p>")
                .attemptCount(1)
                .maxAttempts(3)
                .status(FailedEmailStatus.PENDING)
                .lastError("Previous timeout")
                .lastAttemptAt(now.minusMinutes(2))
                .nextRetryAt(now.minusMinutes(1))
                .build();
        attempt = failedEmailRepo.save(attempt);

        // Mail succeeds now
        doNothing().when(mailSender).send(any(MimeMessage.class));

        emailRetryService.retryPendingEmails();

        FailedEmailAttempt reloaded = failedEmailRepo.findById(attempt.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FailedEmailStatus.DELIVERED);
        assertThat(reloaded.getAttemptCount()).isEqualTo(2);
        assertThat(reloaded.getNextRetryAt()).isNull();
    }

    @Test
    @DisplayName("EB-62 / INT-02: Retry worker caps at maxAttempts and transitions to EXHAUSTED")
    void test3_retryPendingEmails_capsAtMaxAttemptsAndMarksExhausted() {
        LocalDateTime now = LocalDateTime.now();
        FailedEmailAttempt attempt = FailedEmailAttempt.builder()
                .recipient("exhaust-student@example.org")
                .subject("Important Alert")
                .htmlBody("<p>Urgent Notice</p>")
                .attemptCount(2)
                .maxAttempts(3)
                .status(FailedEmailStatus.PENDING)
                .lastError("Relay failure 2")
                .lastAttemptAt(now.minusMinutes(6))
                .nextRetryAt(now.minusMinutes(1))
                .build();
        attempt = failedEmailRepo.save(attempt);

        // Mail continues to fail
        doThrow(new MailSendException("Permanent 550 Mailbox unavailable"))
                .when(mailSender).send(any(MimeMessage.class));

        emailRetryService.retryPendingEmails();

        FailedEmailAttempt reloaded = failedEmailRepo.findById(attempt.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FailedEmailStatus.EXHAUSTED);
        assertThat(reloaded.getAttemptCount()).isEqualTo(3);
        assertThat(reloaded.getNextRetryAt()).isNull();
        assertThat(reloaded.getLastError()).contains("Permanent 550 Mailbox unavailable");
    }

    @Test
    @DisplayName("EB-62 / INT-02: Admin endpoints enforce role security and allow inspection/manual actions")
    void test4_adminEndpoints_roleAccessAndManualActions() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        FailedEmailAttempt attempt = FailedEmailAttempt.builder()
                .recipient("admin-inspect@example.org")
                .subject("Password Reset")
                .htmlBody("<p>Reset token</p>")
                .attemptCount(3)
                .maxAttempts(3)
                .status(FailedEmailStatus.EXHAUSTED)
                .lastError("Network down")
                .lastAttemptAt(now)
                .build();
        attempt = failedEmailRepo.save(attempt);
        UUID publicId = attempt.getPublicId();

        String studentEmail = "student-no-access-" + UUID.randomUUID().toString().substring(0, 6) + "@example.org";
        Student s = auth.student(studentEmail);
        String studentToken = auth.authToken(studentEmail, AuthFixture.STUDENT);

        String adminEmail = "admin-email-obs-" + UUID.randomUUID().toString().substring(0, 6) + "@example.org";
        Admin a = auth.admin(adminEmail);
        String adminToken = auth.authToken(adminEmail, AuthFixture.ADMIN);

        // 1. Anonymous access is rejected
        mockMvc.perform(get("/api/v1/admin/failed-emails"))
                .andExpect(status().isUnauthorized());

        // 2. Student access is rejected with 403 Forbidden
        mockMvc.perform(get("/api/v1/admin/failed-emails")
                        .header("Authorization", studentToken))
                .andExpect(status().isForbidden());

        // 3. Admin access succeeds with paginated list
        mockMvc.perform(get("/api/v1/admin/failed-emails")
                        .header("Authorization", adminToken)
                        .param("status", "EXHAUSTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].recipient").value("admin-inspect@example.org"))
                .andExpect(jsonPath("$.content[0].status").value("EXHAUSTED"));

        // 4. Admin detail inspection returns htmlBody
        mockMvc.perform(get("/api/v1/admin/failed-emails/" + publicId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipient").value("admin-inspect@example.org"))
                .andExpect(jsonPath("$.htmlBody").value("<p>Reset token</p>"));

        // 5. Admin manual retry executes
        doNothing().when(mailSender).send(any(MimeMessage.class));
        mockMvc.perform(post("/api/v1/admin/failed-emails/" + publicId + "/retry")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.delivery").value("DELIVERED"));

        // 6. Admin manual resolution marks RESOLVED
        mockMvc.perform(patch("/api/v1/admin/failed-emails/" + publicId + "/resolve")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.status").value("Failed email record marked as RESOLVED"));

        FailedEmailAttempt reloaded = failedEmailRepo.findByPublicId(publicId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FailedEmailStatus.RESOLVED);
        assertThat(reloaded.getResolvedBy()).isEqualTo(adminEmail);
    }
}
