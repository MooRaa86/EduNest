package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-15 / EB-22 — DRAFT mentorship confinement probes (MSH-01, G8, D-02 (a)).
 *
 * Contract: a DRAFT mentorship is visible ONLY to its owner-mentor and ADMIN,
 * on every read surface; everyone else gets a not-found/confined response —
 * no existence or status leak.
 *
 * Route-security reality (asserted here as the actual chain behavior):
 * <ul>
 *   <li>{@code GET /api/v1/mentorship/{id}} and the paged overview
 *       {@code GET /api/v1/mentorship} are NOT public — anonymous callers are
 *       rejected by the chain (401) BEFORE the service runs; the D-02 guard
 *       then confines every AUTHENTICATED non-owner to 404.</li>
 *   <li>{@code GET /api/v1/student/mentorships/{id}/overview} IS
 *       permitAll — this is the genuinely anonymous surface, and here the
 *       anonymous response must be the 404 shape (criterion: "DRAFT invisible
 *       (404) for non-owner everywhere incl. public overview").</li>
 *   <li>Explore/categories/top-mentorships/recommendations are already
 *       ACTIVE-only in the repository (verified during recon) — regression
 *       probed below so a future query edit can't silently regress.</li>
 * </ul>
 */
@Tag("critical")
class MentorshipDraftConfinementProbesTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private MentorShipRepository mentorships;

	private static final String OWNER = "eb22-owner@example.org";
	private static final String MENTOR = "eb22-other-mentor@example.org";
	private static final String STUDENT = "eb22-student@example.org";
	private static final String ADMIN = "eb22-admin@example.org";

	private Long draftId;
	private Long activeId;
	private String draftTitle;
	private String activeTitle;

	@BeforeEach
	void seed() throws Exception {
		// owner mentor + their DRAFT and ACTIVE mentorships
		var chain = auth.mentorship(OWNER, Status.DRAFT);
		draftId = chain.mentorship().getId();
		draftTitle = chain.mentorship().getTitle(); // "Probe DRAFT <nano>" — unique
		activeId = auth.mentorship(OWNER, Status.ACTIVE).mentorship().getId();
		activeTitle = mentorships.findById(activeId).orElseThrow().getTitle();

		auth.mentorship(MENTOR, Status.ACTIVE); // other mentor, no relation
		auth.student(STUDENT);
		auth.admin(ADMIN);
	}

	/* ---------- 1. detail: GET /api/v1/mentorship/{id} ---------- */

	@Test
	@DisplayName("EB22/[1] DRAFT detail: anonymous → 401 (chain), STUDENT → 404, other MENTOR → 404")
	void draftDetail_nonOwner_confined() throws Exception {
		String route = "/api/v1/mentorship/" + draftId;
		mockMvc.perform(get(route)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(route).header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get(route).header("Authorization", auth.authToken(MENTOR, AuthFixture.MENTOR)))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("EB22/[2] DRAFT detail: owner-mentor → 200, ADMIN → 200")
	void draftDetail_ownerAndAdmin_allowed() throws Exception {
		String route = "/api/v1/mentorship/" + draftId;
		mockMvc.perform(get(route).header("Authorization", auth.authToken(OWNER, AuthFixture.MENTOR)))
				.andExpect(status().isOk());
		mockMvc.perform(get(route).header("Authorization", auth.authToken(ADMIN, AuthFixture.ADMIN)))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("EB22/[3] ACTIVE detail: authed student → 200 (public-to-authed behavior unchanged)")
	void activeDetail_unchanged() throws Exception {
		mockMvc.perform(get("/api/v1/mentorship/" + activeId)
						.header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT)))
				.andExpect(status().isOk());
	}

	/* ---------- 2. student overview (public route): GET /api/v1/student/mentorships/{id}/overview ---------- */

	@Test
	@DisplayName("EB22/[4] DRAFT student overview: anonymous → 404, STUDENT → 404 (no existence leak)")
	void draftStudentOverview_anonAndStudent_404() throws Exception {
		String route = "/api/v1/student/mentorships/" + draftId + "/overview";
		mockMvc.perform(get(route)).andExpect(status().isNotFound());
		mockMvc.perform(get(route).header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT)))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("EB22/[5] DRAFT student overview: owner-mentor → 200, ADMIN → 200")
	void draftStudentOverview_ownerAndAdmin_allowed() throws Exception {
		String route = "/api/v1/student/mentorships/" + draftId + "/overview";
		mockMvc.perform(get(route).header("Authorization", auth.authToken(OWNER, AuthFixture.MENTOR)))
				.andExpect(status().isOk());
		mockMvc.perform(get(route).header("Authorization", auth.authToken(ADMIN, AuthFixture.ADMIN)))
				.andExpect(status().isOk());
	}

	/* ---------- 3. paged overview: GET /api/v1/mentorship ---------- */

	@Test
	@DisplayName("EB22/[6] paged overview excludes DRAFT for authed students (anonymous → 401 chain)")
	void overviewListing_excludesDraft() throws Exception {
		mockMvc.perform(get("/api/v1/mentorship?page=0&size=50"))
				.andExpect(status().isUnauthorized());

		String body = mockMvc.perform(
						get("/api/v1/mentorship?page=0&size=50")
								.header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain(draftTitle).contains(activeTitle);
	}

	/* ---------- 4. regression: ACTIVE-only surfaces stay DRAFT-free ---------- */

	@Test
	@DisplayName("EB22/[7] explore keyword search returns only ACTIVE mentorships")
	void explore_activeOnly() throws Exception {
		// unique-titled pair on the same mentor so the keyword matches only one
		String suffix = String.valueOf(System.nanoTime());
		var mentor = mentorships.findById(activeId).orElseThrow().getMentor();
		MentorShip draftSearch = MentorShip.builder()
				.title("eb22-draft-" + suffix).subtitle("probe").description("probe")
				.category("backEnd").price(0.0).duration(1.0).status(Status.DRAFT).mentor(mentor).build();
		MentorShip activeSearch = MentorShip.builder()
				.title("eb22-active-" + suffix).subtitle("probe").description("probe")
				.category("backEnd").price(0.0).duration(1.0).status(Status.ACTIVE).mentor(mentor).build();
		mentorships.saveAndFlush(draftSearch);
		mentorships.saveAndFlush(activeSearch);

		String body = mockMvc.perform(get("/api/v1/mentorship/explore?keyword=eb22-active-" + suffix))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(body).contains("eb22-active-" + suffix).doesNotContain("eb22-draft-" + suffix);
	}

	@Test
	@DisplayName("EB22/[8] top-by-mentor (authed surface) returns only ACTIVE mentorships")
	void topByMentor_activeOnly() throws Exception {
		String body = mockMvc.perform(get("/api/v1/student/mentorships/mentor/" + OWNER + "/top-mentorships")
						.header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain(draftTitle).contains(activeTitle);
	}
}
