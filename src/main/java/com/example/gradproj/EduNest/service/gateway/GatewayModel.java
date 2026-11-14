package com.example.gradproj.EduNest.service.gateway;

import java.util.List;

/**
 * [EB-112][P5-06] Read-only showcase model served by the root-domain gateway.
 *
 * Pure data — assembled once at startup by {@link GatewayService} from real
 * runtime sources (request mappings, build properties, environment) plus the
 * documented architecture facts. Never contains secrets or PII (asserted by
 * GatewayServiceTest).
 *
 * Every nested record is Jackson-serializable as-is (records + List/Map only),
 * so the JSON view and the HTML showcase render from the single source.
 */
public record GatewayModel(
        String service,
        String tagline,
        String description,
        String version,
        String environment,
        String commit,
        List<Domain> domains,
        List<Workflow> workflows,
        List<Diagram> diagrams,
        WebSocketIntegration websocket,
        List<TechItem> techStack,
        Metrics metrics,
        List<ApiEndpoint> apiSurface
) {

    /** A showcase domain chapter. {@code audiences} drives the view filtering. */
    public record Domain(
            String id,
            String title,
            List<String> audiences,   // subset of: hr, business, technical
            String summary,           // plain language — shown to all audiences
            List<String> bullets      // technical detail — technical/business only
    ) {}

    /** A documented end-to-end workflow, optionally backed by a diagram id. */
    public record Workflow(
            String id,
            String name,
            List<String> audiences,
            List<String> steps,
            String diagramId
    ) {}

    /** A Mermaid diagram (code is a complete mermaid block, rendered client-side). */
    public record Diagram(
            String id,
            String title,
            List<String> audiences,
            String code
    ) {}

    /**
     * The real-time integration contract, sourced from WebSocketConfig,
     * JwtChannelInterceptor, ChatWebSocketController and
     * ConversationWebSocketControllers — verified against the implemented
     * code (facts pinned by tests, not aspirations).
     */
    public record WebSocketIntegration(
            String endpoint,
            String transportContract,
            ConnectAuth connectAuth,
            List<WsDestination> subscribe,
            List<WsDestination> send,
            String errorSemantics,
            String reconnectGuidance
    ) {}

    public record ConnectAuth(
            String header,
            String canonical,     // what the FE team should send
            String tolerated,     // what ALSO works server-side
            String failureMode,
            String principal
    ) {}

    public record WsDestination(
            String destination,
            String direction,     // "subscribe" | "send"
            String purpose,
            String payload,       // JSON shape or field list; null for pure topics
            String authRule
    ) {}

    public record TechItem(String area, String name, String why) {}

    public record Metrics(
            Integer testsRun,
            Integer testsSkipped,
            Integer testsFailing,
            String metricsNote
    ) {}

    /** One live REST endpoint reflected from RequestMappingHandlerMapping. */
    public record ApiEndpoint(
            String method,
            String path,
            boolean publicRoute,  // per PublicRoutesPolicy — computed, not hardcoded
            String controller
    ) {}
}
