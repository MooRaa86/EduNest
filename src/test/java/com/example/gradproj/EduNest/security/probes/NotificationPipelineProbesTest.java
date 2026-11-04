package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.contactus.ContactMessageEntity;
import com.example.gradproj.EduNest.entity.notification.AdminNotification;
import com.example.gradproj.EduNest.entity.users.Admin;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.repository.contactus.ContactMessageRepository;
import com.example.gradproj.EduNest.repository.notification.AdminNotificationRepository;
import com.example.gradproj.EduNest.repository.notification.UserNotificationRepository;
import com.example.gradproj.EduNest.service.contactus.ContactMessageService;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P4-01 / EB-50: Notification Pipeline Probes Test.
 *
 * Covers:
 * 1. NOTIF-03: Transaction rollback suppresses pre-commit STOMP pushes (no ghost notifications).
 * 2. NOTIF-03: Transaction commit flushes STOMP pushes via afterCommit hook.
 * 3. NOTIF-04 / D-12: Admin notifications fan out to ALL active admins (not just the newest one).
 * 4. NOTIF-02: Contact message notification to guest sender gracefully skips in-app push (HTTP 200, no 404).
 * 5. NOTIF-05: Pagination size is bounded/clamped to max 100 to prevent unbounded memory queries.
 */
@Tag("critical")
@Tag("notification-pipeline")
public class NotificationPipelineProbesTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private NotificationService notificationService;
	@Autowired private ContactMessageService contactMessageService;
	@Autowired private ContactMessageRepository contactMessageRepo;
	@Autowired private UserNotificationRepository userNotificationRepo;
	@Autowired private AdminNotificationRepository adminNotificationRepo;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private ObjectMapper objectMapper;

	@SpyBean
	private SimpMessagingTemplate messagingTemplate;

	@Test
	@DisplayName("NOTIF-03: Rollback suppresses STOMP push — zero ghost notifications")
	void notif03_transactionRollbackSuppressesNotificationPush() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String email = "notif-rb-" + suffix + "@example.org";
		Student student = auth.student(email);

		TransactionTemplate tx = new TransactionTemplate(txManager);
		try {
			tx.execute(status -> {
				notificationService.sendToUserByEmail(
						email,
						"Ghost Alert",
						"This must never be delivered",
						NotificationType.ANNOUNCEMENT
				);
				// Force rollback
				status.setRollbackOnly();
				return null;
			});
		} catch (Exception ignored) {
		}

		// DB relation must not exist
		assertThat(userNotificationRepo.countByUserEmailAndIsReadFalse(email))
				.as("Rollback leaves 0 persisted notification relations")
				.isZero();

		// Push must NEVER have executed
		verify(messagingTemplate, never())
				.convertAndSendToUser(eq(email), eq("/queue/notifications"), any());
	}

	@Test
	@DisplayName("NOTIF-03: Successful commit delivers STOMP push via afterCommit")
	void notif03_transactionCommitDeliversNotificationPush() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String email = "notif-commit-" + suffix + "@example.org";
		Student student = auth.student(email);

		TransactionTemplate tx = new TransactionTemplate(txManager);
		tx.execute(status -> {
			notificationService.sendToUserByEmail(
					email,
					"Real Alert",
					"This is delivered on commit",
					NotificationType.TASK
			);
			return null;
		});

		// DB relation exists
		assertThat(userNotificationRepo.countByUserEmailAndIsReadFalse(email))
				.as("Commit persists notification relation")
				.isEqualTo(1);

		// Push executed
		verify(messagingTemplate, atLeastOnce())
				.convertAndSendToUser(eq(email), eq("/queue/notifications"), any());
	}

	@Test
	@DisplayName("NOTIF-04 (D-12): Admin notification broadcasts to ALL active admins")
	void notif04_adminNotificationBroadcastsToAllAdmins() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String admin1Email = "probe-admin1-" + suffix + "@example.org";
		String admin2Email = "probe-admin2-" + suffix + "@example.org";

		Admin a1 = auth.admin(admin1Email);
		Admin a2 = auth.admin(admin2Email);

		notificationService.sendToAdmin(
				"Broadcast Alert " + suffix,
				"New mentorship submitted for review",
				NotificationType.MENTORSHIP
		);

		TransactionTemplate tx = new TransactionTemplate(txManager);

		// Wait briefly for @Async execution and check inside tx session
		long deadline = System.currentTimeMillis() + 4000;
		int a1Count = 0;
		int a2Count = 0;
		while (System.currentTimeMillis() < deadline) {
			int[] counts = tx.execute(status -> {
				List<AdminNotification> all = adminNotificationRepo.findAll();
				long c1 = all.stream().filter(n -> n.getAdmin() != null && admin1Email.equals(n.getAdmin().getEmail())
						&& n.getTitle().contains(suffix)).count();
				long c2 = all.stream().filter(n -> n.getAdmin() != null && admin2Email.equals(n.getAdmin().getEmail())
						&& n.getTitle().contains(suffix)).count();
				return new int[]{(int) c1, (int) c2};
			});
			if (counts != null && counts[0] >= 1 && counts[1] >= 1) {
				a1Count = counts[0];
				a2Count = counts[1];
				break;
			}
			Thread.sleep(100);
		}

		assertThat(a1Count).as("Admin 1 received persisted notification").isGreaterThanOrEqualTo(1);
		assertThat(a2Count).as("Admin 2 received persisted notification").isGreaterThanOrEqualTo(1);

		// Verify both admins received STOMP delivery
		verify(messagingTemplate, atLeastOnce())
				.convertAndSendToUser(eq(admin1Email), eq("/queue/notifications"), any());
		verify(messagingTemplate, atLeastOnce())
				.convertAndSendToUser(eq(admin2Email), eq("/queue/notifications"), any());
	}

	@Test
	@DisplayName("NOTIF-02: Contact message notification to guest sender does NOT 404 — skips in-app push")
	void notif02_guestContactMessageNotificationDoesNot404() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String guestEmail = "guest" + suffix + "@external.org";
		String adminEmail = "probe-admin-guest-" + suffix + "@example.org";
		auth.admin(adminEmail);
		String adminToken = auth.authToken(adminEmail, AuthFixture.ADMIN);

		// 1. Guest submits public contact message with valid Egyptian phone format
		ContactMessageEntity msg = ContactMessageEntity.builder()
				.name("Guest User")
				.email(guestEmail)
				.phone("01012345678")
				.message("Hello support from a guest")
				.build();
		msg = contactMessageRepo.saveAndFlush(msg);

		// 2. Admin triggers in-app notification to the message sender
		mockMvc.perform(post("/api/v1/contact/message/{id}/notification", msg.getId())
						.header("Authorization", adminToken)
						.param("title", "Support Follow-up")
						.contentType(MediaType.TEXT_PLAIN)
						.content("We received your inquiry"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.Status").value(
						org.hamcrest.Matchers.containsString("in-app notification skipped")));

		// 3. Verify no push was sent for the unregistered guest
		verify(messagingTemplate, never())
				.convertAndSendToUser(eq(guestEmail), anyString(), any());
	}

	@Test
	@DisplayName("NOTIF-05: Pagination size is clamped to max 100")
	void notif05_paginationClampingEnforcesSafeMax() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String adminEmail = "probe-admin-clamp-" + suffix + "@example.org";
		auth.admin(adminEmail);
		String adminToken = auth.authToken(adminEmail, AuthFixture.ADMIN);

		// Request size = 5000 -> must be clamped to at most 100
		mockMvc.perform(get("/api/v1/admin/notifications")
						.header("Authorization", adminToken)
						.param("page", "0")
						.param("size", "5000"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(100));
	}
}
