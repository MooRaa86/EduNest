package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.badges.Badge;
import com.example.gradproj.EduNest.entity.badges.BadgeAward;
import com.example.gradproj.EduNest.enums.badge.BadgeCategory;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.badges.BadgeAwardRepository;
import com.example.gradproj.EduNest.repository.badges.BadgeRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-19 / EB-26 — badge-award read scoping probes (G11 / GAME-02).
 *
 * Contract: {@code GET /api/v1/badge-awards/student/{studentId}} reveals
 * personal data (who earned what, award notes) and was enumerable by
 * incrementing ids with ZERO scoping. Now scoped to: ADMIN, the student
 * themself (principal-derived email), or a mentor of one of the student's
 * enrollments. Everyone else → 403; anonymous → 401 (chain).
 *
 * Audited, no change needed (EB-26):
 * <ul>
 *   <li>{@code /mentorship/{id}} and {@code /badge/{id}} award reads were
 *       already confined to the owner-mentor (403 via getCurrentMentor).</li>
 *   <li>Badge CATALOG reads ({@code /api/v1/badges/**} — title/category/
 *       description/points) stay open per the P1-19 approach note: no
 *       personal data.</li>
 * </ul>
 */
@Tag("critical")
class BadgeAwardScopingProbesTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private BadgeRepository badges;
	@Autowired private BadgeAwardRepository awardRepo;
	@Autowired private StudentRepository studentRepo;

	private static final String MENTOR = "eb26-mentor@example.org";
	private static final String ENROLLED = "eb26-student@example.org";
	private static final String STRANGER = "eb26-stranger@example.org";
	private static final String ADMIN = "eb26-admin@example.org";
	private static final String OTHER_MENTOR = "eb26-other-mentor@example.org";

	private Long studentRowId;
	private Long badgeId;
	private String awardNote;

	@BeforeEach
	void seed() throws Exception {
		var chain = auth.mentorship(MENTOR, Status.ACTIVE);
		auth.enroll(chain.mentorship().getId(), ENROLLED);
		auth.student(STRANGER);
		auth.admin(ADMIN);
		auth.mentor(OTHER_MENTOR); // a mentor with NO tie to this student

		studentRowId = studentRepo.findByEmail(ENROLLED).orElseThrow().getId();

		Badge badge = badges.saveAndFlush(Badge.builder()
				.mentorship(chain.mentorship()).title("eb26 badge")
				.category(BadgeCategory.ACHIEVEMENT).description("eb26").points(50).build());
		badgeId = badge.getId();
		awardNote = "eb26-award-note-" + System.nanoTime();
		awardRepo.saveAndFlush(BadgeAward.builder()
				.badge(badge)
				.student(studentRepo.findByEmail(ENROLLED).orElseThrow())
				.awardedBy(chain.mentor())
				.note(awardNote)
				.build());
	}

	@Test
	@DisplayName("EB26/[1] anonymous → 401 (route is chain-authenticated)")
	void anonymous_unauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/badge-awards/student/" + studentRowId))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("EB26/[2] the student themself → 200 with their own award")
	void selfRead_ok() throws Exception {
		var result = mockMvc.perform(get("/api/v1/badge-awards/student/" + studentRowId)
						.header("Authorization", auth.authToken(ENROLLED, AuthFixture.STUDENT)))
				.andExpect(status().isOk()).andReturn();
		String body = result.getResponse().getContentAsString();
		org.assertj.core.api.Assertions.assertThat(body)
				.contains("eb26 badge").contains(awardNote);
	}

	@Test
	@DisplayName("EB26/[3] a DIFFERENT student (id enumeration) → 403")
	void strangerStudent_forbidden() throws Exception {
		mockMvc.perform(get("/api/v1/badge-awards/student/" + studentRowId)
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("EB26/[4] mentor of one of the student's enrollments → 200")
	void mentorOfEnrollment_ok() throws Exception {
		var result = mockMvc.perform(get("/api/v1/badge-awards/student/" + studentRowId)
						.header("Authorization", auth.authToken(MENTOR, AuthFixture.MENTOR)))
				.andExpect(status().isOk()).andReturn();
		String body = result.getResponse().getContentAsString();
		org.assertj.core.api.Assertions.assertThat(body).contains("eb26 badge");
	}

	@Test
	@DisplayName("EB26/[5] mentor with NO enrollment tie to the student → 403")
	void unlinkedMentor_forbidden() throws Exception {
		mockMvc.perform(get("/api/v1/badge-awards/student/" + studentRowId)
						.header("Authorization", auth.authToken(OTHER_MENTOR, AuthFixture.MENTOR)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("EB26/[6] ADMIN → 200")
	void admin_ok() throws Exception {
		mockMvc.perform(get("/api/v1/badge-awards/student/" + studentRowId)
						.header("Authorization", auth.authToken(ADMIN, AuthFixture.ADMIN)))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("EB26/[7] nonexistent student id → 403 (no existence oracle)")
	void nonexistentId_forbidden() throws Exception {
		mockMvc.perform(get("/api/v1/badge-awards/student/999999999")
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("EB26/[8] regression: owner-only award reads stay confined (stranger → 403)")
	void ownerOnlyReads_stayConfined() throws Exception {
		// a STUDENT attacker never passes getCurrentMentor() regardless of id
		mockMvc.perform(get("/api/v1/badge-awards/badge/" + badgeId)
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden());
	}
}
