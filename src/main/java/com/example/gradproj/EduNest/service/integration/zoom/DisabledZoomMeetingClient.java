package com.example.gradproj.EduNest.service.integration.zoom;

import com.example.gradproj.EduNest.config.integrations.ZoomProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * ADD-05 / EB-85 groundwork placeholder — the default {@link ZoomMeetingClient}
 * bean until the real HTTP client lands with the credentials provisioning.
 *
 * Boot-safety contract: constructing this bean NEVER fails and performs ZERO
 * network calls (the app builds with {@code ZOOM_ENABLED} absent entirely).
 * Call-time contract: every method fails fast with an {@link IllegalStateException}
 * whose message names exactly who owns the missing prerequisite:
 * <ul>
 *   <li>disabled (default) → provision the Server-to-Server OAuth app per
 *       [ADD-05]/EB-85 (Omar's Marketplace checklist is on
 *       {@link ZoomProperties}).</li>
 *   <li>enabled but not live-ready → credentials partially/fully missing; put
 *       them in the env store before use.</li>
 *   <li>enabled + configured → impossible in this placeholder (the bean swap
 *       happens at EB-85/EB-66 merge); if a call ever reaches the placeholder
 *       in that state, the wiring regressed — fail loudly.</li>
 * </ul>
 */
@Component
public class DisabledZoomMeetingClient implements ZoomMeetingClient {

    private final ZoomProperties props;

    @Autowired
    public DisabledZoomMeetingClient(ZoomProperties props) {
        this.props = props;
    }

    @Override
    public ZoomMeetingResult createMeeting(ZoomMeetingCreateRequest request) {
        throw guard("create");
    }

    @Override
    public ZoomMeetingDetails getMeetingDetails(long zoomMeetingId) {
        throw guard("read");
    }

    private IllegalStateException guard(String operation) {
        if (!props.isEnabled()) {
            return new IllegalStateException(
                    "Zoom integration is disabled (app.zoom.enabled=false). Provision the "
                            + "Server-to-Server OAuth app per [ADD-05]/EB-85, set ZOOM_ACCOUNT_ID / "
                            + "ZOOM_CLIENT_ID / ZOOM_CLIENT_SECRET, then flip ZOOM_ENABLED=true. "
                            + "(attempted operation: zoom meeting " + operation + ")");
        }
        if (!props.isConfigured()) {
            return new IllegalStateException(
                    "Zoom integration is ENABLED but its credentials are incomplete — fill "
                            + "ZOOM_ACCOUNT_ID / ZOOM_CLIENT_ID / ZOOM_CLIENT_SECRET in the env store "
                            + "before any Zoom call. (attempted operation: zoom meeting " + operation + ")");
        }
        return new IllegalStateException(
                "Zoom client regression: credentials are live but no HTTP client bean is wired — "
                        + "the ZoomMeetingClient bean must have been swapped for the real client in the "
                        + "EB-85/P4-17 (EB-66) merge. (attempted operation: zoom meeting " + operation + ")");
    }
}
