package com.example.gradproj.EduNest.flow;

import com.example.gradproj.EduNest.entity.notification.UserNotification;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.repository.notification.UserNotificationRepository;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-04 / EB-38 — Notification Delivery & Read Flow Integration Suite (TST-01 payload 3).
 *
 * Covers:
 * 1. Happy-path: Notifications dispatched to user -> User checks unread count and queries
 *    notifications list -> User marks single notification as read -> Count decrements ->
 *    User marks all as read -> Count becomes 0 -> User deletes notification.
 * 2. Failure-path: Stranger cannot mark another user's notification as read -> 403 Forbidden.
 * 3. Failure-path: Stranger cannot delete another user's notification -> 403 Forbidden.
 */
@Tag("critical")
@Tag("business-flow")
public class NotificationDeliveryFlowIntegrationTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private NotificationService notificationService;
	@Autowired private UserNotificationRepository userNotifications;

	@Test
	@DisplayName("Flow 6 Happy Path: Receive notifications -> Read unread count -> Mark one read -> Mark all read -> Delete")
	void notificationDeliveryFlow_happyPath() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String userEmail = "flow-m6-user-" + unique + "@example.org";

		Student user = auth.student(userEmail);
		String userToken = auth.authToken(userEmail, AuthFixture.STUDENT);

		// Step 1: Dispatch two domain notifications to user
		notificationService.sendToUserByEmail(
				userEmail,
				"Welcome to EduNest " + unique,
				"Explore mentorships and enhance your skills",
				NotificationType.ANNOUNCEMENT
		);
		notificationService.sendToUserByEmail(
				userEmail,
				"Upcoming Task Deadline " + unique,
				"Your task is due in 24 hours",
				NotificationType.TASK
		);

		// Step 2: User queries unread count -> exactly 2
		mockMvc.perform(get("/api/v1/notifications/unread/count")
						.header("Authorization", userToken))
				.andExpect(status().isOk())
				.andExpect(content().string("2"));

		// Step 3: User queries unread notifications page
		mockMvc.perform(get("/api/v1/notifications/unread")
						.header("Authorization", userToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.totalElements").value(2));

		Page<UserNotification> notifPage = userNotifications.findWithNotification(userEmail, PageRequest.of(0, 10));
		assertThat(notifPage.getContent()).hasSize(2);
		UserNotification firstNotif = notifPage.getContent().get(0);
		UserNotification secondNotif = notifPage.getContent().get(1);

		// Step 4: User marks the first notification as read
		mockMvc.perform(patch("/api/v1/notifications/{id}/mark-read", firstNotif.getId())
						.header("Authorization", userToken))
				.andExpect(status().isOk());

		// Observable effect: unread count decrements to 1
		mockMvc.perform(get("/api/v1/notifications/unread/count")
						.header("Authorization", userToken))
				.andExpect(status().isOk())
				.andExpect(content().string("1"));

		UserNotification reloadedFirst = userNotifications.findById(firstNotif.getId()).orElseThrow();
		assertThat(reloadedFirst.isRead()).isTrue();

		// Step 5: User marks all notifications as read
		mockMvc.perform(patch("/api/v1/notifications/mark-all-read")
						.header("Authorization", userToken))
				.andExpect(status().isOk());

		// Observable effect: unread count is 0
		mockMvc.perform(get("/api/v1/notifications/unread/count")
						.header("Authorization", userToken))
				.andExpect(status().isOk())
				.andExpect(content().string("0"));

		// Step 6: User deletes a notification
		mockMvc.perform(delete("/api/v1/notifications/delete/{id}", secondNotif.getId())
						.header("Authorization", userToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.status").value("notification deleted successfully"));

		// Observable effect: deleted from repository
		assertThat(userNotifications.findById(secondNotif.getId())).isEmpty();
	}

	@Test
	@DisplayName("Flow 6 Failure Path: Stranger cannot mark another user's notification as read (403 Forbidden)")
	void notificationFlow_strangerCannotMarkRead() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String ownerEmail = "flow-m6-own-" + unique + "@example.org";
		String strangerEmail = "flow-m6-stranger-" + unique + "@example.org";

		auth.student(ownerEmail);
		auth.student(strangerEmail);

		notificationService.sendToUserByEmail(
				ownerEmail,
				"Confidential Alert " + unique,
				"Account alert content",
				NotificationType.ANNOUNCEMENT
		);

		Page<UserNotification> notifs = userNotifications.findWithNotification(ownerEmail, PageRequest.of(0, 1));
		UserNotification un = notifs.getContent().get(0);

		String strangerToken = auth.authToken(strangerEmail, AuthFixture.STUDENT);

		mockMvc.perform(patch("/api/v1/notifications/{id}/mark-read", un.getId())
						.header("Authorization", strangerToken))
				.andExpect(status().isForbidden());

		// Observable effect: remains unread
		UserNotification unmodified = userNotifications.findById(un.getId()).orElseThrow();
		assertThat(unmodified.isRead()).isFalse();
	}

	@Test
	@DisplayName("Flow 6 Failure Path: Stranger cannot delete another user's notification (403 Forbidden)")
	void notificationFlow_strangerCannotDeleteNotification() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String ownerEmail = "flow-m6-del-own-" + unique + "@example.org";
		String strangerEmail = "flow-m6-del-stranger-" + unique + "@example.org";

		auth.student(ownerEmail);
		auth.student(strangerEmail);

		notificationService.sendToUserByEmail(
				ownerEmail,
				"Important Notification " + unique,
				"Must not be deleted by stranger",
				NotificationType.ANNOUNCEMENT
		);

		Page<UserNotification> notifs = userNotifications.findWithNotification(ownerEmail, PageRequest.of(0, 1));
		UserNotification un = notifs.getContent().get(0);

		String strangerToken = auth.authToken(strangerEmail, AuthFixture.STUDENT);

		mockMvc.perform(delete("/api/v1/notifications/delete/{id}", un.getId())
						.header("Authorization", strangerToken))
				.andExpect(status().isForbidden());

		// Observable effect: notification still exists
		assertThat(userNotifications.findById(un.getId())).isPresent();
	}
}
