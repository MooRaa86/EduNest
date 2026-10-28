package com.example.gradproj.EduNest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Whole-context smoke test.
 * Requires the real app environment (DATABASE_URL / MAIL_HOST / JWT_SECRET) because the
 * full context binds the production property placeholders — until the dedicated test
 * profile + Testcontainers arrive (EB-35), this test is skipped in bare shells and runs
 * wherever the app is normally configured to boot.
 */
@SpringBootTest(properties = {
        "JWT_SECRET=it-smoke-secret-0123456789abcdefghij0123",
        "spring.profiles.active=prod"
})
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = ".+",
        disabledReason = "Whole-context smoke test needs the app's real environment (DATABASE_URL et al.); "
                + "env-independent tests arrive with the test profile (EB-35).")
class EduNestApplicationTests {

	@Test
	void contextLoads() {
	}

}
