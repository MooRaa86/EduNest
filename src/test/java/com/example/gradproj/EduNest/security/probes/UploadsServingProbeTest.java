package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-03 / EB-37 — G14: profile-image folder mismatch → catch-all /uploads/** public.
 */
@Tag("critical")
class UploadsServingProbeTest extends ProdChainIntegrationTestBase {

	private static final String FILE_REL = "student/g14-probe.jpg";

	@AfterEach
	void cleanup() throws Exception {
		// only the file — the directories stay (harmless; deleting them races readers)
		Files.deleteIfExists(Path.of("uploads").resolve(FILE_REL));
	}

	/**
	 * G14 — ImageStorageService writes uploads/student|mentor|admin; the URL rules
	 * list different folder names, so the real serving coverage falls into the
	 * PUBLIC catch-all /uploads/**. The probe pins that TODAY these user-content
	 * folders are anonymous-public.
	 * EXPECT TODAY: 200 for an ANONYMOUS caller (no Authorization header at all).
	 * FLIPS WITH: [P1-21] upload exposure policy (EB-28) or D-25 (Supabase Storage
	 * replaces local serving) → then this asserts private/404.
	 */
	@Test
	@DisplayName("G14/todayOpen: anonymous caller fetches a user-content file under /uploads → 200")
	void g14_anonymousFetchesUserContentUpload() throws Exception {
		Path dir = Path.of("uploads");
		Files.createDirectories(dir.resolve("student"));
		Files.write(dir.resolve(FILE_REL), "g14 content".getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(get("/uploads/student/g14-probe.jpg"))
				.andExpect(status().isOk());
	}
}
