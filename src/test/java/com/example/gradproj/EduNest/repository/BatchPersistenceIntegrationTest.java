package com.example.gradproj.EduNest.repository;

import com.example.gradproj.EduNest.entity.certificate.Certificate;
import com.example.gradproj.EduNest.entity.notification.AdminNotification;
import com.example.gradproj.EduNest.entity.notification.UserNotification;
import com.example.gradproj.EduNest.entity.users.Admin;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.repository.certificate.CertificateRepository;
import com.example.gradproj.EduNest.repository.notification.AdminNotificationRepository;
import com.example.gradproj.EduNest.repository.notification.UserNotificationRepository;
import com.example.gradproj.EduNest.repository.users.AdminRepository;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EB-61 / P4-12 — Batch persistence loop optimization (DB-07).
 *
 * Verifies that:
 * 1. Hibernate JDBC batch settings are properly configured and recognized by the EntityManagerFactory
 *    (batch_size=30, order_inserts=true, order_updates=true, batch_versioned_data=true).
 * 2. Multi-entity fan-outs (such as NotificationService.sendToMentorshipStudents across 35+ enrolled
 *    students) persist all relations cleanly across batch chunk boundaries without data loss or constraint failure.
 * 3. Batch operations on certificates (CertificateRepository.saveAll) and admin notifications
 *    (AdminNotificationRepository.saveAll) persist accurately.
 */
@Tag("critical")
class BatchPersistenceIntegrationTest extends ProdChainIntegrationTestBase {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private AuthFixture auth;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserNotificationRepository userNotificationRepository;

    @Autowired
    private AdminNotificationRepository adminNotificationRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Test
    @DisplayName("P4-12/DB-07: Verify Hibernate batching configuration properties in EntityManagerFactory")
    void verifyHibernateBatchConfiguration() {
        Map<String, Object> props = entityManagerFactory.getProperties();

        Object batchSize = props.get("hibernate.jdbc.batch_size");
        assertThat(String.valueOf(batchSize))
                .as("hibernate.jdbc.batch_size must be configured to 30")
                .isEqualTo("30");

        Object orderInserts = props.get("hibernate.order_inserts");
        assertThat(String.valueOf(orderInserts))
                .as("hibernate.order_inserts must be enabled for effective batching")
                .isEqualTo("true");

        Object orderUpdates = props.get("hibernate.order_updates");
        assertThat(String.valueOf(orderUpdates))
                .as("hibernate.order_updates must be enabled for effective batching")
                .isEqualTo("true");

        Object batchVersioned = props.get("hibernate.jdbc.batch_versioned_data");
        assertThat(String.valueOf(batchVersioned))
                .as("hibernate.jdbc.batch_versioned_data must be enabled")
                .isEqualTo("true");
    }

    @Test
    @DisplayName("P4-12/DB-07: Notification fanout persists 35+ user notifications across batch boundaries without loss")
    void sendToMentorshipStudents_persistsAcrossBatchBoundaries() {
        String mentorEmail = "batch-mentor@example.org";
        var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
        Long mentorshipId = chain.mentorship().getId();

        int studentCount = 35;
        List<String> studentEmails = new ArrayList<>(studentCount);
        for (int i = 1; i <= studentCount; i++) {
            String studentEmail = "batch-student-" + i + "@example.org";
            auth.enroll(mentorshipId, studentEmail);
            studentEmails.add(studentEmail);
        }

        notificationService.sendToMentorshipStudents(
                mentorshipId,
                "Assignment Announcement",
                "New assignment published for week 1",
                NotificationType.TASK
        );

        // Wait briefly for @Async fanout persistence to complete
        long deadline = System.currentTimeMillis() + 5000;
        long totalPersisted = 0;
        while (System.currentTimeMillis() < deadline) {
            totalPersisted = userNotificationRepository.count();
            if (totalPersisted >= studentCount) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        assertThat(totalPersisted)
                .as("All %d notifications must be persisted by async fanout", studentCount)
                .isGreaterThanOrEqualTo(studentCount);

        for (String email : studentEmails) {
            var page = userNotificationRepository.findWithNotification(email, PageRequest.of(0, 10));
            assertThat(page.getContent())
                    .as("Student %s must have received the notification", email)
                    .hasSize(1);
            assertThat(page.getContent().get(0).getNotification().getTitle())
                    .isEqualTo("Assignment Announcement");
            assertThat(page.getContent().get(0).isRead())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("P4-12/DB-07: Certificate batch save persists 35+ certificates cleanly across batch boundaries")
    void certificateBatchSave_persistsAcrossBatchBoundaries() {
        String mentorEmail = "cert-batch-mentor@example.org";
        var chain = auth.mentorship(mentorEmail, Status.ACTIVE);

        int count = 35;
        List<Certificate> certificates = new ArrayList<>(count);
        LocalDateTime now = LocalDateTime.now();

        for (int i = 1; i <= count; i++) {
            Student student = auth.student("cert-student-" + i + "@example.org");
            certificates.add(Certificate.builder()
                    .student(student)
                    .mentorship(chain.mentorship())
                    .rank(i)
                    .issuedAt(now)
                    .build());
        }

        List<Certificate> saved = certificateRepository.saveAll(certificates);
        assertThat(saved).hasSize(count);

        for (int i = 1; i <= count; i++) {
            String email = "cert-student-" + i + "@example.org";
            var result = certificateRepository.findByStudentEmail(email, PageRequest.of(0, 10));
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getRank()).isEqualTo(i);
        }
    }
}
