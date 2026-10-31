package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-03 / EB-37 — G7/G8: leaderboard & DRAFT mentorship confinement.
 */
@Tag("critical")
class LeaderboardDraftProbesTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private TotalPointsRepository totalPoints;

	private static final String ENROLLED_STUDENT = "probe-lb-enrolled@example.org";
	private static final String OUTSIDER = "probe-lb-outsider@example.org";

	/** G7 / EB-23: an unenrolled student cannot access another course leaderboard. */
	@Test
	@DisplayName("G7: un-enrolled student is denied the foreign leaderboard")
	void g7_outsiderCannotSeeForeignLeaderboard() throws Exception {
		auth.student(OUTSIDER); // token-holder must exist in DB (validator checks users table)
		var chain = auth.mentorship("probe-lb-mentor@example.org",
				com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
		auth.enroll(chain.mentorship().getId(), ENROLLED_STUDENT);
		totalPoints.saveAndFlush(TotalPoints.builder()
				.student(auth.student(ENROLLED_STUDENT))
				.mentorship(chain.mentorship()).totalPoints(42).build());

		mockMvc.perform(get("/api/v1/student/mentorships/{mid}/leaderboard", chain.mentorship().getId())
						.header("Authorization", auth.authToken(OUTSIDER, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden());
	}

	/**
	 * G8 — DRAFT mentorship detail readable directly.
	 * Audit: getMentorShipById has no status filter.
	 * EXPECT TODAY: 200 (stranger sees an unpublished mentorship).
	 * FLIPS WITH: [P1-15] DRAFT confinement (per ISSUE-MAPPING) → expect 404/403 then.
	 */
	@Test
	@DisplayName("G8/todayOpen: stranger opens a DRAFT (unpublished) mentorship detail → 200")
	void g8_strangerReadsDraftMentorshipDetail() throws Exception {
		auth.student(OUTSIDER); // token-holder must exist in DB (validator checks users table)
		var chain = auth.mentorship("probe-draft-mentor@example.org",
				com.example.gradproj.EduNest.enums.mentorShip.Status.DRAFT);
		Long mid = chain.mentorship().getId();

		mockMvc.perform(get("/api/v1/mentorship/{id}", mid)
						.header("Authorization", auth.authToken(OUTSIDER, AuthFixture.STUDENT)))
				.andExpect(status().isOk());
	}

	/**
	 * G8b — the PUBLIC overview path serves DRAFT mentorship data to ANONYMOUS callers
	 * (query `findMentorshipDetailsById` filters only by id).
	 * EXPECT TODAY: not-401/403 (public) and reachable for a DRAFT.
	 * FLIPS WITH: [P1-15] → 404 then.
	 */
	@Test
	@DisplayName("G8b/todayOpen: anonymous caller reads a DRAFT mentorship overview → public")
	void g8b_anonymousReadsDraftMentorshipOverview() throws Exception {
		var chain = auth.mentorship("probe-draft-mentor2@example.org",
				com.example.gradproj.EduNest.enums.mentorShip.Status.DRAFT);

		mockMvc.perform(get("/api/v1/student/mentorships/{mid}/overview", chain.mentorship().getId()))
				.andExpect(status().is2xxSuccessful());
	}
}
