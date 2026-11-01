package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-03 / EB-37 — G9/G10/G11: generic file endpoint, notification push, badge reads.
 */
@Tag("critical")
class FileNotifyBadgeProbesTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Value("${app.upload.base-dir}") private String baseDir;

	private static final String STRANGER = "probe-fnb-stranger@example.org";

	/**
	 * G9 — unknown paths and traversal are rejected by the generic guard.
	 */
	@Test
	@DisplayName("G9: arbitrary-path downloads are forbidden")
	void g9_strangerFetchesArbitraryFile() throws Exception {
		auth.student(STRANGER); // token-holder must exist in DB (validator checks users table)
		Path dir = Path.of(baseDir, "probe-fnb");
		Files.createDirectories(dir);
		Path file = Files.createTempFile(dir, "g9-probe-", ".txt");
		Files.write(file, "g9 secret bytes".getBytes(StandardCharsets.UTF_8));
		try {
			mockMvc.perform(get("/files")
							.param("filePath", "probe-fnb/" + file.getFileName())
							.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT)))
					.andExpect(status().isNotFound());

			// traversal must STAY blocked (this part is FIXED behavior, keep forever)
			mockMvc.perform(get("/files")
							.param("filePath", "../../application.properties")
							.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT)))
					.andExpect(status().isNotFound());
		} finally {
			try {
				Files.deleteIfExists(file);
			} catch (IOException e) {
				// Windows may temporarily lock even a closed synthetic fixture.
				System.err.println("Could not delete disposable G9 fixture; retrying at JVM exit: " + e);
				file.toFile().deleteOnExit();
			}
		}
	}

	/**
	 * G10 — any authenticated user pushes a notification to ANY email.
	 * P1-11 / EB-18 (Decision D-05): Deleted the test endpoint POST /api/v1/notifications/send.
	 * Now returns 404 Not Found (SEL-10 / NOTIF-01 closed).
	 */
	@Test
	@DisplayName("G10/enforced: test notification send endpoint deleted → 404 (SEL-10/NOTIF-01 fixed)")
	void g10_userPushesNotificationToAnyone() throws Exception {
		auth.student(STRANGER);
		auth.student("victim@example.org");
		mockMvc.perform(post("/api/v1/notifications/send")
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT))
						.contentType("application/json")
						.content("{\"email\":\"victim@example.org\",\"title\":\"probe\","
								+ "\"content\":\"unsolicited\",\"type\":\"ANNOUNCEMENT\"}"))
				.andExpect(status().isNotFound());
	}

	/**
	 * MAIL-01 — open platform email relay POST /send (PlatformMailController).
	 * P1-11 / EB-18 (Decision D-05): Deleted PlatformMailController and POST /send.
	 * Now returns 404 Not Found (MAIL-01 closed).
	 */
	@Test
	@DisplayName("MAIL-01/enforced: open platform mail endpoint POST /send deleted → 404 (MAIL-01 fixed)")
	void g10_platformMailSendEndpointDeleted() throws Exception {
		auth.student(STRANGER);
		mockMvc.perform(post("/send")
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT))
						.contentType("application/json")
						.content("{\"email\":\"victim@example.org\",\"name\":\"Stranger\","
								+ "\"message\":\"unsolicited email\"}"))
				.andExpect(status().isNotFound());
	}

	/**
	 * G11 — badge-award read scoping (FLIPPED 2026-11-01, EB-26 / P1-19).
	 * Was (Option A pin): 200 — any authenticated user could enumerate any
	 * student's badge awards by id (BadgeAwardService.getAwardsByStudent had
	 * no scoping at all).
	 * Now: 403 — /student/{id} is confined to ADMIN, the student themself
	 * (principal-derived email) or a mentor of one of the student's
	 * enrollments. Fuller matrix: BadgeAwardScopingProbesTest (EB-26).
	 */
	@Test
	@DisplayName("G11/confined: stranger reads another student's badge awards → 403")
	void g11_strangerReadsForeignBadgeAwards() throws Exception {
		auth.student(STRANGER); // token-holder must exist in DB (validator checks users table)
		var victim = auth.student("probe-badge-victim@example.org");

		mockMvc.perform(get("/api/v1/badge-awards/student/{studentId}", victim.getId())
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden());
	}
}
