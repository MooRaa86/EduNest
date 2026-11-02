package com.example.gradproj.EduNest.service.integration.zoom;

import com.example.gradproj.EduNest.config.integrations.ZoomProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;

class HttpZoomMeetingClientTest {

    private ZoomProperties props;
    private MockRestServiceServer mockServer;
    private HttpZoomMeetingClient client;

    @BeforeEach
    void setUp() {
        props = new ZoomProperties();
        props.setEnabled(true);
        props.setAccountId("test-account");
        props.setClientId("test-client");
        props.setClientSecret("test-secret");
        props.setBaseUrl("https://api.zoom.us/v2");
        props.setHostUserId("me");
        props.setMaxMeetingMinutes(40);
        props.setOauthTokenUrl("https://zoom.us/oauth/token");

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new HttpZoomMeetingClient(props, builder);
    }

    private void expectTokenCall(String accessToken, int expiresIn) {
        mockServer.expect(requestTo("https://zoom.us/oauth/token?grant_type=account_credentials&account_id=test-account"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .andRespond(withSuccess(
                        """
                        {
                            "access_token": "%s",
                            "token_type": "bearer",
                            "expires_in": %d,
                            "scope": "meeting:write:admin meeting:read:admin"
                        }
                        """.formatted(accessToken, expiresIn),
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    @DisplayName("EB-66: token is cached in-memory and not requested repeatedly")
    void tokenIsCachedInMemory() {
        expectTokenCall("token-123", 3600);

        String t1 = client.getAccessToken();
        String t2 = client.getAccessToken();

        assertThat(t1).isEqualTo("token-123");
        assertThat(t2).isEqualTo("token-123");
        mockServer.verify(); // asserts only 1 call was made to OAuth
    }

    @Test
    @DisplayName("EB-66: createMeeting clamps duration to 40 min per D-29 Basic plan and parses response")
    void createMeetingClampsDurationAndParsesResponse() {
        expectTokenCall("token-abc", 3600);

        mockServer.expect(requestTo("https://api.zoom.us/v2/users/me/meetings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-abc"))
                .andExpect(jsonPath("$.topic").value("Advanced Spring Boot"))
                .andExpect(jsonPath("$.duration").value(40)) // requested 90 clamped to 40
                .andExpect(jsonPath("$.type").value(2))
                .andRespond(withSuccess(
                        """
                        {
                            "id": 98765432101,
                            "topic": "Advanced Spring Boot",
                            "join_url": "https://us05web.zoom.us/j/98765432101?pwd=abc",
                            "start_url": "https://us05web.zoom.us/s/98765432101?zak=xyz",
                            "password": "secretpasscode"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        Instant start = Instant.parse("2026-11-05T18:00:00Z");
        ZoomMeetingCreateRequest req = new ZoomMeetingCreateRequest("Advanced Spring Boot", start, 90, "UTC", null);
        ZoomMeetingResult res = client.createMeeting(req);

        assertThat(res.zoomMeetingId()).isEqualTo(98765432101L);
        assertThat(res.topic()).isEqualTo("Advanced Spring Boot");
        assertThat(res.joinUrl()).isEqualTo("https://us05web.zoom.us/j/98765432101?pwd=abc");
        assertThat(res.startUrl()).isEqualTo("https://us05web.zoom.us/s/98765432101?zak=xyz");
        assertThat(res.passcode()).isEqualTo("secretpasscode");
        mockServer.verify();
    }

    @Test
    @DisplayName("EB-66: getMeetingDetails queries meeting endpoint with Bearer token")
    void getMeetingDetailsParsesSuccessfully() {
        expectTokenCall("token-xyz", 3600);

        mockServer.expect(requestTo("https://api.zoom.us/v2/meetings/98765432101"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-xyz"))
                .andRespond(withSuccess(
                        """
                        {
                            "id": 98765432101,
                            "topic": "Algorithms Live",
                            "status": "waiting",
                            "start_time": "2026-11-05T18:00:00Z",
                            "duration": 40,
                            "timezone": "UTC",
                            "join_url": "https://us05web.zoom.us/j/98765432101",
                            "start_url": "https://us05web.zoom.us/s/98765432101",
                            "password": "pass"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        ZoomMeetingDetails details = client.getMeetingDetails(98765432101L);

        assertThat(details.zoomMeetingId()).isEqualTo(98765432101L);
        assertThat(details.topic()).isEqualTo("Algorithms Live");
        assertThat(details.status()).isEqualTo("waiting");
        assertThat(details.joinUrl()).isEqualTo("https://us05web.zoom.us/j/98765432101");
        assertThat(details.startUrl()).isEqualTo("https://us05web.zoom.us/s/98765432101");
        assertThat(details.plannedStart()).isEqualTo(Instant.parse("2026-11-05T18:00:00Z"));
        mockServer.verify();
    }

    @Test
    @DisplayName("EB-66: updateMeeting sends PATCH with updated fields")
    void updateMeetingSendsPatch() {
        expectTokenCall("token-upd", 3600);

        mockServer.expect(requestTo("https://api.zoom.us/v2/meetings/98765432101"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-upd"))
                .andExpect(jsonPath("$.topic").value("Updated Topic"))
                .andExpect(jsonPath("$.duration").value(40))
                .andRespond(withStatus(NO_CONTENT));

        Instant newStart = Instant.parse("2026-11-06T19:00:00Z");
        client.updateMeeting(98765432101L, "Updated Topic", newStart, 40);
        mockServer.verify();
    }

    @Test
    @DisplayName("EB-66: deleteMeeting sends DELETE to Zoom cloud")
    void deleteMeetingSendsDelete() {
        expectTokenCall("token-del", 3600);

        mockServer.expect(requestTo("https://api.zoom.us/v2/meetings/98765432101"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-del"))
                .andRespond(withStatus(NO_CONTENT));

        client.deleteMeeting(98765432101L);
        mockServer.verify();
    }

    @Test
    @DisplayName("EB-66: endMeeting sends PUT status with action=end")
    void endMeetingSendsPutStatus() {
        expectTokenCall("token-end", 3600);

        mockServer.expect(requestTo("https://api.zoom.us/v2/meetings/98765432101/status"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-end"))
                .andExpect(jsonPath("$.action").value("end"))
                .andRespond(withStatus(NO_CONTENT));

        client.endMeeting(98765432101L);
        mockServer.verify();
    }

    @Test
    @DisplayName("EB-66: 401 Unauthorized invalidates cached token and retries once")
    void unauthorizedRetriesOnceWithFreshToken() {
        expectTokenCall("stale-token", 3600);

        // 1st attempt: 401 Unauthorized
        mockServer.expect(requestTo("https://api.zoom.us/v2/meetings/123"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer stale-token"))
                .andRespond(withStatus(UNAUTHORIZED));

        // Token refresh call
        expectTokenCall("fresh-token", 3600);

        // 2nd attempt: Success
        mockServer.expect(requestTo("https://api.zoom.us/v2/meetings/123"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer fresh-token"))
                .andRespond(withSuccess(
                        """
                        {
                            "id": 123,
                            "topic": "Retry Meeting",
                            "status": "waiting",
                            "start_time": "2026-11-05T18:00:00Z",
                            "duration": 40,
                            "timezone": "UTC",
                            "join_url": "https://us05web.zoom.us/j/123",
                            "start_url": "https://us05web.zoom.us/s/123"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        ZoomMeetingDetails details = client.getMeetingDetails(123L);
        assertThat(details.zoomMeetingId()).isEqualTo(123L);
        assertThat(details.topic()).isEqualTo("Retry Meeting");
        mockServer.verify();
    }

    @Test
    @DisplayName("EB-66: unconfigured client fails fast before any network call")
    void unconfiguredFailsFast() {
        props.setClientId("");
        assertThatThrownBy(() -> client.getMeetingDetails(123L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("credentials are incomplete");
    }
}
