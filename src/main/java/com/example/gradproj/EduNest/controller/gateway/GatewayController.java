package com.example.gradproj.EduNest.controller.gateway;

import com.example.gradproj.EduNest.service.gateway.GatewayModel;
import com.example.gradproj.EduNest.service.gateway.GatewayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * [EB-112][P5-06] Root-domain gateway & production showcase.
 *
 * Route matrix (all anonymous, GET/HEAD only — enforced by
 * PublicRoutesPolicy.PUBLIC_GATEWAY_PATTERNS in the security chain):
 *
 * <ul>
 *   <li>{@code GET /} — content-negotiated: HTML showcase for browsers
 *       (Accept: text/html), JSON model for API clients, filtered by
 *       {@code ?audience=hr|business|technical} (default: technical).</li>
 *   <li>{@code GET /view} — shareable direct link to the HTML view.</li>
 *   <li>{@code GET /diagrams} — all Mermaid diagrams as JSON.</li>
 *   <li>{@code GET /status} — lightweight runtime metadata (version,
 *       environment, commit, uptime).</li>
 *   <li>{@code GET /api/v1/showcase}, {@code /api/v1/showcase/diagrams},
 *       {@code /api/v1/showcase/status} — aliases kept for backward
 *       compatibility with the ticket contract.</li>
 * </ul>
 *
 * GET-only by design: the security chain denies non-GET/HEAD methods on
 * these paths, and the controller registers no other verbs.
 */
@RestController
@RequiredArgsConstructor
public class GatewayController {

    private final GatewayService gatewayService;

    /** GET / — HTML for browsers, JSON for API clients. */
    @GetMapping("/")
    public ResponseEntity<?> gateway(
            @RequestParam(required = false) String audience,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept
    ) {
        if (accept != null && accept.contains("text/html")) {
            return html();
        }
        return ResponseEntity.ok(gatewayService.modelFor(audience));
    }

    /** GET /view — shareable direct link to the visual HTML view. */
    @GetMapping("/view")
    public ResponseEntity<byte[]> view() {
        return html();
    }

    /** GET /diagrams (and alias) — programmatic access to all Mermaid diagrams. */
    @GetMapping({"/diagrams", "/api/v1/showcase/diagrams"})
    public ResponseEntity<List<GatewayModel.Diagram>> diagrams() {
        return ResponseEntity.ok(gatewayService.model().diagrams());
    }

    /** GET /status (and alias) — lightweight runtime metadata. */
    @GetMapping({"/status", "/api/v1/showcase/status"})
    public ResponseEntity<Map<String, Object>> status() {
        GatewayModel m = gatewayService.model();
        return ResponseEntity.ok(Map.of(
                "service", m.service(),
                "version", m.version(),
                "environment", m.environment(),
                "commit", m.commit(),
                "uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                "timestamp", Instant.now().toString()
        ));
    }

    /** GET /api/v1/showcase — JSON model alias (audience-filtered). */
    @GetMapping({"/api/v1/showcase", "/api/v1/showcase/"})
    public ResponseEntity<GatewayModel> showcase(
            @RequestParam(required = false) String audience
    ) {
        return ResponseEntity.ok(gatewayService.modelFor(audience));
    }

    private ResponseEntity<byte[]> html() {
        byte[] body = gatewayService.html();
        if (body == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("gateway html unavailable".getBytes());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .body(body);
    }
}
