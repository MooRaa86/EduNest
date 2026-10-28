package com.example.gradproj.EduNest.testing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * P2-01 / EB-35 — base class for whole-application integration tests.
 *
 * Boots the FULL app context against the fake environment defined in
 * {@code src/test/resources/application-test.properties} (H2 in-memory, stubbed mail,
 * fixed JWT secret, EDUNEST_UNSAFE_DEV chain). Runs on any machine with zero setup —
 * no MySQL, no env vars, no Docker (D-26).
 *
 * Subclasses get a configured {@link MockMvc} through {@link #mockMvc}.
 *
 * Variants:
 * - {@link TransactionalIntegrationTestBase}: same, with DB writes auto-rolled back.
 * - (EB-37 will add a prod-chain variant via {@code @ActiveProfiles({"test", "prod"})}
 *   for authorization-matrix probes.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

	@Autowired
	protected MockMvc mockMvc;
}
