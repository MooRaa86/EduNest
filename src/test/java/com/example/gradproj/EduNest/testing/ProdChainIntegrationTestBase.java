package com.example.gradproj.EduNest.testing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * P2-03 / EB-37 — authorization-probe base.
 *
 * {@code @ActiveProfiles({"prod", "test"})} with prod FIRST: the security guard
 * (P1-02) sees the "prod" profile → the real stateless JWT chain + authorization
 * matrix boots; "test" applied LAST so every fake-environment override from
 * {@code application-test.properties} wins over {@code application-prod.properties}
 * (H2 instead of ${DATABASE_URL}, H2 dialect, stubbed mail, fixed test JWT secret).
 *
 * Result: the production security chain runs the full app against H2 with zero
 * env setup and no Docker (EB-35, D-27). Probes mint real JWTs via {@code AuthFixture}
 * and hit real endpoints through {@code MockMvc}; assertions encode TODAY's behavior
 * (Option A doctrine — audit issue date in every test name; flips tracked to fix EBs).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles({"prod", "test"})
public abstract class ProdChainIntegrationTestBase {

	private static final Path UPLOAD_ROOT = createUploadRoot();

	private static Path createUploadRoot() {
		try {
			return Files.createTempDirectory("edunest-security-probes-");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void uploadRoot(DynamicPropertyRegistry registry) {
		registry.add("app.upload.base-dir", () -> UPLOAD_ROOT.toString());
	}

	@Autowired
	protected MockMvc mockMvc;
}
