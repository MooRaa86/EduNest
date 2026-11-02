package com.example.gradproj.EduNest.config.integrations;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ADD-05 / EB-85 groundwork & EB-66 live client — Zoom Server-to-Server OAuth configuration.
 *
 * Binds the {@code ZOOM_*} environment variables (drop-in ready: Omar provisions
 * the Marketplace app → keys land in the env store → flip {@code ZOOM_ENABLED}).
 * Everything else in the integration (HTTP client, meeting service wiring in
 * {@code P4-17}/EB-66) reads from here; no values are ever hardcoded.
 *
 * Omar-side provisioning checklist (done once in the Zoom Marketplace):
 * <ol>
 *   <li>Marketplace → Build App → <b>Server-to-Server OAuth</b> (needs a paid
 *       plan for long meetings — Basic caps at 40 min).</li>
 *   <li>Scopes, least-privilege: {@code meeting:write:admin} (create/schedule)
 *       + {@code meeting:read:admin} (details/attendance read).</li>
 *   <li>Copy {@code Account ID} → ZOOM_ACCOUNT_ID, {@code Client ID} →
 *       ZOOM_CLIENT_ID, {@code Client Secret} → ZOOM_CLIENT_SECRET. No redirect
 *       URI is needed for Server-to-Server apps.</li>
 *   <li>Secrets go to the env store only (never git); rotation per P6-03.</li>
 * </ol>
 *
 * Disposition contract ({@link com.example.gradproj.EduNest.service.integration.zoom.DisabledZoomMeetingClient}):
 * {@code enabled=false} (default) → placeholder bean, zero external calls;
 * {@code enabled=true} without credentials is possible during bring-up and the
 * placeholder reports it at call time; the real HTTP client swaps in at EB-66.
 */
@Component
@ConfigurationProperties(prefix = "app.zoom")
@Getter
@Setter
public class ZoomProperties {

    /** Master switch — false keeps the placeholder active and calls external nothing. */
    private boolean enabled = false;

    /** Zoom Account ID (Server-to-Server OAuth app credentials block). */
    private String accountId = "";

    /** Zoom OAuth Client ID. */
    private String clientId = "";

    /** Zoom OAuth Client Secret — env store only, never git. */
    private String clientSecret = "";

    /** REST base URL; override target for tests/rehearsals if ever needed. */
    private String baseUrl = "https://api.zoom.us/v2";

    /** Host user ID or email (Server-to-Server OAuth target, defaults to "me"). */
    private String hostUserId = "me";

    /** Max meeting duration in minutes (Basic plan caps at 40; D-29). */
    private int maxMeetingMinutes = 40;

    /** OAuth token endpoint for Server-to-Server app. */
    private String oauthTokenUrl = "https://zoom.us/oauth/token";

    /** All three credentials present? */
    public boolean isConfigured() {
        return nonBlank(accountId) && nonBlank(clientId) && nonBlank(clientSecret);
    }

    /** enabled AND all credentials present — the live-use condition. */
    public boolean isReadyForLiveUse() {
        return enabled && isConfigured();
    }

    private static boolean nonBlank(String v) {
        return v != null && !v.isBlank();
    }
}
