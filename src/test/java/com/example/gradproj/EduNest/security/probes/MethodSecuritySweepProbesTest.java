package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.chat.ChatRoomRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-10 / EB-17 — method-security &amp; ownership sweep probes (SEL-09 + TD-07/G12).
 *
 * Role-matrix IT for the two families this ticket closed:
 *
 * <ul>
 *   <li><b>G12 (TD-07)</b> — {@code POST /api/v1/chat-room/create/{mentorshipId}}
 *       carried only a MENTOR role gate; {@code createRoom} verified
 *       visibility, never ownership, so any mentor could create a room inside
 *       ANY mentorship. Now the owner check
 *       ({@code SecurityService#isMentorOwnMentorship → existsByIdAndMentor_Email})
 *       answers 403 for foreign AND non-existent ids — one uniform denial, no
 *       id-space oracle. Foreign denial must persist NOTHING.</li>
 *   <li><b>SEL-09 (dashboard)</b> — {@code /api/v1/dashboard/**} sits behind
 *       "authenticated" URL rules only; the role gates live on the service
 *       methods, and {@code getStats} / {@code getSalesChartData} /
 *       {@code getStudents} had none — any authenticated user could reach
 *       mentor money/roster/stat reads. All three now enforce
 *       {@code hasRole('MENTOR')} like their siblings.</li>
 *   <li><b>SEL-09 (admin badges)</b> — all seven {@code AdminBadgeController}
 *       routes (including {@code GET /api/users/{userId}/badges}) had NO method
 *       security and relied on the URL matrix; a class-level
 *       {@code hasRole('ADMIN')} now mirrors it.</li>
 * </ul>
 */
@Tag("critical")
class MethodSecuritySweepProbesTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private ChatRoomRepository roomRepo;
	@Autowired private StudentRepository studentRepo;

	private static final String OWNER = "eb17-owner-mentor@example.org";
	private static final String FOREIGN = "eb17-foreign-mentor@example.org";
	private static final String STUDENT = "eb17-student@example.org";
	private static final String ADMIN = "eb17-admin@example.org";

	private Long mentorshipId;
	private Long studentId;

	@BeforeEach
	void seed() {
		var chain = auth.mentorship(OWNER, Status.ACTIVE);
		mentorshipId = chain.mentorship().getId();
		auth.enroll(mentorshipId, STUDENT);
		auth.mentor(FOREIGN);
		auth.admin(ADMIN);
		studentId = studentRepo.findByEmail(STUDENT).orElseThrow().getId();
	}

	/* ---------- G12 / TD-07: room creation ownership ---------- */

	@Test
	@DisplayName("EB17/[1] G12: FOREIGN mentor-room create → 403, no room row persisted")
	void foreignMentor_roomCreate_forbidden() throws Exception {
		long before = roomRepo.count();
		mockMvc.perform(post("/api/v1/chat-room/create/" + mentorshipId)
						.header("Authorization", auth.authToken(FOREIGN, AuthFixture.MENTOR))
						.contentType("application/json")
						.content("{\"name\":\"foreign room attempt\"}"))
				.andExpect(status().isForbidden());
		assertThat(roomRepo.count()).isEqualTo(before);
	}

	@Test
	@DisplayName("EB17/[2] G12: owner mentor-room create → 200 and the row IS persisted")
	void ownerMentor_roomCreate_ok() throws Exception {
		long before = roomRepo.count();
		mockMvc.perform(post("/api/v1/chat-room/create/" + mentorshipId)
						.header("Authorization", auth.authToken(OWNER, AuthFixture.MENTOR))
						.contentType("application/json")
						.content("{\"name\":\"owner room\"}"))
				.andExpect(status().isOk());
		assertThat(roomRepo.count()).isEqualTo(before + 1);
	}

	@Test
	@DisplayName("EB17/[3] G12: non-existent mentorship id → 403 (uniform denial — no id-space oracle)")
	void unknownMentorship_roomCreate_forbidden() throws Exception {
		mockMvc.perform(post("/api/v1/chat-room/create/99999999")
						.header("Authorization", auth.authToken(FOREIGN, AuthFixture.MENTOR))
						.contentType("application/json")
						.content("{\"name\":\"probe\"}"))
				.andExpect(status().isForbidden());
	}

	/* ---------- SEL-09: dashboard role gaps ---------- */

	@Test
	@DisplayName("EB17/[4] SEL-09: student → 403 on GET /api/v1/dashboard/students (roster read)")
	void student_dashboardStudents_forbidden() throws Exception {
		mockMvc.perform(get("/api/v1/dashboard/students")
						.header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("EB17/[5] SEL-09: student → 403 on GET /api/v1/dashboard/revenue-chart (money read)")
	void student_dashboardRevenueChart_forbidden() throws Exception {
		mockMvc.perform(get("/api/v1/dashboard/revenue-chart")
						.header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("EB17/[6] SEL-09: student (even ENROLLED in this mentorship) → 403 on GET /{id}/stats")
	void student_dashboardStats_forbidden() throws Exception {
		mockMvc.perform(get("/api/v1/dashboard/" + mentorshipId + "/stats")
						.header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("EB17/[7] SEL-09: owner mentor (control) → 200 on GET /api/v1/dashboard/students")
	void mentor_dashboardStudents_ok() throws Exception {
		mockMvc.perform(get("/api/v1/dashboard/students")
						.header("Authorization", auth.authToken(OWNER, AuthFixture.MENTOR)))
				.andExpect(status().isOk());
	}

	/* ---------- SEL-09: admin badge surface ---------- */

	@Test
	@DisplayName("EB17/[8] SEL-09: student badge catalog create (POST /api/admin/badges) → 403")
	void student_badgeCreate_forbidden() throws Exception {
		mockMvc.perform(post("/api/admin/badges")
						.header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT))
						.contentType("application/json")
						.content("{\"name\":\"eb17 badge\",\"description\":\"eb17 sweep\",\"type\":\"INNOVATOR_AWARD\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("EB17/[9] admin badge catalog create (control) → 201")
	void admin_badgeCreate_created() throws Exception {
		mockMvc.perform(post("/api/admin/badges")
						.header("Authorization", auth.authToken(ADMIN, AuthFixture.ADMIN))
						.contentType("application/json")
						.content("{\"name\":\"eb17 badge\",\"description\":\"eb17 sweep\",\"type\":\"INNOVATOR_AWARD\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("EB17/[10] SEL-09: student → 403 on GET /api/users/{id}/badges (URL-admin route, now also method-gated)")
	void student_userBadgesRead_forbidden() throws Exception {
		mockMvc.perform(get("/api/users/" + studentId + "/badges")
						.header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("EB17/[11] admin → 200 on GET /api/admin/badges (control parity)")
	void admin_badgeList_ok() throws Exception {
		mockMvc.perform(get("/api/admin/badges")
						.header("Authorization", auth.authToken(ADMIN, AuthFixture.ADMIN)))
				.andExpect(status().isOk());
	}
}
