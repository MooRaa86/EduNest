# Phase 05 — Production Readiness (In-App)

## 1. Objective

The app becomes *operable* under the prod profile: health/readiness endpoints, structured logging with request correlation, rate limiting beyond the auth surfaces, config/secrets inventory ready for infra, and documentation claims reconciled with reality (the "books say, code does" drift DOC-01/02 tracks).

## 2. Scope

- **In scope:** Actuator (health/readiness/info, minimal exposure), logback + MDC + println removal, rate-limit rollout beyond auth, `.env.example`/config matrix handoff to Phase 6, docs parity (graduation books + README claims per D-11/D-16/D-21), roadmap-module decision execution (TD-06).
- **Out of scope:** infrastructure provisioning & secrets *storage* (P6-*), monitoring stacks (P8), rate-limit policy tuning for attack scenarios (P8-01).

## 3. Entry criteria

1. P4 core merged (health/logging shouldn't chase a moving target).
2. D-11/D-16/D-21 decided-or-defaulted.

## 4. Task index

| Task | Title | Pri | Size |
|---|---|---|---|
| [P5-01](tasks.md#p5-01) | Actuator: health/readiness/info with minimal exposure | P1 | S |
| [P5-02](tasks.md#p5-02) | Logging config: logback, MDC request IDs, println purge | P1 | M |
| [P5-03](tasks.md#p5-03) | Rate-limit rollout beyond auth surfaces | P2 | M |
| [P5-04](tasks.md#p5-04) | Config/secrets inventory & `.env.example` | P2 | S |
| [P5-05](tasks.md#p5-05) | Docs parity & product claims corrections | P2 | M |

## 5. Execution order & dependencies

1 → 2 → 3 (same-touch config files); 4/5 parallel small tasks. 5 must complete **before** P7-05 (go/no-go evidence includes claims parity).

## 6. Exit criteria

1. `/actuator/health` (public, no read-DB? no — health includes DB check per spring default, verify) and readiness usable by P6-05/P7-07 runbook.
2. Every response traceable by request ID in logs; prod logs contain no `System.out` noise.
3. `README`/graduation books/roadmap claims corrected per decisions; no "PDF certificates" or "Agora" claims unless per D-16/D-11 outcomes.
4. Details: [`acceptance-criteria.md`](acceptance-criteria.md).

## 7. Risks

Actuator exposure over-share — risk mitigated by task's minimal-exposure rule (R-none; documented healthinfo policy).
Logging change ripples into log parsing expectations (auditors/tools) — P8-02 will treat P5-02's config as baseline.

## 8. Definition of done (phase level)

Acceptance signed; claims parity recorded in P7-05 checklist dependency.
