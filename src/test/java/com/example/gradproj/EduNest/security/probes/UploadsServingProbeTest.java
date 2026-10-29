package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-21 / EB-28 — G14/TD-09 proposed regression coverage.
 *
 * BEFORE: ImageStorageService wrote uploads/student|mentor|admin while the URL
 * rules listed phantom names (student-profile, mentor-profile, profile-images);
 * the real serving coverage fell into the PUBLIC catch-all /uploads/**, so
 * student work under /uploads/submissions was anonymous-public too (FILE-01).
 *
 * NOW: the prod chain lists only UploadPolicy.PUBLIC_FOLDERS (mentorship-cover-
 * images, student, mentor, chatroom) and denyAlls every other /uploads path;
 * WebConfig maps one explicit resource handler per public folder. This pin
 * holds the explicit-allowlist public contract for real folders: student
 * profile images stay anonymously fetchable at the same URL — while the
 * private/admin/unknown flips are pinned by UploadExposurePolicyProbeTest.
 *
 * Fixtures are synthetic, written under the disposable test base dir
 * (app.upload.base-dir → a fresh temporary directory) — the repository's uploads/
 * tree is never touched.
 */
@Tag("critical")
class UploadsServingProbeTest extends ProdChainIntegrationTestBase {

	@Value("${app.upload.base-dir}") private String baseDir;
	private static final String FILE_REL = "student/g14-probe.jpg";

	@AfterEach
	void cleanup() throws Exception {
		// only the file — the directories stay (harmless; deleting them races readers)
		Files.deleteIfExists(Path.of(baseDir).resolve(FILE_REL));
	}

	/**
	 * G14 — after the P1-21/EB-28 policy: real folder names ARE the policy names;
	 * student is an anonymous-public category, covered by the EXPLICIT rule
	 * /uploads/student/** (no catch-all needed any more).
	 * EXPECT: 200 for an ANONYMOUS caller (no Authorization header at all).
	 * PRIVATE/admin/unknown categories → 401/403: see UploadExposurePolicyProbeTest.
	 */
	@Test
	@DisplayName("G14/allowlist: anonymous caller fetches a public student image → 200 (explicit rule, not catch-all)")
	void g14_anonymousFetchesPublicStudentImage() throws Exception {
		Files.createDirectories(Path.of(baseDir).resolve("student"));
		Files.write(Path.of(baseDir).resolve(FILE_REL), "g14 content".getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(get("/uploads/student/g14-probe.jpg"))
				.andExpect(status().isOk());
	}
}
