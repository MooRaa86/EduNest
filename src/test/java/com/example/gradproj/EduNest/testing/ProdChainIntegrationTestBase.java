package com.example.gradproj.EduNest.testing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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

	@Autowired
	protected MockMvc mockMvc;
}
