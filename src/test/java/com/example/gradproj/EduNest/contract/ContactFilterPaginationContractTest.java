package com.example.gradproj.EduNest.contract;

import com.example.gradproj.EduNest.entity.contactus.ContactMessageEntity;
import com.example.gradproj.EduNest.repository.contactus.ContactMessageRepository;
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
 * P4-09 / EB-58 — full pagination contract for
 * {@code GET /api/v1/contact/messages/filter} (admin-only, the ONE endpoint
 * that historically bound a handler {@code Pageable}).
 *
 * Final contract (PO-approved, verified empirically against spring-data-commons
 * 3.5.5: Spring's {@code PageableHandlerMethodArgumentResolver} silently maps
 * {@code size<1} to the default 20 and malformed values to defaults, which
 * violates the approved clamp contract): the controller now binds explicit
 * {@code int page}/{@code int size} params, retains Pageable's sort, and clamps via
 * {@code PaginationPolicy.ofClamped} —
 * <ul>
 *   <li>omitted → documented defaults page=0, size=20 (preserved);</li>
 *   <li>{@code page<0} → 0; {@code size<1} → 1; {@code size>50} → 50;</li>
 *   <li>malformed {@code page=abc}/{@code size=abc} → 400 ERR_VALIDATION with
 *       the standard {@code ApiError} envelope (+ offending field);</li>
 * </ul>
 * The existing Pageable resolver remains responsible for client sort parsing.
 */
@Tag("critical")
class ContactFilterPaginationContractTest extends ProdChainIntegrationTestBase {

	private static final String FILTER_URL = "/api/v1/contact/messages/filter";

	@Autowired private AuthFixture auth;
	@Autowired private ContactMessageRepository contactMessages;

	private String adminToken() {
		String adminEmail = "eb58-contact-admin-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@example.org";
		auth.admin(adminEmail);
		return auth.authToken(adminEmail, AuthFixture.ADMIN);
	}

	private void seedPendingMessages(int count, String tag) {
		for (int i = 0; i < count; i++) {
			contactMessages.saveAndFlush(ContactMessageEntity.builder()
					.name("Guest " + i)
					.email("eb58-" + tag + "-" + i + "@example.org")
					.phone("0101234567" + (i % 10))
					.message("Hello from guest " + i)
					.build());
		}
	}

	@Test
	@DisplayName("P4-09: omitted page/size → documented defaults 0/20 preserved")
	void defaults_unchanged() throws Exception {
		seedPendingMessages(1, "default");
		mockMvc.perform(get(FILTER_URL)
						.header("Authorization", adminToken())
						.param("status", "PENDING"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.Data.page").value(0))
				.andExpect(jsonPath("$.apiResponse.Data.size").value(20));
	}

	@Test
	@DisplayName("P4-09: size=10000 → clamped to global cap 50 → 200")
	void oversizedSize_clampedToCap() throws Exception {
		seedPendingMessages(3, "cap");
		mockMvc.perform(get(FILTER_URL)
						.header("Authorization", adminToken())
						.param("status", "PENDING")
						.param("page", "0")
						.param("size", "10000"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.Data.size").value(50));
	}

	@Test
	@DisplayName("P4-09: size=0 → clamped to 1 (approved contract; resolver default would have returned 20)")
	void zeroSize_clampedToOne() throws Exception {
		seedPendingMessages(3, "zero");
		mockMvc.perform(get(FILTER_URL)
						.header("Authorization", adminToken())
						.param("status", "PENDING")
						.param("size", "0"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.Data.size").value(1));
	}

	@Test
	@DisplayName("P4-09: size=-1 → clamped to 1")
	void negativeSize_clampedToOne() throws Exception {
		seedPendingMessages(3, "negsize");
		mockMvc.perform(get(FILTER_URL)
						.header("Authorization", adminToken())
						.param("status", "PENDING")
						.param("size", "-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.Data.size").value(1));
	}

	@Test
	@DisplayName("P4-09: page=-1 → clamped to page 0 → 200")
	void negativePage_clampedToZero() throws Exception {
		seedPendingMessages(3, "negpage");
		mockMvc.perform(get(FILTER_URL)
						.header("Authorization", adminToken())
						.param("status", "PENDING")
						.param("page", "-1")
						.param("size", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.Data.page").value(0))
				.andExpect(jsonPath("$.apiResponse.Data.size").value(5));
	}

	@Test
	@DisplayName("P4-09: client sort remains effective when size is clamped")
	void sort_isPreserved() throws Exception {
		seedPendingMessages(3, "sort");
		mockMvc.perform(get(FILTER_URL)
						.header("Authorization", adminToken())
						.param("status", "PENDING")
						.param("size", "0")
						.param("sort", "name,desc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.Data.size").value(1))
				.andExpect(jsonPath("$.apiResponse.Data.content[0].name").value("Guest 2"));
	}

	@Test
	@DisplayName("P4-09: page=abc → 400 ERR_VALIDATION with fields.page (was resolver-silent default 200)")
	void malformedPage_badRequest() throws Exception {
		seedPendingMessages(1, "malformedpage");
		mockMvc.perform(get(FILTER_URL)
						.header("Authorization", adminToken())
						.param("status", "PENDING")
						.param("page", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("ERR_VALIDATION"))
				.andExpect(jsonPath("$.message").value("Please check the submitted fields."))
				.andExpect(jsonPath("$.fields.page").value("Parameter 'page' must be a valid integer."))
				.andExpect(jsonPath("$.refId").doesNotExist());
	}

	@Test
	@DisplayName("P4-09: size=abc → 400 ERR_VALIDATION with fields.size (was resolver-silent default 200)")
	void malformedSize_badRequest() throws Exception {
		seedPendingMessages(1, "malformedsize");
		mockMvc.perform(get(FILTER_URL)
						.header("Authorization", adminToken())
						.param("status", "PENDING")
						.param("size", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("ERR_VALIDATION"))
				.andExpect(jsonPath("$.fields.size").exists())
				.andExpect(jsonPath("$.refId").doesNotExist());
	}
}
