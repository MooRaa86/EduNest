package com.example.gradproj.EduNest.security.probes;

import org.junit.jupiter.api.Tag;

@Tag("critical")
class LoginBodyCompatibilityDefaultTest extends LoginContractProbeBase {
    @Override boolean bodyTokenExpected() { return true; }
}
