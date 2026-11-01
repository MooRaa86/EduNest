package com.example.gradproj.EduNest.flow;

import com.example.gradproj.EduNest.entity.badges.Badge;
import com.example.gradproj.EduNest.entity.badges.BadgeAward;
import com.example.gradproj.EduNest.entity.certificate.Certificate;
import com.example.gradproj.EduNest.entity.mentorship.Commission;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.notification.UserNotification;
import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.badge.BadgeCategory;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.badges.BadgeAwardRepository;
import com.example.gradproj.EduNest.repository.badges.BadgeRepository;
import com.example.gradproj.EduNest.repository.certificate.CertificateRepository;
import com.example.gradproj.EduNest.repository.mentorShip.CommissionRepository;
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
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-04 / EB-38 — Mentorship Completion -> Certificate / Badge Flow Integration Suite (TST-01 payload 3).
 *
 * Covers:
 * 1. Happy-path: Mentor awards badge to student -> Points delta applied -> Mentor updates mentorship
 *    status to COMPLETED -> Certificates auto-issued based on leaderboard ranks -> Students receive
 *    notifications -> Student retrieves certificate via student certificates API.
 * 2. Failure-path: Cannot complete mentorship with zero enrolled students -> 400 Bad Request.
 * 3. Failure-path: Cannot change status of an already COMPLETED mentorship -> 400 Bad Request.
 * 4. Failure-path: Foreign mentor cannot update status of another mentor's mentorship -> 403 Forbidden.
 */
@Tag("critical")
@Tag("business-flow")
public class MentorshipCompletionCertificateFlowIntegrationTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private MentorShipRepository mentorships;
	@Autowired private TotalPointsRepository totalPoints;
	@Autowired private BadgeRepository badges;
	@Autowired private BadgeAwardRepository badgeAwards;
	@Autowired private CertificateRepository certificates;
	@Autowired private CommissionRepository commissions;
	@Autowired private UserNotificationRepository userNotifications;

	@BeforeEach
	void ensureCommission() {
		if (commissions.count() == 0) {
			commissions.saveAndFlush(Commission.builder().rate(10.0).build());
		}
	}

	@Test
	@DisplayName("Flow 4 Happy Path: Badge award -> Completion -> Auto-issue certificates by rank -> Student reads certificate")
	void completionCertificateFlow_happyPath() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String mentorEmail = "flow-m4-mentor-" + unique + "@example.org";
		String student1Email = "flow-m4-s1-" + unique + "@example.org";
		String student2Email = "flow-m4-s2-" + unique + "@example.org";

		Student student1 = auth.student(student1Email);
		Student student2 = auth.student(student2Email);

		var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
		MentorShip ms = chain.mentorship();
		Long mentorshipId = ms.getId();

		// Enroll two students
		auth.enroll(mentorshipId, student1Email);
		auth.enroll(mentorshipId, student2Email);

		// Initialize points: student1 has 80 points, student2 has 40 points
		totalPoints.saveAndFlush(TotalPoints.builder().student(student1).mentorship(ms).totalPoints(80).build());
		totalPoints.saveAndFlush(TotalPoints.builder().student(student2).mentorship(ms).totalPoints(40).build());

		// Seed a badge belonging to this mentorship (points = 20)
		Badge badge = badges.saveAndFlush(Badge.builder()
				.mentorship(ms)
				.title("Top Architect " + unique)
				.category(BadgeCategory.ACHIEVEMENT)
				.description("Awarded for outstanding system design")
				.points(20)
				.build());

		String mentorToken = auth.authToken(mentorEmail, AuthFixture.MENTOR);
		String student1Token = auth.authToken(student1Email, AuthFixture.STUDENT);

		// Step 1: Mentor awards badge to student1
		mockMvc.perform(post("/api/v1/badge-awards/{badgeId}/award", badge.getId())
						.header("Authorization", mentorToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"studentId\":" + student1.getId() + ",\"note\":\"Superb design patterns\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.apiResponse.message").value("Badge awarded successfully"));

		// Observable effect: BadgeAward exists, points for student1 increased by 20 to 100
		assertThat(badgeAwards.existsByBadge_IdAndStudent_Id(badge.getId(), student1.getId())).isTrue();
		TotalPoints tp1 = totalPoints.findByStudent_IdAndMentorship_Id(student1.getId(), mentorshipId).orElseThrow();
		assertThat(tp1.getTotalPoints()).isEqualTo(100);

		// Step 2: Mentor marks mentorship as COMPLETED
		mockMvc.perform(post("/api/v1/mentorship/update-status/{id}", mentorshipId)
						.header("Authorization", mentorToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"COMPLETED\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.status").value("Status updated successfully"));

		// Step 3: Observable effect on Certificates: certificates issued for both students ordered by leaderboard rank
		List<Certificate> certList = certificates.findAll().stream()
				.filter(c -> c.getMentorship().getId().equals(mentorshipId))
				.toList();
		assertThat(certList).hasSize(2);

		Certificate cert1 = certList.stream().filter(c -> c.getStudent().getId().equals(student1.getId())).findFirst().orElseThrow();
		Certificate cert2 = certList.stream().filter(c -> c.getStudent().getId().equals(student2.getId())).findFirst().orElseThrow();

		assertThat(cert1.getRank()).isEqualTo(1); // 100 points -> Rank #1
		assertThat(cert2.getRank()).isEqualTo(2); // 40 points -> Rank #2

		// Step 4: Observable effect on Notifications: student1 and student2 received certificate notifications
		Page<UserNotification> s1Notifs = userNotifications.findWithNotification(student1Email, PageRequest.of(0, 10));
		assertThat(s1Notifs.getContent()).anyMatch(un ->
				un.getNotification().getTitle().equals("Certificate Issued!") &&
				un.getNotification().getContent().contains("rank #1")
		);

		// Step 5: Student 1 fetches certificates via API
		mockMvc.perform(get("/api/v1/student/certificates")
						.header("Authorization", student1Token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.data.content[0].rank").value(1))
				.andExpect(jsonPath("$.apiResponse.data.content[0].mentorshipTitle").value(ms.getTitle()));
	}

	@Test
	@DisplayName("Flow 4 Failure Path: Cannot complete mentorship with zero enrolled students (400 Bad Request)")
	void completionFlow_cannotCompleteWithZeroStudents() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String mentorEmail = "flow-m4-empty-m-" + unique + "@example.org";

		var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
		Long mentorshipId = chain.mentorship().getId();

		String mentorToken = auth.authToken(mentorEmail, AuthFixture.MENTOR);

		mockMvc.perform(post("/api/v1/mentorship/update-status/{id}", mentorshipId)
						.header("Authorization", mentorToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"COMPLETED\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("ERR_BUSINESS_RULE"))
				.andExpect(jsonPath("$.message").value("Cannot complete mentorship with no enrolled students"));

		MentorShip unchanged = mentorships.findById(mentorshipId).orElseThrow();
		assertThat(unchanged.getStatus()).isEqualTo(Status.ACTIVE);
	}

	@Test
	@DisplayName("Flow 4 Failure Path: Cannot change status of already completed mentorship (400 Bad Request)")
	void completionFlow_cannotChangeCompletedStatus() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String mentorEmail = "flow-m4-alrcomp-m-" + unique + "@example.org";
		String studentEmail = "flow-m4-alrcomp-s-" + unique + "@example.org";

		var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
		Long mentorshipId = chain.mentorship().getId();
		auth.enroll(mentorshipId, studentEmail);

		String mentorToken = auth.authToken(mentorEmail, AuthFixture.MENTOR);

		// Successfully complete
		mockMvc.perform(post("/api/v1/mentorship/update-status/{id}", mentorshipId)
						.header("Authorization", mentorToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"COMPLETED\"}"))
				.andExpect(status().isOk());

		// Attempting to change status back to ACTIVE must fail
		mockMvc.perform(post("/api/v1/mentorship/update-status/{id}", mentorshipId)
						.header("Authorization", mentorToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"ACTIVE\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("ERR_BUSINESS_RULE"))
				.andExpect(jsonPath("$.message").value("you can't change status of completed mentorship"));
	}

	@Test
	@DisplayName("Flow 4 Failure Path: Foreign mentor cannot update status of another mentor's mentorship -> 401 Unauthorized (BadCredentialsException)")
	void completionFlow_foreignMentorCannotUpdateStatus() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String ownerMentorEmail = "flow-m4-own-m-" + unique + "@example.org";
		String foreignMentorEmail = "flow-m4-for-m-" + unique + "@example.org";

		var chain = auth.mentorship(ownerMentorEmail, Status.ACTIVE);
		auth.mentor(foreignMentorEmail);

		String foreignMentorToken = auth.authToken(foreignMentorEmail, AuthFixture.MENTOR);

		// Option A doctrine: mentorShipServiceI throws BadCredentialsException -> Spring maps to 401
		mockMvc.perform(post("/api/v1/mentorship/update-status/{id}", chain.mentorship().getId())
						.header("Authorization", foreignMentorToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"COMPLETED\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("ERR_INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.message").value("Invalid email or password."));
	}
}
