# Secrets Management (P6-03 / EB-74)

> **Store amendment (2026-11-11, D-22):** the `docker secret` baseline from the original plan was written pre-Railway. With the D-22 pivot, **Railway's environment dashboard is the secret store** for the deployed container; Supabase manages the database and storage credentials it owns. Vault remains optional per scale (D-22) and is not adopted.

## Store topology

| Layer | Store | Holds |
|---|---|---|
| Production (Railway) | Railway dashboard, environment variables — injected into the container at boot, never baked into the image (`SecretsHygieneGuardTest` enforces the Dockerfile bake rule) | `JWT_SECRET`, `DATABASE_PASSWORD`, `MAIL_*`, `ZOOM_CLIENT_SECRET`, `SUPABASE_S3_*` |
| Database / storage | Supabase project dashboard (project `ciogysvcykzxrjuskxmk`) | DB password (source of truth), S3 access-key pairs |
| Local development | `.env` copied from [`../../.env.example`](../../.env.example) — **gitignored, restricted perms; each developer generates their own values** | same variables, personal/dev values |

## Rules (non-negotiable)

1. **No secret values in git, image layers, compose defaults, logs, or test fixtures** — ever.
2. Values live only in: Railway/Supabase dashboards, or a developer's local `.env` (gitignored).
3. A new secret must be registered in **three places** before use: `.env.example` (template), the [secret inventory](secrets-inventory.md), and the canonical matrix (`mds/deployment/environment-configuration.md` §8). `EnvContractGuardTest` + `SecretsHygieneGuardTest` fail the build otherwise.
4. Rotation follows the [rotation procedures](rotation.md). JWT rotation additionally requires the [generation record](JWT-GENERATION-RECORD.md) to be updated.
5. **R1 ordering rule (plan/RISKS §2):** real `JWT_SECRET` provisioning/rotation only after the `JwtSecretProvider` (P1-01/SEL-01) code that honors it is deployed. That gate is satisfied since P1-01 merged — the deployed app already refuses to boot without a valid secret.

## Who holds what

| Holder | Access | Notes |
|---|---|---|
| Repository admin (Omar) | Railway dashboard, Supabase dashboard, Zoom Marketplace app | sole holder of every production secret |
| Contributors (backend team) | none to production stores | local dev only; no real values needed |
| CI (GitHub Actions) | none | tests use placeholders/generated values; no production secrets as repo variables |

## Scratch values during rotation

`ops/secrets/local/` (gitignored) may temporarily hold the *previous* value of a secret during a rotation window (rollback lane). Values there are transient — the store of record is always the dashboard.

## Contents

- [`secrets-inventory.md`](secrets-inventory.md) — the secret-classified variable register (structure only, zero values)
- [`rotation.md`](rotation.md) — per-secret rotation procedures (stable + app restart)
- [`JWT-GENERATION-RECORD.md`](JWT-GENERATION-RECORD.md) — provenance record for `JWT_SECRET` (no values)
- [`new-jwt-secret.ps1`](new-jwt-secret.ps1) / [`new-jwt-secret.sh`](new-jwt-secret.sh) — cryptographically random secret generator (writes nothing to disk)
