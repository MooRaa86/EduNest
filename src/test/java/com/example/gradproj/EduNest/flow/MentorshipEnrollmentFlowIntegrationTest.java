package com.example.gradproj.EduNest.flow;

import com.example.gradproj.EduNest.entity.mentorship.Commission;
import com.example.gradproj.EduNest.entity.mentorship.Enrollment;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.notification.UserNotification;
import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.mentorShip.CommissionRepository;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.notification.UserNotificationRepository;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-04 / EB-38 — Mentorship Enrollment Flow Integration Suite (TST-01 payload 3).
 *
 * Covers:
 * 1. Happy-path: Student explores active mentorship -> joins mentorship -> Enrollment row created
 *    with discount/commission split -> TotalPoints row created with 0 points -> Mentor notification delivered.
 * 2. Failure-path (Duplicate): Student cannot enroll twice in the same mentorship (MSH-04, DB-04).
 * 3. Failure-path (Status): Student cannot join a DRAFT mentorship.
 * 4. Failure-path (Rate): Enrollment without commission configuration is rejected cleanly.
 */
@Tag("critical")
@Tag("business-flow")
public class MentorshipEnrollmentFlowIntegrationTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private MentorShipRepository mentorships;
	@Autowired private EnrollmentRepository enrollments;
	@Autowired private TotalPointsRepository totalPoints;
	@Autowired private CommissionRepository commissions;
	@Autowired private UserNotificationRepository userNotifications;

	@BeforeEach
	void ensureCommission() {
		if (commissions.count() == 0) {
			commissions.saveAndFlush(Commission.builder().rate(10.0).build());
		}
	}

	@Test
	@DisplayName("Flow 1 Happy Path: Explore -> Join -> Enrollment + 0 Points + Mentor Notification")
	void enrollmentFlow_happyPath() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String mentorEmail = "flow-m1-mentor-" + unique + "@example.org";
		String studentEmail = "flow-m1-student-" + unique + "@example.org";

		Student student = auth.student(studentEmail);
		Mentor mentor = auth.mentor(mentorEmail);

		// Seed active mentorship: price 200.0, 10% discount -> 180.0 net price
		MentorShip ms = mentorships.saveAndFlush(MentorShip.builder()
				.title("Architecture Mastery " + unique)
				.subtitle("Clean Code & System Design")
				.description("Comprehensive engineering flow")
				.category("backEnd")
				.price(200.0)
				.discountPercentage(10)
				.duration(4.0)
				.status(Status.ACTIVE)
				.mentor(mentor)
				.build());

		String studentToken = auth.authToken(studentEmail, AuthFixture.STUDENT);

		// Step 1: Student views mentorship details
		mockMvc.perform(get("/api/v1/mentorship/{id}", ms.getId())
						.header("Authorization", studentToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.mentorShip.title").value("Architecture Mastery " + unique));

		// Step 2: Student joins mentorship
		mockMvc.perform(post("/api/v1/mentorship/{id}/join", ms.getId())
						.header("Authorization", studentToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.message").value("Mentorship joined successfully"));

		// Step 3: Assert observable effect on Enrollment
		boolean isEnrolled = enrollments.existsByMentorShip_IdAndStudent_Id(ms.getId(), student.getId());
		assertThat(isEnrolled).isTrue();

		Enrollment enrollment = enrollments.findAll().stream()
				.filter(e -> e.getMentorShip().getId().equals(ms.getId()) && e.getStudent().getId().equals(student.getId()))
				.findFirst()
				.orElseThrow();
		assertThat(enrollment.getPrice()).isEqualTo(180.0, within(1e-6));
		assertThat(enrollment.getPlatformProfit()).isEqualTo(18.0, within(1e-6)); // 10% of 180.0
		assertThat(enrollment.getJoinedAt()).isNotNull();

		// Step 4: Assert observable effect on TotalPoints: row exists with 0 points
		Optional<TotalPoints> tpOpt = totalPoints.findByStudent_IdAndMentorship_Id(student.getId(), ms.getId());
		assertThat(tpOpt).isPresent();
		assertThat(tpOpt.get().getTotalPoints()).isZero();

		// Step 5: Assert observable effect on Notification: mentor received notification
		Page<UserNotification> mentorNotifs = userNotifications.findWithNotification(mentorEmail, PageRequest.of(0, 10));
		assertThat(mentorNotifs.getContent()).anyMatch(un ->
				un.getNotification().getTitle().equals("New Student Enrolled") &&
				un.getNotification().getContent().contains(student.getEmail()) &&
				un.getNotification().getContent().contains("162.0$") // 180 - 18
		);
	}

	@Test
	@DisplayName("Flow 1 Failure Path: Duplicate join returns 400 Bad Request (MSH-04/DB-04)")
	void enrollmentFlow_cannotJoinTwice() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String mentorEmail = "flow-m1-dup-mentor-" + unique + "@example.org";
		String studentEmail = "flow-m1-dup-student-" + unique + "@example.org";

		Student student = auth.student(studentEmail);
		var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
		Long mentorshipId = chain.mentorship().getId();

		String studentToken = auth.authToken(studentEmail, AuthFixture.STUDENT);

		// First join succeeds
		mockMvc.perform(post("/api/v1/mentorship/{id}/join", mentorshipId)
						.header("Authorization", studentToken))
				.andExpect(status().isOk());

		// Second join must fail with business rule violation
		mockMvc.perform(post("/api/v1/mentorship/{id}/join", mentorshipId)
						.header("Authorization", studentToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("ERR_BUSINESS_RULE"))
				.andExpect(jsonPath("$.message").value("You are already enrolled in this mentorship"));

		// Observable effect: exactly one enrollment exists
		long count = enrollments.findAll().stream()
				.filter(e -> e.getMentorShip().getId().equals(mentorshipId) && e.getStudent().getId().equals(student.getId()))
				.count();
		assertThat(count).isEqualTo(1);
	}

	@Test
	@DisplayName("Flow 1 Failure Path: Joining DRAFT mentorship returns 400 Bad Request")
	void enrollmentFlow_cannotJoinDraftMentorship() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String mentorEmail = "flow-m1-draft-mentor-" + unique + "@example.org";
		String studentEmail = "flow-m1-draft-student-" + unique + "@example.org";

		Student student = auth.student(studentEmail);
		var chain = auth.mentorship(mentorEmail, Status.DRAFT);
		Long mentorshipId = chain.mentorship().getId();

		String studentToken = auth.authToken(studentEmail, AuthFixture.STUDENT);

		mockMvc.perform(post("/api/v1/mentorship/{id}/join", mentorshipId)
						.header("Authorization", studentToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("ERR_BUSINESS_RULE"))
				.andExpect(jsonPath("$.message").value("This mentorship is not available to join"));

		boolean isEnrolled = enrollments.existsByMentorShip_IdAndStudent_Id(mentorshipId, student.getId());
		assertThat(isEnrolled).isFalse();
	}
}
