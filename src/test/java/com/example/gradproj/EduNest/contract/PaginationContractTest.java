package com.example.gradproj.EduNest.contract;

import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P4-09 / EB-58 — API contract test: pagination (API-02 expectations).
 *
 * Endpoint: {@code GET /api/v1/mentorship/explore} — PUBLIC (PublicRoutesPolicy),
 * canonical explore surface; pagination math is the API-wide convention
 * (raw {@code int page}/{@code int size} params → {@code utils.PaginationPolicy}
 * at the service, {@code mds/api/MDS.md} pagination row). This suite compiles the
 * pagination CONTRACT as tests so every controller inherits it implicitly.
 *
 * P4-09 clamped contract (supersedes the EB-40 as-coded quirks):
 * <ul>
 *   <li>{@code size<=0} → clamped to 1 → 200 (was: 500 ERR_INTERNAL_SYSTEM).</li>
 *   <li>{@code page<0} → clamped to 0 → 200 (was: 500).</li>
 *   <li>{@code size>50} → clamped to 50 → 200 (was: accepted unboundedly).</li>
 *   <li>beyond-range {@code page} → <b>200</b> with an EMPTY result — page
 *       exhaustion is a 200 contract, NOT 404 (unchanged).</li>
 *   <li>defaults (no params) → page=0, size=5 (unchanged).</li>
 * </ul>
 * Malformed numeric pagination params (e.g. size=abc) → 400 ERR_VALIDATION,
 * pinned by {@code MalformedPaginationContractTest}.
 */
@Tag("critical")
class PaginationContractTest extends ProdChainIntegrationTestBase {

	private static final String EXPLORE = "/api/v1/mentorship/explore";
	private static final int GLOBAL_MAX_PAGE_SIZE = 50;

	@Test
	@DisplayName("P2-06/API-02: defaults (no page/size params) → 200 (documented defaults page=0, size=5)")
	void defaultParams_ok() throws Exception {
		mockMvc.perform(get(EXPLORE)).andExpect(status().isOk());
	}

	@Test
	@DisplayName("P2-06/API-02: page beyond data range → 200 (page exhaustion is a 200 contract, not 404)")
	void beyondRangePage_ok200() throws Exception {
		mockMvc.perform(get(EXPLORE).param("page", "9999").param("size", "10"))
				.andExpect(status().isOk());
	}

	@ParameterizedTest(name = "P4-09/API-02: nonpositive size={0} → clamped to 1 → 200 (was EB-40 500)")
	@ValueSource(strings = {"0", "-1"})
	void nonpositiveSize_clampedToOne(String size) throws Exception {
		mockMvc.perform(get(EXPLORE).param("page", "0").param("size", size))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.mentorShips.size").value(1))
				.andExpect(jsonPath("$.apiResponse.mentorShips.page").value(0));
	}

	@Test
	@DisplayName("P4-09/API-02: page=-1 → clamped to 0 → 200 (was EB-40 500)")
	void negativePage_clampedToZero() throws Exception {
		mockMvc.perform(get(EXPLORE).param("page", "-1").param("size", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.mentorShips.page").value(0))
				.andExpect(jsonPath("$.apiResponse.mentorShips.size").value(5));
	}

	@Test
	@DisplayName("P4-09/API-02: size=10000 → clamped to global cap 50 → 200 (was unbounded)")
	void hugeSize_clampedToCap() throws Exception {
		mockMvc.perform(get(EXPLORE).param("page", "0").param("size", "10000"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.mentorShips.size").value(GLOBAL_MAX_PAGE_SIZE));
	}
}
