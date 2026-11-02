package com.example.gradproj.EduNest.contract;

import com.example.gradproj.EduNest.repository.contactus.ContactMessageRepository;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-06 / EB-40 — API contract test: bean-validation 400s (SEL-07).
 *
 * {@code D-26} makes {@code 400 ERR_VALIDATION + fields map} the PERMANENT
 * validation contract. EB-32 (P1-25) pinned it for the auth family
 * (login / verify / resend — {@code AuthRequestValidationTest}); this suite
 * widens the SAME contract to a second, PUBLIC producer family —
 * {@code POST /api/v1/contact/save-message} — so validation is provably
 * producer-agnostic, not just the auth DTOs.
 *
 * Payload rules (ContactMessageRequestDto): name/message/phone {@code @NotBlank};
 * email {@code @NotBlank} + custom {@code @Email} regexp
 * ({@code name@domain.tld}, TLD com/net/org/edu/ac.<2-3 chars>);
 * phone {@code @Pattern ^01[0-9]{9}$} (Egyptian mobile shape).
 */
@Tag("critical")
class BeanValidationContractTest extends ProdChainIntegrationTestBase {

	@Autowired private ContactMessageRepository contactMessages;

	private String saveCall(String name, String email, String phone, String message) {
		return "{\"name\":" + name + ",\"email\":" + email +
				",\"phone\":" + phone + ",\"message\":" + message + "}";
	}

	private String json(String s) {
		return s == null ? "null" : "\"" + s + "\"";
	}

	@ParameterizedTest(name = "P2-06/SEL-07 {0} → 400 ERR_VALIDATION, violated key = {1}, message body untouched")
	@CsvSource(nullValues = "NULL", value = {
			"missing name,                  name",
			"missing email,                 email",
			"missing phone,                 phone",
			"missing message,               message",
			"bad email TLD,                 email",
			"bad phone shape,               phone"
	})
	void invalidFields_answerCatalogValidation400(String label, String expectedKey) throws Exception {
		final String email = label.startsWith("missing email") ? null
				: label.startsWith("bad email") ? "contract@badzz"
				: "contract-save@example.org";
		final String name = label.startsWith("missing name") ? null : "Contract Probe";
		final String phone = label.startsWith("missing phone") ? null
				: label.startsWith("bad phone") ? "2122234" : "01012345678";
		final String message = label.startsWith("missing message") ? null : "P2-06 contract message";

		String body = saveCall(json(name), json(email), json(phone), json(message));
		String response = mockMvc.perform(post("/api/v1/contact/save-message")
						.contentType("application/json")
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("ERR_VALIDATION"))
				.andExpect(jsonPath("$.fields." + expectedKey).exists())
				.andReturn().getResponse().getContentAsString();

		org.assertj.core.api.Assertions.assertThat(response)
				.doesNotContain("Hibernate", "SQLException", "java.lang", "org.springframework");
		// a rejected request must NOT have persisted a row
		long rowsWithProbeEmail = contactMessages.findAll().stream()
				.filter(m -> email != null && email.equals(m.getEmail()))
				.count();
		org.assertj.core.api.Assertions.assertThat(rowsWithProbeEmail).isZero();
	}
}
