package com.example.gradproj.EduNest.service.integration.zoom;

import com.example.gradproj.EduNest.config.integrations.ZoomProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * P4-17 / EB-66 — Real HTTP client for Zoom Server-to-Server OAuth integration.
 *
 * <p>Activated when {@code app.zoom.enabled=true}. Handles Server-to-Server token
 * generation and in-memory caching with skew refresh, meeting creation with
 * Basic-plan duration clamping (D-29 / 40 min), meeting details retrieval,
 * meeting update, meeting deletion, and meeting termination.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.zoom", name = "enabled", havingValue = "true")
public class HttpZoomMeetingClient implements ZoomMeetingClient {

    private final ZoomProperties props;
    private final RestClient restClient;

    private volatile String cachedAccessToken;
    private volatile Instant tokenExpiresAt = Instant.MIN;

    @Autowired
    public HttpZoomMeetingClient(ZoomProperties props, RestClient.Builder restClientBuilder) {
        this.props = props;
        this.restClient = restClientBuilder.build();
    }

    // For test harnesses that construct without Spring container
    public HttpZoomMeetingClient(ZoomProperties props, RestClient restClient) {
        this.props = props;
        this.restClient = restClient;
    }

    @Override
    public ZoomMeetingResult createMeeting(ZoomMeetingCreateRequest request) {
        validateConfigured();
        int maxMinutes = props.getMaxMeetingMinutes();
        int requestedMinutes = request.durationMinutes();
        int duration = requestedMinutes;
        if (duration > maxMinutes) {
            log.warn("Requested meeting duration ({} min) exceeds configured cap ({} min, D-29 Basic plan). Clamping duration.",
                    requestedMinutes, maxMinutes);
            duration = maxMinutes;
        }

        String hostUser = props.getHostUserId();
        if (hostUser == null || hostUser.isBlank()) {
            hostUser = "me";
        }

        String url = props.getBaseUrl() + "/users/" + hostUser + "/meetings";

        Map<String, Object> body = new HashMap<>();
        body.put("topic", request.topic());
        body.put("type", 2); // Scheduled meeting
        body.put("start_time", DateTimeFormatter.ISO_INSTANT.format(request.startTime()));
        body.put("duration", duration);
        body.put("timezone", request.timezone() != null && !request.timezone().isBlank() ? request.timezone() : "UTC");
        if (request.passcode() != null && !request.passcode().isBlank()) {
            body.put("password", request.passcode());
        }

        Map<String, Object> settings = new HashMap<>();
        settings.put("host_video", true);
        settings.put("participant_video", true);
        settings.put("join_before_host", false);
        settings.put("mute_upon_entry", true);
        body.put("settings", settings);

        Map<String, Object> response = executeWithRetry(token ->
                restClient.post()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {})
        );

        if (response == null || !response.containsKey("id")) {
            throw new IllegalStateException("Zoom createMeeting returned invalid response: " + response);
        }

        long meetingId = ((Number) response.get("id")).longValue();
        String topic = (String) response.getOrDefault("topic", request.topic());
        String joinUrl = (String) response.get("join_url");
        String startUrl = (String) response.get("start_url");
        String passcode = (String) response.get("password");

        log.info("Zoom meeting created successfully: meetingId={}, topic='{}'", meetingId, topic);
        return new ZoomMeetingResult(meetingId, topic, joinUrl, startUrl, passcode);
    }

    @Override
    public ZoomMeetingDetails getMeetingDetails(long zoomMeetingId) {
        validateConfigured();
        String url = props.getBaseUrl() + "/meetings/" + zoomMeetingId;

        Map<String, Object> response = executeWithRetry(token ->
                restClient.get()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {})
        );

        if (response == null || !response.containsKey("id")) {
            throw new IllegalStateException("Zoom getMeetingDetails returned invalid response for meetingId: " + zoomMeetingId);
        }

        long id = ((Number) response.get("id")).longValue();
        String topic = (String) response.get("topic");
        String status = (String) response.get("status");
        String joinUrl = (String) response.get("join_url");
        String startUrl = (String) response.get("start_url");
        String startTimeStr = (String) response.get("start_time");
        Instant plannedStart = startTimeStr != null ? Instant.parse(startTimeStr) : null;

        return new ZoomMeetingDetails(id, topic, status, joinUrl, startUrl, plannedStart);
    }

    @Override
    public void updateMeeting(long zoomMeetingId, String topic, Instant startTime, Integer durationMinutes) {
        validateConfigured();
        String url = props.getBaseUrl() + "/meetings/" + zoomMeetingId;

        Map<String, Object> body = new HashMap<>();
        if (topic != null && !topic.isBlank()) {
            body.put("topic", topic);
        }
        if (startTime != null) {
            body.put("start_time", DateTimeFormatter.ISO_INSTANT.format(startTime));
        }
        if (durationMinutes != null && durationMinutes > 0) {
            int duration = Math.min(durationMinutes, props.getMaxMeetingMinutes());
            body.put("duration", duration);
        }

        if (body.isEmpty()) {
            return;
        }

        try {
            executeWithRetry(token -> {
                restClient.patch()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                return null;
            });
            log.info("Zoom meeting {} updated successfully with {}", zoomMeetingId, body);
        } catch (HttpClientErrorException.NotFound notFound) {
            log.warn("Zoom meeting {} not found on cloud during update. Proceeding without error.", zoomMeetingId);
        } catch (Exception e) {
            log.error("Failed to update Zoom meeting {}: {}", zoomMeetingId, e.getMessage(), e);
            throw new IllegalStateException("Failed to update Zoom meeting: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteMeeting(long zoomMeetingId) {
        validateConfigured();
        String url = props.getBaseUrl() + "/meetings/" + zoomMeetingId;

        try {
            executeWithRetry(token -> {
                restClient.delete()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .toBodilessEntity();
                return null;
            });
            log.info("Zoom meeting {} deleted successfully from cloud.", zoomMeetingId);
        } catch (HttpClientErrorException.NotFound notFound) {
            log.info("Zoom meeting {} was already deleted or not found on cloud.", zoomMeetingId);
        } catch (Exception e) {
            log.error("Failed to delete Zoom meeting {}: {}", zoomMeetingId, e.getMessage(), e);
            throw new IllegalStateException("Failed to delete Zoom meeting: " + e.getMessage(), e);
        }
    }

    @Override
    public void endMeeting(long zoomMeetingId) {
        validateConfigured();
        String url = props.getBaseUrl() + "/meetings/" + zoomMeetingId + "/status";
        Map<String, String> body = Map.of("action", "end");

        try {
            executeWithRetry(token -> {
                restClient.put()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                return null;
            });
            log.info("Zoom live meeting {} ended successfully.", zoomMeetingId);
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest e) {
            log.debug("Notice ending Zoom meeting {}: {} (may not have been started in Zoom)", zoomMeetingId, e.getMessage());
        } catch (Exception e) {
            log.warn("Non-fatal warning ending Zoom meeting {}: {}", zoomMeetingId, e.getMessage());
        }
    }

    /**
     * Executes a REST operation with token retry: if 401 Unauthorized occurs,
     * invalidates the cached token and retries once.
     */
    private <T> T executeWithRetry(ZoomOperation<T> operation) {
        String token = getAccessToken();
        try {
            return operation.execute(token);
        } catch (HttpClientErrorException.Unauthorized unauthorized) {
            log.warn("Zoom returned 401 Unauthorized. Evicting token cache and retrying once...");
            invalidateToken();
            String refreshedToken = getAccessToken();
            return operation.execute(refreshedToken);
        }
    }

    @FunctionalInterface
    private interface ZoomOperation<T> {
        T execute(String token);
    }

    /**
     * Retrieves or refreshes Server-to-Server OAuth access token with in-memory caching.
     */
    public synchronized String getAccessToken() {
        Instant now = Instant.now();
        if (cachedAccessToken != null && now.isBefore(tokenExpiresAt.minusSeconds(60))) {
            return cachedAccessToken;
        }

        validateConfigured();

        String tokenUrl = props.getOauthTokenUrl() + "?grant_type=account_credentials&account_id=" + props.getAccountId();
        String credentials = props.getClientId() + ":" + props.getClientSecret();
        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        log.info("Requesting fresh Zoom Server-to-Server OAuth token for accountId: {}", props.getAccountId());

        Map<String, Object> tokenResponse;
        try {
            tokenResponse = restClient.post()
                    .uri(tokenUrl)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw new IllegalStateException("Zoom OAuth token request failed with status: " + resp.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to acquire Zoom S2S OAuth token: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to acquire Zoom OAuth token: " + e.getMessage(), e);
        }

        if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
            throw new IllegalStateException("Zoom OAuth response did not contain access_token: " + tokenResponse);
        }

        this.cachedAccessToken = (String) tokenResponse.get("access_token");
        int expiresInSeconds = tokenResponse.containsKey("expires_in")
                ? ((Number) tokenResponse.get("expires_in")).intValue()
                : 3600;
        this.tokenExpiresAt = now.plusSeconds(expiresInSeconds);

        log.info("Zoom S2S OAuth token successfully cached. Valid for {} seconds.", expiresInSeconds);
        return cachedAccessToken;
    }

    public synchronized void invalidateToken() {
        this.cachedAccessToken = null;
        this.tokenExpiresAt = Instant.MIN;
    }

    private void validateConfigured() {
        if (!props.isConfigured()) {
            throw new IllegalStateException(
                    "Zoom integration is ENABLED but its credentials are incomplete — fill "
                            + "ZOOM_ACCOUNT_ID / ZOOM_CLIENT_ID / ZOOM_CLIENT_SECRET in the env store "
                            + "before any Zoom call.");
        }
    }
}
