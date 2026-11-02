package com.example.gradproj.EduNest.config;

import com.example.gradproj.EduNest.config.integrations.ZoomProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADD-05 / EB-85 groundwork — the drop-in day: once the Marketplace app exists
 * and the env store carries the credentials, THIS is the state {@code app.zoom}
 * must resolve to. The test inlines the exact env values the placeholder will
 * pick up; when the real HTTP client lands (EB-85/P4-17 merge), these same
 * properties select it.
 */
@Tag("critical")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
		properties = {
				"app.zoom.enabled=true",
				"app.zoom.account-id=demo-account",
				"app.zoom.client-id=demo-client-id",
				"app.zoom.client-secret=demo-client-secret"
		})
@AutoConfigureMockMvc
@ActiveProfiles({"test"})
class ZoomPropertiesBindingTest {

	@Autowired
	private ZoomProperties props;

	@DisplayName("EB-85: env values bind verbatim and readyForLiveUse flips on")
	@Test
	void bindFillsCredentialsAndReadyFlag() {
		assertThat(props.isEnabled()).isTrue();
		assertThat(props.getAccountId()).isEqualTo("demo-account");
		assertThat(props.getClientId()).isEqualTo("demo-client-id");
		assertThat(props.getClientSecret()).isEqualTo("demo-client-secret");
		assertThat(props.isConfigured()).isTrue();
		assertThat(props.isReadyForLiveUse()).isTrue();
	}
}
