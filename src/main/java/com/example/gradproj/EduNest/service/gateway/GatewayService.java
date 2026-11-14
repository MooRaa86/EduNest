package com.example.gradproj.EduNest.service.gateway;

import com.example.gradproj.EduNest.config.security.PublicRoutesPolicy;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * [EB-112][P5-06] Builds and caches the root-gateway showcase model.
 *
 * Sourcing discipline — everything shown is either:
 *  <ul>
 *    <li><b>Live runtime data</b>: version (build-info), environment
 *        (active profiles), commit (deploy env), REST surface
 *        (reflected from RequestMappingHandlerMapping + PublicRoutesPolicy),</li>
 *    <li><b>Verified architecture facts</b>: the WebSocket contract is taken
 *        from WebSocketConfig / JwtChannelInterceptor / ChatWebSocketController
 *        / ConversationWebSocketControllers and pinned by tests,
 *        and the process rules mirror the documented sprints.</li>
 *  </ul>
 * The model contains no secrets and no PII (asserted in GatewayServiceTest).
 *
 * Content negotiation contract (GatewayController):
 *  - GET /            → HTML showcase for browsers, JSON model for API clients
 *  - ?audience=hr|business|technical (default: technical)
 */
@Slf4j
@Service
public class GatewayService {

    private static final String AUDIENCE_HR = "hr";
    private static final String AUDIENCE_BUSINESS = "business";
    private static final String AUDIENCE_TECHNICAL = "technical";
    private static final List<String> KNOWN_AUDIENCES =
            List.of(AUDIENCE_HR, AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL);

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider;
    private final ObjectProvider<BuildProperties> buildPropertiesProvider;
    private final Environment environment;

    /**
     * {@code requestMappingHandlerMapping} is qualified explicitly: the
     * Actuator endpoint mapping ({@code controllerEndpointHandlerMapping})
     * is a second RequestMappingHandlerMapping bean, and an unqualified
     * ObjectProvider would be ambiguous.
     */
    public GatewayService(
            @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
            ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider,
            ObjectProvider<BuildProperties> buildPropertiesProvider,
            Environment environment) {
        this.handlerMappingProvider = handlerMappingProvider;
        this.buildPropertiesProvider = buildPropertiesProvider;
        this.environment = environment;
    }

    private volatile GatewayModel baseModel;
    private volatile byte[] gatewayHtml;

    @PostConstruct
    void buildModel() {
        this.baseModel = new GatewayModel(
                "EduNest Backend",
                "Cohort-based mentorship & experiential learning platform",
                "Enterprise backend enforcing structured multi-week sprints, "
                        + "milestone submissions, timed assessments with late-penalty "
                        + "mechanics, live coaching with verified attendance, gamified "
                        + "point ledgers, durable completion verification, and exactly-once "
                        + "enrollment payments.",
                version(),
                environment(),
                commit(),
                domains(),
                workflows(),
                diagrams(),
                websocketContract(),
                techStack(),
                metrics(),
                apiSurface()
        );
        this.gatewayHtml = loadHtml();
        log.info("EB-112: gateway showcase model built ({} domains, {} diagrams, {} API endpoints)",
                baseModel.domains().size(), baseModel.diagrams().size(), baseModel.apiSurface().size());
    }

    // ===================== public API =====================

    /** Full (technical) model. */
    public GatewayModel model() {
        return baseModel;
    }

    /** Model filtered for an audience; unknown/absent → technical. */
    public GatewayModel modelFor(String audience) {
        String a = normalizeAudience(audience);
        if (AUDIENCE_TECHNICAL.equals(a)) return baseModel;
        boolean hrOnly = AUDIENCE_HR.equals(a);
        return new GatewayModel(
                baseModel.service(),
                baseModel.tagline(),
                baseModel.description(),
                baseModel.version(),
                baseModel.environment(),
                baseModel.commit(),
                baseModel.domains().stream()
                        .filter(d -> d.audiences().contains(a))
                        .map(d -> hrOnly
                                ? new GatewayModel.Domain(d.id(), d.title(), d.audiences(), d.summary(), List.of())
                                : d)
                        .toList(),
                baseModel.workflows().stream()
                        .filter(w -> w.audiences().contains(a))
                        .toList(),
                baseModel.diagrams().stream()
                        .filter(dg -> dg.audiences().contains(a))
                        .toList(),
                hrOnly ? null : baseModel.websocket(),
                hrOnly ? List.of() : baseModel.techStack(),
                baseModel.metrics(),
                hrOnly ? List.of() : baseModel.apiSurface()
        );
    }

    /** Audience query parameter handling: unknown/absent → technical. */
    public static String normalizeAudience(String audience) {
        if (audience == null) return AUDIENCE_TECHNICAL;
        String a = audience.toLowerCase(Locale.ROOT).trim();
        return KNOWN_AUDIENCES.contains(a) ? a : AUDIENCE_TECHNICAL;
    }

    /** Self-contained HTML shell (cached at startup). */
    public byte[] html() {
        return gatewayHtml;
    }

    // ===================== runtime data =====================

    private String version() {
        BuildProperties props = buildPropertiesProvider.getIfAvailable();
        return props != null ? props.getVersion() : "unreleased";
    }

    private String environment() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length > 0 ? String.join(",", profiles) : "default";
    }

    private String commit() {
        // [P6-08/EB-79 parity] Prefer the same source /actuator/info uses:
        // git.properties (git-commit-id plugin) → deploy env → local marker.
        String abbrev = environment.getProperty("git.commit.id.abbrev");
        if (abbrev != null && !abbrev.isBlank()) return abbrev;
        String sha = System.getenv("RAILWAY_GIT_COMMIT_SHA");
        if (sha == null || sha.isBlank()) sha = System.getenv("GIT_COMMIT");
        return (sha == null || sha.isBlank()) ? "local" : sha.substring(0, Math.min(12, sha.length()));
    }

    /**
     * Live REST surface: reflected from RequestMappingHandlerMapping at
     * startup. Only /api/** GET endpoints are listed, with the public flag
     * computed from PublicRoutesPolicy (never hardcoded).
     */
    private List<GatewayModel.ApiEndpoint> apiSurface() {
        RequestMappingHandlerMapping mapping = handlerMappingProvider.getIfAvailable();
        if (mapping == null) return List.of();
        List<GatewayModel.ApiEndpoint> out = new ArrayList<>();
        mapping.getHandlerMethods().forEach((info, handlerMethod) -> {
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/api/")) continue;
                String method = info.getMethodsCondition().getMethods().isEmpty()
                        ? "ALL"
                        : info.getMethodsCondition().getMethods().iterator().next().name();
                boolean isPublic = info.getMethodsCondition().getMethods().stream()
                        .allMatch(m -> PublicRoutesPolicy.isPublicRoute(pattern));
                out.add(new GatewayModel.ApiEndpoint(
                        method,
                        pattern,
                        isPublic,
                        handlerMethod.getBeanType().getSimpleName()));
            }
        });
        out.sort((x, y) -> x.path().compareToIgnoreCase(y.path()));
        return List.copyOf(out);
    }

    private byte[] loadHtml() {
        try {
            return new ClassPathResource("gateway/gateway.html")
                    .getInputStream().readAllBytes();
        } catch (Exception e) {
            log.error("EB-112: gateway.html missing from classpath — HTML view disabled", e);
            return ("<html><body><h1>EduNest Gateway</h1>"
                    + "<p>HTML view unavailable — the JSON model remains available at /api/v1/showcase.</p>"
                    + "</body></html>").getBytes(StandardCharsets.UTF_8);
        }
    }

    // ===================== verified architecture content =====================

    private List<GatewayModel.Domain> domains() {
        return List.of(
                new GatewayModel.Domain("identity-auth", "Identity, Authentication & Security Governance",
                        List.of(AUDIENCE_HR, AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        "One account system with three personas (student, mentor, admin), verified onboarding and full audit traceability.",
                        List.of(
                                "Polymorphic identity: unified UserEntity base with specialized Student / Mentor / Admin personas.",
                                "Cryptographic OTP onboarding: email-based 6-digit verification codes with TTL expiration and OTP type isolation.",
                                "Stateless JWT (HMAC-signed) + database-backed refresh-token sessions with rotation and replay mitigation.",
                                "Fail-closed security matrix: PublicRoutesPolicy + ProjectSecurityConfig as single source of truth; AuthRateLimitFilter for brute-force protection; UploadPolicy blocks path traversal.",
                                "End-to-end traceability: MDC-backed correlation IDs (X-Correlation-ID) across every request cycle.",
                                "Ownership verified server-side on every request — WebSocket and storage paths included (Sprint-1 IDOR sweep).")),
                new GatewayModel.Domain("curriculum", "Mentorship Curriculum & Scaffolding",
                        List.of(AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        "Mentors build structured multi-week programs — not static video dumps — with drafts invisible to students until publish.",
                        List.of(
                                "Mentorships partition into sequential Week containers holding lectures, downloadable resources, milestone tasks, projects and quizzes.",
                                "DRAFT mentorships are confined to owning mentors and admins (EB-22) — unpublished material never leaks.",
                                "Server-side pricing: effective price, platform commission and mentor earning are computed and snapshotted at enrollment (never client-trusted).")),
                new GatewayModel.Domain("submissions", "Milestone Submissions & Grading Engine",
                        List.of(AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        "Students submit deliverables for every milestone; mentors grade with feedback that feeds the point system.",
                        List.of(
                                "TaskSubmission and ProjectSubmission pipelines with multi-format file attachments (5 MB caps, magic-byte validation).",
                                "Hybrid cloud storage: AWS SDK v2 streaming to Supabase S3-compatible storage with local-disk fallback for offline development.",
                                "Ownership checks on every upload read path; public/private exposure split by category.")),
                new GatewayModel.Domain("assessment", "Assessment Engine & Late Submissions (EB-53)",
                        List.of(AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        "Timed quizzes with server-decided deadlines and fair, transparent late penalties.",
                        List.of(
                                "Strict quiz duration windows (durationMinutes) with server-side start/deadline calculation — the student's clock is not the source of truth.",
                                "Late penalty mechanics: progressive deduction (-1 pt/min) within a grace period.",
                                "Automated sweeper: a @Scheduled job auto-closes expired quiz attempts.")),
                new GatewayModel.Domain("live-coaching", "Synchronous Live Coaching & Attendance (EB-66)",
                        List.of(AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        "Mentors run live sessions through Zoom with verified attendance that earns engagement points.",
                        List.of(
                                "Server-to-Server Zoom OAuth integration (D-16) generating join URLs automatically.",
                                "Duration tracking + verified attendee records (SessionAttendance) awarding engagement bonus points.",
                                "Auto-lifecycle: session states transition by server rules (EB-57), not by memory.")),
                new GatewayModel.Domain("gamification", "Gamification Ledger & Badges (EB-51)",
                        List.of(AUDIENCE_HR, AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        "Real-time points, cohort leaderboards and achievement badges keep students engaged and measurable.",
                        List.of(
                                "Atomic, append-only points ledger (TotalPoints) — points are never mutated or deleted, preserving trust in the record.",
                                "Scoped cohort leaderboards with privacy guards (no email exposure).",
                                "Automated achievement triggers + admin-curated badge awards.")),
                new GatewayModel.Domain("certification", "Durable Completion & Certification (EB-52)",
                        List.of(AUDIENCE_HR, AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        "Finishing a program produces a tamper-proof, verifiable certificate — automatically.",
                        List.of(
                                "Multi-stage background orchestration: ENQUEUED → CERTIFICATES → NOTIFICATIONS → DONE.",
                                "Cryptographic idempotency: deterministic name-based UUID hashing guarantees zero duplicate certificates.",
                                "Historical point freezing at graduation preserves the achievement record.")),
                new GatewayModel.Domain("realtime-chat", "Real-Time Chat & Notification Engine (EB-19/EB-20/EB-50)",
                        List.of(AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        "Cohort rooms and private 1:1 messaging in real time, with durable in-app notifications.",
                        List.of(
                                "STOMP WebSocket broker (/ws, /topic, /queue, /user) for cohort rooms and private direct messages.",
                                "Spoofing prevention: JwtChannelInterceptor binds authenticated identities at transport level; room read/write rules enforced on SUBSCRIBE and SEND.",
                                "Server-derived event routing: the server decides where messages go — client-supplied routing is never trusted.",
                                "Durable notifications: DB row written inside the transaction (source of truth), live push only after commit (sendAfterCommit, EB-50).",
                                "Audit-safe soft deletes redact sensitive message content while preserving conversational continuity.")),
                new GatewayModel.Domain("finance-commissions", "Financial Management & Commissions (EB-47/EB-108)",
                        List.of(AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        "Enrollment payments with a governed platform commission and exactly-once settlement.",
                        List.of(
                                "Commission governance: Day-1 seeding of the default 10% commission via idempotent Flyway migration (V4); admin-adjustable with bounds.",
                                "Stripe Checkout integration (D-28): server-created sessions, server-side money math, amount snapshots.",
                                "Webhooks: HMAC fail-closed verification, exactly-once enrollment (duplicate → 409), unknown events answered 200.",
                                "Verified live: $100 − 20% → $80 charge, $8 platform, $72 mentor — all computed server-side.")),
                new GatewayModel.Domain("platform-ops", "Operational Infrastructure & CI/CD (EB-72..EB-80)",
                        List.of(AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        "Every change is gated by a fully automated, no-skip pipeline before it reaches production.",
                        List.of(
                                "121 test suites comprising 858 automated tests against PostgreSQL 17 Testcontainers (sprint-2 CI baseline: 0 failures, 106 deliberate skips).",
                                "No-skip CI enforcement: @Disabled/@Ignore fails the build — green means something.",
                                "Fail-closed staging pipeline: GitHub Actions with migration gates and DB-identity guards (deploy-gate.sh).",
                                "Schema owned by Flyway; Hibernate validates at boot (fail-fast on drift).",
                                "Observability: Logback + MDC request IDs, Actuator health/readiness with minimal exposure."))
        );
    }

    private List<GatewayModel.Workflow> workflows() {
        return List.of(
                new GatewayModel.Workflow("student-journey", "The Student Journey",
                        List.of(AUDIENCE_HR, AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        List.of(
                                "Register with email → receive a 6-digit OTP (expires in minutes) → verify.",
                                "Explore published mentorships (DRAFT confinement enforced server-side).",
                                "Join: free mentorships enroll instantly; paid ones return a Stripe Checkout URL.",
                                "Learn week by week: lectures, resources, milestone tasks, projects, timed quizzes.",
                                "Submit deliverables → mentor grades → points awarded to the ledger.",
                                "Complete all checkpoints → background worker issues a verifiable certificate."),
                        null),
                new GatewayModel.Workflow("enrollment-payment", "Paid Enrollment & Exactly-Once Settlement",
                        List.of(AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        List.of(
                                "POST /join → server creates a Stripe Checkout Session and returns checkoutUrl.",
                                "Student pays on Stripe's hosted page ($80 after discount in the live verification).",
                                "Stripe calls /api/v1/payments/webhook → HMAC signature verified fail-closed.",
                                "Transaction PENDING → PAID; enrollment created exactly once (repeat join → 409).",
                                "Unknown events answered 200; replays are no-ops — idempotency lives in the consumer."),
                        null),
                new GatewayModel.Workflow("quiz-late", "Timed Quiz & Late Penalty",
                        List.of(AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        List.of(
                                "Quiz opens with server-calculated start/deadline from durationMinutes.",
                                "On-time submission → full scoring.",
                                "Late submission within grace → progressive -1 pt/min deduction.",
                                "After the window → a scheduled sweeper auto-closes the attempt."),
                        "quiz-late-penalty"),
                new GatewayModel.Workflow("completion", "Completion & Certificate Issuance",
                        List.of(AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        List.of(
                                "Enrollment reaches its completion checkpoints.",
                                "Background worker: ENQUEUED → CERTIFICATES → NOTIFICATIONS → DONE.",
                                "Deterministic UUID (name-hash of enrollment + secret) → zero duplicate certificates.",
                                "Points statement frozen to preserve historical achievement."),
                        "completion"),
                new GatewayModel.Workflow("live-attendance", "Live Zoom Session & Verified Attendance",
                        List.of(AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        List.of(
                                "Mentor schedules a live session → Zoom S2S OAuth creates the meeting + join URL.",
                                "Students attend; duration and attendance are recorded (SessionAttendance).",
                                "Verified attendees earn engagement bonus points in the ledger."),
                        "zoom-attendance")
        );
    }

    private List<GatewayModel.Diagram> diagrams() {
        return List.of(
                new GatewayModel.Diagram("system-context", "C4 Level 1 — System Context",
                        List.of(AUDIENCE_HR, AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        """
                        flowchart LR
                            S["🎓 Student"] -->|enroll, learn, submit| EDUNEST
                            M["🧑‍🏫 Mentor"] -->|publish, grade, coach| EDUNEST
                            A["🛡️ Admin"] -->|govern, adjust| EDUNEST
                            EDUNEST["EduNest Backend<br/>(Spring Boot monolith)"]
                            EDUNEST -->|checkout & webhooks| STRIPE["Stripe<br/>(payments)"]
                            EDUNEST -->|S2S OAuth| ZOOM["Zoom<br/>(live sessions)"]
                            EDUNEST -->|S3 API| SUPA["Supabase<br/>(PostgreSQL + Storage)"]
                            EDUNEST -->|email outbox| MAIL["SMTP / Email API<br/>(EB-116 seam)"]
                        """),
                new GatewayModel.Diagram("container-architecture", "C4 Level 2 — Container Architecture",
                        List.of(AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        """
                        flowchart TB
                            subgraph CLIENT["Clients"]
                                FE["Frontend (React)"]
                                MOB["API consumers"]
                            end
                            subgraph BACKEND["EduNest Backend (single deployable)"]
                                REST["REST Controllers<br/>/api/v1/**"]
                                WS["STOMP WebSocket<br/>/ws"]
                                SVC["Service Layer<br/>(business rules + ownership)"]
                                GATE["Integration Gateways<br/>Stripe / Zoom / Email / S3<br/>(provider seam + enabled flag)"]
                                SCHED["@Scheduled workers<br/>quiz sweeper / completion / retry"]
                            end
                            DB[("Supabase PostgreSQL<br/>Flyway-managed schema")]
                            OBJ[("Supabase Storage (S3)")]
                            FE -->|HTTPS REST| REST
                            FE -->|native WebSocket + JWT| WS
                            MOB -->|HTTPS| REST
                            REST --> SVC
                            WS --> SVC
                            SVC --> GATE
                            SVC --> DB
                            GATE --> OBJ
                            SCHED --> DB
                        """),
                new GatewayModel.Diagram("layered-modules", "Layered Modules & Dependency Direction",
                        List.of(AUDIENCE_TECHNICAL),
                        """
                        flowchart TD
                            CTRL["controller/**<br/>(HTTP + WebSocket entry)"] --> SVC["service/**<br/>(business logic, authorization)"]
                            SVC --> REPO["repository/**<br/>(Spring Data)"]
                            SVC --> INT["service/integration/**<br/>(provider seams)"]
                            REPO --> ENT["entity/**<br/>(Flyway-owned schema)"]
                            CFG["config/**<br/>(security, WS, storage, correlation)"] -.-> CTRL
                            CFG -.-> SVC
                            SEC["security filters<br/>(JWT validator, rate limit)"] -.-> CTRL
                        """),
                new GatewayModel.Diagram("er-diagram", "Entity Relationship Diagram (core domain)",
                        List.of(AUDIENCE_TECHNICAL),
                        """
                        erDiagram
                            USERS ||--o| STUDENTS : "persona"
                            USERS ||--o| MENTORS : "persona"
                            MENTORS ||--o{ MENTORSHIPS : publishes
                            MENTORSHIPS ||--o{ WEEKS : "structured by"
                            WEEKS ||--o{ LECTURES : contains
                            WEEKS ||--o{ TASKS : contains
                            WEEKS ||--o{ PROJECTS : contains
                            WEEKS ||--o{ QUIZZES : contains
                            STUDENTS ||--o{ ENROLLMENTS : joins
                            MENTORSHIPS ||--o{ ENROLLMENTS : "enrolled in"
                            TASKS ||--o{ TASK_SUBMISSIONS : receives
                            ENROLLMENTS ||--o{ TASK_SUBMISSIONS : owns
                            QUIZZES ||--o{ QUIZ_ATTEMPTS : "attempted via"
                            ENROLLMENTS ||--o| TOTAL_POINTS : "ledger"
                            ENROLLMENTS ||--o{ PAYMENT_TRANSACTIONS : "settled by"
                            USERS ||--o{ CHAT_MESSAGES : "sends"
                            USERS ||--o{ NOTIFICATIONS : "receives"
                        """),
                new GatewayModel.Diagram("quiz-late-penalty", "Sequence — Timed Quiz Late Penalty (EB-53)",
                        List.of(AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        """
                        sequenceDiagram
                            participant St as Student
                            participant B as Backend
                            participant DB as PostgreSQL
                            St->>B: start quiz attempt
                            B->>DB: store attempt + server deadline (durationMinutes)
                            St->>B: submit answers
                            alt on time
                                B->>DB: score full
                            else late within grace
                                B->>DB: score minus (-1 pt / minute late)
                            else after window
                                B-->>St: rejected (closed)
                            end
                            Note over B,DB: @Scheduled sweeper auto-closes expired attempts
                        """),
                new GatewayModel.Diagram("completion", "Sequence — Completion & Certificate Issuance (EB-52)",
                        List.of(AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        """
                        sequenceDiagram
                            participant W as Completion Worker
                            participant DB as PostgreSQL
                            participant N as Notification Service
                            W->>DB: enrollment complete → ENQUEUED
                            W->>DB: issue certificate (deterministic UUID)
                            Note right of W: UUID.nameUUIDFromBytes(enrollment + secret)<br/>= zero duplicates, re-runnable
                            W->>DB: state → CERTIFICATES
                            W->>N: notify student (row in tx, push after commit)
                            W->>DB: freeze historical points → NOTIFICATIONS → DONE
                        """),
                new GatewayModel.Diagram("zoom-attendance", "Sequence — Live Session Attendance (EB-66)",
                        List.of(AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        """
                        sequenceDiagram
                            participant M as Mentor
                            participant B as Backend
                            participant Z as Zoom (S2S OAuth)
                            participant St as Student
                            M->>B: schedule live session
                            B->>Z: create meeting (S2S OAuth, minimal scopes)
                            Z-->>B: join URL
                            B-->>St: session link
                            St->>Z: attends
                            B->>B: record SessionAttendance (duration verified)
                            B->>B: award engagement points to ledger
                        """),
                new GatewayModel.Diagram("quiz-state", "State — Quiz Attempt Lifecycle",
                        List.of(AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        """
                        stateDiagram-v2
                            [*] --> NOT_STARTED
                            NOT_STARTED --> IN_PROGRESS: student starts (server deadline set)
                            IN_PROGRESS --> SUBMITTED_ON_TIME: submit before deadline
                            IN_PROGRESS --> SUBMITTED_LATE: submit within grace (-1 pt/min)
                            IN_PROGRESS --> AUTO_CLOSED: deadline passed (sweeper)
                            SUBMITTED_ON_TIME --> GRADED: mentor evaluation + points
                            SUBMITTED_LATE --> GRADED: penalized score + points
                            AUTO_CLOSED --> [*]
                        """),
                new GatewayModel.Diagram("deployment", "Deployment Topology (CI/CD)",
                        List.of(AUDIENCE_BUSINESS, AUDIENCE_TECHNICAL),
                        """
                        flowchart LR
                            DEV["Developer PR"] --> CI["GitHub Actions<br/>build + test (no-skip) + package + smoke"]
                            CI -->|green, approved| MAIN["merge to main"]
                            MAIN --> STG["Staging: migration gate + deploy-gate<br/>(DB identity guard)"]
                            MAIN --> PROD["Production (Railway auto-deploy)<br/>migration gate automated by EB-115"]
                            STG --> DBP[("Supabase PostgreSQL<br/>(staging project)")]
                            PROD --> DBR[("Supabase PostgreSQL<br/>(production project)")]
                            PROD --> OBJ[("Supabase Storage")]
                        """)
        );
    }

    /**
     * The WebSocket contract, sourced live from the implemented classes.
     * Canonical vs tolerated auth behavior is pinned by
     * WsConnectAuthContractTest — the wording below must not drift from it.
     */
    private GatewayModel.WebSocketIntegration websocketContract() {
        return new GatewayModel.WebSocketIntegration(
                "/ws  (native WebSocket path: /ws/websocket)",
                "Native WebSocket transport ONLY for authenticated sessions. SockJS fallback "
                        + "transports cannot authenticate — the handshake interceptor was removed in EB-19 — "
                        + "so a SockJS client must be pinned to transports: ['websocket']. The integration "
                        + "suite connects on ws://host/ws/websocket.",
                new GatewayModel.ConnectAuth(
                        "Authorization — native STOMP header on the CONNECT frame (not an HTTP header on the upgrade request).",
                        "Raw JWT, no 'Bearer ' prefix — the FE contract used by WsStompIntegrationTestBase and mds/websocket/MDS.md §6.",
                        "A 'Bearer ' prefix is ALSO accepted: JwtService#isTokenValid strips it before parsing (verified in source).",
                        "Missing/invalid token fails the CONNECT itself (connection-level rejection). No principal exists yet, "
                                + "so there is no error queue to explain it — the client sees the connection refuse.",
                        "ChatPrincipal(email, fullName, userId) is rebuilt from token claims per frame; user destinations "
                                + "(/user/...) resolve automatically from the bound email — no per-user destination guessing."),
                List.of(
                        new GatewayModel.WsDestination("/user/queue/notifications", "subscribe",
                                "In-app notifications (mentor-notify, admin broadcasts): {title, content, type, isRead, time}. "
                                        + "Row is written inside the transaction (source of truth); live push fires after commit (EB-50).",
                                null, "Authenticated CONNECT"),
                        new GatewayModel.WsDestination("/user/queue/messages", "subscribe",
                                "Private 1:1 conversation messages — ConversationMessageResponse {id, conversationId, senderId, "
                                        + "senderEmail, senderName, senderProfileImageUrl, recipientId, recipientEmail, content, sentAt}. "
                                        + "Both the recipient AND the sender receive it (sender echo = instant UI update).",
                                null, "Authenticated CONNECT"),
                        new GatewayModel.WsDestination("/user/queue/errors", "subscribe",
                                "WebSocket authorization violations as authored text, e.g. 'Forbidden: you are not a member of this "
                                        + "room' or 'Invalid destination format'. Violating frames are DROPPED; the session stays alive.",
                                null, "Authenticated CONNECT"),
                        new GatewayModel.WsDestination("/topic/room/{roomId}", "subscribe",
                                "Cohort room chat — ChatMessageResponse {id, message, senderName, senderEmail, senderProfileImageUrl, "
                                        + "roomId, time}. The server publishes to BOTH the numeric and UUID forms of the roomId, so "
                                        + "subscribe with the id form you hold and you will receive the message.",
                                null, "READ rule: ChatAuthorization.canReadRoom on SUBSCRIBE")),
                List.of(
                        new GatewayModel.WsDestination("/app/chat/{roomId}", "send",
                                "Send a cohort room message.",
                                "{\"content\": \"your message\"} — a bare string body is rejected by the payload converter. "
                                        + "roomId accepts numeric Long OR UUID publicId.",
                                "WRITE rule: ChatAuthorization.canWriteRoom on SEND"),
                        new GatewayModel.WsDestination("/app/chat.private", "send",
                                "Send a private 1:1 message (sender↔recipient pair validated in ConversationService).",
                                "{\"recipientEmail\": \"user@example.com\", \"recipientId\": \"uuid-or-null\", \"content\": \"text\"}",
                                "Authenticated CONNECT only (room prefix rule does not match /app/chat.private)")
                ),
                "Authorization violations on SUBSCRIBE/SEND are dropped and explained on /user/queue/errors — never raw STOMP ERROR "
                        + "frames. This is deliberate: interceptor exceptions never reach the wire on the async client inbound channel "
                        + "(proven by the EB-39 socket matrix).",
                "Reconnect with the same CONNECT contract (fresh JWT in the Authorization native header). Recommended STOMP "
                        + "heartbeats 20000/20000 ms (as used by the integration suite)."
        );
    }

    private List<GatewayModel.TechItem> techStack() {
        return List.of(
                new GatewayModel.TechItem("Framework", "Spring Boot (Web MVC, Security, Validation, Actuator)",
                        "Single-deployable monolith with a fail-fast production profile."),
                new GatewayModel.TechItem("Database", "PostgreSQL 17 on Supabase + Flyway + Spring Data JPA",
                        "Schema owned by versioned migrations; Hibernate validates at boot (fail-fast on drift)."),
                new GatewayModel.TechItem("Real-time", "Spring WebSocket + STOMP (SockJS endpoint, native-WS auth)",
                        "Transport-level JWT binding and server-derived routing."),
                new GatewayModel.TechItem("Payments", "Stripe SDK (Checkout + webhooks)",
                        "HMAC fail-closed, exactly-once enrollment, server-side money math (test mode first per D-28)."),
                new GatewayModel.TechItem("Meetings", "Zoom Server-to-Server OAuth (D-16)",
                        "Minimal scopes, credentials via secret store, behind the provider seam."),
                new GatewayModel.TechItem("Storage", "Supabase Storage (S3) via AWS SDK v2 + local-disk fallback",
                        "Public/private bucket split; ownership enforced server-side."),
                new GatewayModel.TechItem("Auth", "JJWT (HMAC-signed stateless JWT) + DB-backed refresh rotation",
                        "Rotation + revocation close the stolen-token residual risk (EB-91)."),
                new GatewayModel.TechItem("Email", "JavaMail SMTP with a bounded-retry outbox (EB-62)",
                        "HTTP API provider seam planned (EB-116) — Railway blocks outbound SMTP on non-Pro plans."),
                new GatewayModel.TechItem("Testing", "JUnit 5 + Testcontainers PostgreSQL 17 + STOMP probes",
                        "No-skip CI: 858 tests, green-only merges; the G-matrix locks authorization regressions."),
                new GatewayModel.TechItem("CI/CD", "GitHub Actions → GHCR → Railway with deploy gates",
                        "DB-identity guards; production migration gate automated by EB-115."),
                new GatewayModel.TechItem("Observability", "Logback + MDC correlation IDs + Actuator (minimal)",
                        "Every request traceable; health/readiness public, everything else locked."),
                new GatewayModel.TechItem("API docs", "springdoc OpenAPI (Swagger UI)",
                        "Public contract surface for the frontend team.")
        );
    }

    private GatewayModel.Metrics metrics() {
        return new GatewayModel.Metrics(
                923,
                106,
                0,
                "Sprint-2 CI baseline (2026-11-14): 923 run / 0 failures / 106 deliberate skips "
                        + "(conditional & platform-specific excludes). No-skip enforcement: disabling a test fails the build."
        );
    }
}
