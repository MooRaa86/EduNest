package com.example.gradproj.EduNest.testing;

import org.springframework.transaction.annotation.Transactional;

/**
 * P2-01 / EB-35 — DB-touching integration test base.
 *
 * Identical to {@link IntegrationTestBase} plus {@code @Transactional}: every test
 * method runs in a transaction that is ROLLED BACK when the test ends, so fixtures
 * never leak between tests and the shared in-memory H2 stays clean.
 *
 * Use the non-transactional {@link IntegrationTestBase} for WebSocket/async flows
 * (they need committed data; that base arrives with P2-05).
 */
@Transactional
public abstract class TransactionalIntegrationTestBase extends IntegrationTestBase {
}
