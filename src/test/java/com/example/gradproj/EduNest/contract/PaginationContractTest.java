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
 * P2-06 / EB-40 — API contract test: pagination (API-02 expectations).
 *
 * Endpoint: {@code GET /api/v1/mentorship/explore} — PUBLIC (PublicRoutesPolicy),
 * canonical explore surface; pagination math is the API-wide convention
 * (raw {@code int page}/{@code int size} params → {@code PageRequest.of} at the
 * service, {@code mds/api/MDS.md} pagination row). This suite compiles the
 * pagination CONTRACT as tests so every controller inherits it implicitly.
 *
 * As-coded findings (pinned 2026-11-02, reconciliation = documentation, since
 * the cap fix is deliberately phase-4 scope — API-02 maps to P4-09):
 * <ul>
 *   <li>{@code size=0} and {@code size=-1} → {@code AbstractPageRequest} throws
 *       IllegalArgumentException → catch-all → <b>500 ERR_INTERNAL_SYSTEM</b>
 *       (ugly: a client paging bug is an "internal system error"; the eventual
 *       cap/clamp contract is P4-09).</li>
 *   <li>{@code page=-1} → same throw → 500 (page index must be ≥ 0).</li>
 *   <li>{@code size=10000} → accepted unboundedly (no global cap — API-02 /
 *       USR-04, fix task P4-09).</li>
 *   <li>beyond-range {@code page} → <b>200</b> with an EMPTY result — page
 *       exhaustion is a 200 contract, NOT 404.</li>
 * </ul>
 */
@Tag("critical")
class PaginationContractTest extends ProdChainIntegrationTestBase {

	private static final String EXPLORE = "/api/v1/mentorship/explore";

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

	@ParameterizedTest(name = "P2-06/API-02: nonpositive size={0} → as-coded 500 ERR_INTERNAL_SYSTEM (no clamp; cap/clamp contract = P4-09)")
	@ValueSource(strings = {"0", "-1"})
	void nonpositiveSize_internalError(String size) throws Exception {
		mockMvc.perform(get(EXPLORE).param("page", "0").param("size", size))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.errorCode").value("ERR_INTERNAL_SYSTEM"))
				.andExpect(jsonPath("$.refId").exists());
	}

	@Test
	@DisplayName("P2-06/API-02: page=-1 → as-coded 500 ERR_INTERNAL_SYSTEM (index < 0 unguarded)")
	void negativePage_internalError() throws Exception {
		mockMvc.perform(get(EXPLORE).param("page", "-1").param("size", "5"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.errorCode").value("ERR_INTERNAL_SYSTEM"))
				.andExpect(jsonPath("$.refId").exists());
	}

	@Test
	@DisplayName("P2-06/API-02: size=10000 accepted unboundedly → 200 (no global cap — API-02/USR-04, fix = P4-09)")
	void hugeSize_todayUnbounded() throws Exception {
		mockMvc.perform(get(EXPLORE).param("page", "0").param("size", "10000"))
				.andExpect(status().isOk());
	}
}
