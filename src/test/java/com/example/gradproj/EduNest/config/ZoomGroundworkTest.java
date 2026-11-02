package com.example.gradproj.EduNest.config;

import com.example.gradproj.EduNest.config.integrations.ZoomProperties;
import com.example.gradproj.EduNest.service.integration.zoom.DisabledZoomMeetingClient;
import com.example.gradproj.EduNest.service.integration.zoom.ZoomMeetingClient;
import com.example.gradproj.EduNest.service.integration.zoom.ZoomMeetingCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADD-05 / EB-85 groundwork — boot-safe integration disabled by default.
 *
 * Pins the state the app ships in BEFORE Omar provisions the Zoom Marketplace
 * app: (1) the {@code app.zoom} config binds with NO ZOOM_* env at all (all
 * defaults, {@code enabled=false}); (2) the bean in play is the
 * placeholder {@link DisabledZoomMeetingClient}; (3) any call fails fast with
 * the actionable provisioning message — and never touches the network (there
 * is no HTTP client bean to touch); (4) the create-request record enforces
 * its duration contract.
 */
@Tag("critical")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles({"test"})
class ZoomGroundworkTest {

	@Autowired
	private ZoomProperties props;

	@Autowired
	private ZoomMeetingClient client;

	@DisplayName("EB-85: app.zoom binds with no ZOOM_* env — disabled, blank credentials")
	@Test
	void defaultStateIsDisabledAndBlank() {
		assertThat(props.isEnabled()).isFalse();
		assertThat(props.getAccountId()).isBlank();
		assertThat(props.getClientId()).isBlank();
		assertThat(props.getClientSecret()).isBlank();
		assertThat(props.getBaseUrl()).isEqualTo("https://api.zoom.us/v2");
		assertThat(props.isConfigured()).isFalse();
		assertThat(props.isReadyForLiveUse()).isFalse();
	}

	@DisplayName("EB-85: the wired ZoomMeetingClient bean is the boot-safe placeholder")
	@Test
	void placeholderBeanIsInPlay() {
		assertThat(client).isInstanceOf(DisabledZoomMeetingClient.class);
	}

	@DisplayName("EB-85: disabled createMeeting fails fast, names the provisioning owner, zero network")
	@Test
	void disabledCreateFailsFastWithProvisioningMessage() {
		final AtomicReference<String> message = new AtomicReference<>();
		assertThatThrownBy(() -> {
			try {
				client.createMeeting(new ZoomMeetingCreateRequest("Algebra live", Instant.now().plusSeconds(3600), 90, "UTC", "3421"));
			} catch (RuntimeException e) {
				message.set(e.getMessage());
				throw e;
			}
		}).isInstanceOf(IllegalStateException.class);

		assertThat(message.get())
				.contains("app.zoom.enabled=false")
				.contains("[ADD-05]/EB-85")
				.contains("ZOOM_ACCOUNT_ID");
	}

	@DisplayName("EB-85: disabled read fails fast with the same contract")
	@Test
	void disabledReadFailsFastWithProvisioningMessage() {
		assertThatThrownBy(() -> client.getMeetingDetails(123L))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("zoom meeting read");
	}

	@DisplayName("EB-85: ZoomMeetingCreateRequest rejects non-positive durations at the contract level")
	@Test
	void createRequestRejectsBadDurations() {
		assertThatThrownBy(() -> new ZoomMeetingCreateRequest("x", Instant.now(), 0, "UTC", null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ZoomMeetingCreateRequest("x", Instant.now(), -5, "UTC", null))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
