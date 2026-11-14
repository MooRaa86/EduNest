package com.example.gradproj.EduNest.contract;

import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P4-09 / EB-58 — malformed-parameter contract (D-26 + API-05 extension).
 *
 * Before this ticket, {@code ?page=abc} / {@code ?size=abc} fell into the
 * catch-all as a sanitized 500 ERR_INTERNAL_SYSTEM — a client paging typo
 * reported as an internal system error. PO-approved contract: malformed NUMERIC
 * PAGINATION parameters → 400 ERR_VALIDATION with the standard {@code ApiError}
 * envelope and the offending parameter under {@code fields}.
 *
 * Isolation is by construction (exact compile-time param-name set + numeric
 * target type); every other type-mismatch keeps today's sanitized-500 shape,
 * pinned here with two probes:
 * <ul>
 *   <li>{@code minPrice=abc} on explore (non-pagination Double param) → 500.</li>
 *   <li>{@code status=BOGUS} on the mentor student-projects surface (enum
 *       param) → 500.</li>
 * </ul>
 */
@Tag("critical")
class MalformedPaginationContractTest extends ProdChainIntegrationTestBase {

	private static final String EXPLORE = "/api/v1/mentorship/explore";

	@Autowired private AuthFixture auth;

	@Test
	@DisplayName("P4-09: explore page=abc → 400 ERR_VALIDATION with fields.page (was catch-all 500)")
	void malformedPage_explore_badRequest() throws Exception {
		mockMvc.perform(get(EXPLORE).param("page", "abc").param("size", "5"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("ERR_VALIDATION"))
				.andExpect(jsonPath("$.message").value("Please check the submitted fields."))
				.andExpect(jsonPath("$.fields.page").value("Parameter 'page' must be a valid integer."))
				.andExpect(jsonPath("$.refId").doesNotExist());
	}

	@Test
	@DisplayName("P4-09: explore size=abc → 400 ERR_VALIDATION with fields.size (was catch-all 500)")
	void malformedSize_explore_badRequest() throws Exception {
		mockMvc.perform(get(EXPLORE).param("page", "0").param("size", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("ERR_VALIDATION"))
				.andExpect(jsonPath("$.fields.size").value("Parameter 'size' must be a valid integer."))
				.andExpect(jsonPath("$.refId").doesNotExist());
	}

	@Test
	@DisplayName("P4-09: second surface — mentor student-projects size=abc → 400 ERR_VALIDATION")
	void malformedSize_mentorProjects_badRequest() throws Exception {
		String mentorEmail = "eb58-malformed-mentor@example.org";
		var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
		var student = auth.enroll(chain.mentorship().getId(), "eb58-malformed-student@example.org").getStudent();

		mockMvc.perform(get("/profile/students/students/{studentId}/projects", student.getPublicId())
						.header("Authorization", auth.authToken(mentorEmail, AuthFixture.MENTOR))
						.param("size", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("ERR_VALIDATION"))
				.andExpect(jsonPath("$.fields.size").exists())
				.andExpect(jsonPath("$.refId").doesNotExist());
	}

	@Test
	@DisplayName("P4-09 isolation: explore minPrice=abc (non-pagination Double) → sanitized 500 ERR_INTERNAL_SYSTEM unchanged")
	void nonPaginationParam_keepsSanitized500() throws Exception {
		mockMvc.perform(get(EXPLORE).param("minPrice", "abc"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.errorCode").value("ERR_INTERNAL_SYSTEM"))
				.andExpect(jsonPath("$.message").value("An unexpected system error occurred. Please try again."))
				.andExpect(jsonPath("$.refId").exists());
	}

	@Test
	@DisplayName("P4-09 isolation: enum param status=BOGUS → sanitized 500 ERR_INTERNAL_SYSTEM unchanged")
	void enumParam_keepsSanitized500() throws Exception {
		String mentorEmail = "eb58-enum-mentor@example.org";
		var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
		var student = auth.enroll(chain.mentorship().getId(), "eb58-enum-student@example.org").getStudent();

		mockMvc.perform(get("/profile/students/students/{studentId}/projects", student.getPublicId())
						.header("Authorization", auth.authToken(mentorEmail, AuthFixture.MENTOR))
						.param("status", "BOGUS"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.errorCode").value("ERR_INTERNAL_SYSTEM"))
				.andExpect(jsonPath("$.refId").exists());
	}
}
