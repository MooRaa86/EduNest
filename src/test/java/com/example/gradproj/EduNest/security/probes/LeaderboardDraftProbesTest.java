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
	 * G8 — DRAFT mentorship detail confinement (FLIPPED 2026-11-01, EB-22).
	 * Was (Option A pin): 200 — stranger saw an unpublished mentorship detail.
	 * Now: D-02 default (a) gates DRAFT to owner-mentor/ADMIN; every other
	 * authenticated caller gets 404 (no existence leak) via
	 * {@code JwtChannelInterceptor}-independent guard in getMentorShipById.
	 */
	@Test
	@DisplayName("G8/confined: stranger opens a DRAFT (unpublished) mentorship detail → 404")
	void g8_strangerReadsDraftMentorshipDetail() throws Exception {
		auth.student(OUTSIDER); // token-holder must exist in DB (validator checks users table)
		var chain = auth.mentorship("probe-draft-mentor@example.org",
				com.example.gradproj.EduNest.enums.mentorShip.Status.DRAFT);
		Long mid = chain.mentorship().getId();

		mockMvc.perform(get("/api/v1/mentorship/{id}", mid)
						.header("Authorization", auth.authToken(OUTSIDER, AuthFixture.STUDENT)))
				.andExpect(status().isNotFound());
	}

	/**
	 * G8b — the PUBLIC student-overview path (FLIPPED 2026-11-01, EB-22).
	 * Was (Option A pin): anonymous callers could read DRAFT details
	 * (findMentorshipDetailsById filtered only by id).
	 * Now: 404 — D-02 default (a) confines DRAFT to owner-mentor/ADMIN on the
	 * permitAll route too.
	 */
	@Test
	@DisplayName("G8b/confined: anonymous caller reads a DRAFT mentorship overview → 404")
	void g8b_anonymousReadsDraftMentorshipOverview() throws Exception {
		var chain = auth.mentorship("probe-draft-mentor2@example.org",
				com.example.gradproj.EduNest.enums.mentorShip.Status.DRAFT);

		mockMvc.perform(get("/api/v1/student/mentorships/{mid}/overview", chain.mentorship().getId()))
				.andExpect(status().isNotFound());
	}
}
