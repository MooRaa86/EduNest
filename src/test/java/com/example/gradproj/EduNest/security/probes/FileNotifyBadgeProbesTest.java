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
	 * Audit: NotificationController POST /send class-guard isAuthenticated() only.
	 * EXPECT TODAY: 2xx (push through).
	 * ⚠️ OMAR'S NOTE: this is a pure test endpoint scheduled for complete removal —
	 * when it is deleted this probe flips to 404 and is then retired with [P1-11] (EB-18).
	 */
	@Test
	@DisplayName("G10/todayOpen: authenticated user pushes a notification to any email → 2xx")
	void g10_userPushesNotificationToAnyone() throws Exception {
		auth.student(STRANGER); // token-holder must exist in DB (validator checks users table)
		auth.student("victim@example.org"); // the pushed-to user must resolve in NotificationService
		mockMvc.perform(post("/api/v1/notifications/send")
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT))
						.contentType("application/json")
						.content("{\"email\":\"victim@example.org\",\"title\":\"probe\","
								+ "\"content\":\"unsolicited\",\"type\":\"ANNOUNCEMENT\"}"))
				.andExpect(status().is2xxSuccessful());
	}

	/**
	 * G11 — badge-award reads for ANY student id, no ownership scoping.
	 * Audit: BadgeAwardController GETs unannotated; service getAwardsByStudent no check.
	 * EXPECT TODAY: 200.
	 * FLIPS WITH: [P1-19] badge/award read scoping (per ISSUE-MAPPING).
	 */
	@Test
	@DisplayName("G11/todayOpen: stranger reads another student's badge awards → 200")
	void g11_strangerReadsForeignBadgeAwards() throws Exception {
		auth.student(STRANGER); // token-holder must exist in DB (validator checks users table)
		var victim = auth.student("probe-badge-victim@example.org");

		mockMvc.perform(get("/api/v1/badge-awards/student/{studentId}", victim.getId())
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT)))
				.andExpect(status().isOk());
	}
}
