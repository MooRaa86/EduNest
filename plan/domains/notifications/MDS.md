# Domain: Notifications incl. platform mail — Implementation Guide

Canonical audit sources: [`mds/notifications/MDS.md`](../../../mds/notifications/MDS.md), guest/contact flows, platform mail facts in [`mds/api/endpoints.md`](../../../mds/api/endpoints.md) (bare `/send` bean-name route) and [`mds/integrations/MDS.md`](../../../mds/integrations/MDS.md) (MAIL-01), coverage in [`mds/deployment/production-readiness.md`](../../../mds/deployment/production-readiness.md) ops rows.

*(Full relative paths: `../../../mds/notifications/MDS.md`, `../../../mds/api/endpoints.md`, `../../../mds/integrations/MDS.md`.)*

## 1. Purpose & components owned

`NotificationServiceImpl` push fan-out (user-targeted + admin), email templates (badge/contact flows), `PlatformMailController` (the `@RestController("/mail")` bare-`/send` quirk), `ConversationEventListener` queue pushes, OTT/email-verification senders' error behavior, failed-email observability.

## 2. Review checklist (mandatory for any PR here)

- [ ] Pushes only after DB commit (afterCommit pattern; P4-01/P4-02/P4-03 hooks).
- [ ] Fan-out lists computed from roles/rows per D-12 (never "newest admin" style shortcuts).
- [ ] User-targeted pushes absent for guest flows (guard, never 404-crash).
- [ ] Endpoints that accept payloads for *sending* are admin-gated or removed (SEL-10/MAIL-01 closure — default D-05 = gate).
- [ ] Email templates escape merged user fields like the badge flow does (SEL-15 pattern).

## 3. Implementation requirements (per canonical issue)

| Issue | Work | Task |
|---|---|---|
| SEL-10 + NOTIF-01 (G10) | Gate `/api/v1/notifications/send` (+ admin variant per D-05) | [P1-11](../../phases/phase-01-security-stabilization/tasks.md#p1-11) |
| MAIL-01 | Gate bare `POST /send` platform mailbox per D-05 | [P1-11](../../phases/phase-01-security-stabilization/tasks.md#p1-11) |
| NOTIF-02/03 | Guest-contact push guard + afterCommit timing | [P4-01](../../phases/phase-04-business-logic-and-notifications/tasks.md#p4-01) |
| NOTIF-04 + USR-05 | Broadcast per D-12 | [P4-01](../../phases/phase-04-business-logic-and-notifications/tasks.md#p4-01) |
| NOTIF-05 | Unused projection cleanup + bounds (with [P1-26](../../phases/phase-01-security-stabilization/tasks.md#p1-26) where applicable) | [P4-01](../../phases/phase-04-business-logic-and-notifications/tasks.md#p4-01) |
| MSH-10 | Completion-flow notification side-effects | [P4-03](../../phases/phase-04-business-logic-and-notifications/tasks.md#p4-03) |
| SEL-15 (V-02) | Contact-reply escaping | [P1-24](../../phases/phase-01-security-stabilization/tasks.md#p1-24) / [P0-04](../../phases/phase-00-planning-baseline/tasks.md#p0-04) |
| INT-02 | Failed-email persistence + retries | [P4-13](../../phases/phase-04-business-logic-and-notifications/tasks.md#p4-13) |

*(Full task links as listed in `ISSUE-MAPPING.md` — the mapping file is the authoritative cross-ref; these boxes abbreviate.)*

## 4. Definition of done (domain)

Rows owned here on [`ISSUE-MAPPING.md`](../../ISSUE-MAPPING.md) Verified; `notifications/MDS.md` updated w/ afterCommit semantics + D-12 fan-out rule; push endpoints admin-gated or removed per recorded decision; guest flow never 404s on push attempt.
