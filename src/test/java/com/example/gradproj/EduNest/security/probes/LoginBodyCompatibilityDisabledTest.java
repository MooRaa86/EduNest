package com.example.gradproj.EduNest.security.probes;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.TestPropertySource;

@Tag("critical")
@TestPropertySource(properties = "app.auth.return-token-in-body=false")
class LoginBodyCompatibilityDisabledTest extends LoginContractProbeBase {
    @Override boolean bodyTokenExpected() { return false; }
}
