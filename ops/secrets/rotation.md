# Secret Rotation Procedures (P6-03 / EB-74)

> Every rotation: **stable + app restart**. One secret at a time. Verify health after each step. The previous value is kept in `ops/secrets/local/` (gitignored) for the duration of the rotation window only — it is the rollback lane, not a second store.

## General ordering rules

1. **R1 (plan/RISKS §2):** `JWT_SECRET` is never provisioned or rotated before the deployed code honors it via `JwtSecretProvider`. Satisfied since P1-01/SEL-01 merged — do not regress it.
2. Rotate in a quiet window; every JWT rotation logs out all users (re-auth window — same operational pattern as the P1-20/D-01 session-window note).
3. After any Railway variable change, Railway redeploys the container — watch the deploy log for healthy boot, then hit the health endpoint.

---

## §1 — `JWT_SECRET` (Critical)

**Effect of rotation:** every issued token is immediately invalid; all users must re-authenticate (login + OTP flows unaffected — they mint fresh tokens).

1. Open `ops/secrets/local/` and save the **current** value there (rollback lane; never git).
2. Generate a fresh secret:
   - Windows: `powershell -ExecutionPolicy Bypass -File ops/secrets/new-jwt-secret.ps1`
   - macOS/Linux: `bash ops/secrets/new-jwt-secret.sh`
   - Copy the printed value — the scripts write **nothing** to disk.
3. Railway → EduNest service → Variables → set `JWT_SECRET` to the new value. The container redeploys automatically.
4. Verify: deploy log shows healthy startup (`JwtSecretProvider` accepts the secret — it would refuse to boot otherwise), `GET /actuator/health` returns UP, login mints a working token, an old token is rejected.
5. Update `JWT-GENERATION-RECORD.md`: new row (date, generator, method, length, provenance).
6. After a settled verification window (≥24 h), delete the old value from `ops/secrets/local/`.

**Rollback:** if boot or login fails, set the previous value back from `ops/secrets/local/` and redeploy.

## §2 — `DATABASE_PASSWORD` (High)

1. Supabase dashboard → Project Settings → Database → reset the password. Note it only in the dashboard flow (and optionally `ops/secrets/local/` until done).
2. Railway → Variables → update `DATABASE_PASSWORD`.
3. Verify: redeploy healthy; Flyway validation passes (`ddl-auto=validate`); app serves DB-backed requests.
4. **Paired note:** if any external integration holds DB credentials (scripts, `tools/migration` helpers, backup jobs from P6-02), update those stores in the same window. `tools/migration/snapshots/` is gitignored by design.

## §3 — `MAIL_PASSWORD` — Gmail app password (High)

1. Google Account → Security → App passwords → revoke the EduNest app password; generate a new one.
2. Railway → Variables → update `MAIL_PASSWORD`.
3. Verify: redeploy healthy; trigger the registration/OTP email path (or the cheapest real send) and confirm delivery.

## §4 — `ZOOM_CLIENT_SECRET` (High)

1. Zoom Marketplace → the EduNest Server-to-Server OAuth app → regenerate the client secret.
2. Railway → Variables → update `ZOOM_CLIENT_SECRET`.
3. Verify: redeploy healthy; with `ZOOM_ENABLED=true`, create a live meeting via the normal path (EB-66/EB-85 flow).

## §5 — Supabase S3 key pair (High) — rotate as a pair

1. Supabase dashboard → Storage → S3 access keys → **create a new key pair** (do not delete the old one yet).
2. Railway → Variables → update `SUPABASE_S3_ACCESS_KEY` and `SUPABASE_S3_SECRET_KEY` to the new pair.
3. Verify: redeploy healthy; exercise an upload (public and private bucket) and a signed/private read.
4. Only after verification: delete the old key pair in Supabase.
5. Storage code is **fail-closed** in prod (EB-83) — a wrong key shows up immediately as failed uploads, which is the smoke signal.

---

## §6 - `STRIPE_SECRET_KEY` (High) — Stripe Secret key

1. Stripe Dashboard → Developers → API keys → **Roll secret key** (test mode first; live keys gated to P7 provisioning, D-28).
2. Railway → Variables → update `STRIPE_SECRET_KEY` to the new key.
3. Verify: redeploy healthy; run a test-mode checkout end-to-end (join → pay with `4242…` test card → webhook → enrollment created).
4. The join flow **fails fast** with an incomplete/invalid key (EB-108 C decision: the money path never silently bypasses) — a wrong key shows up immediately at `/join` as a 500, which is the smoke signal.

## §7 - `STRIPE_WEBHOOK_SECRET` (High) — webhook signing secret

1. Stripe Dashboard → Developers → Webhooks → select the endpoint → **Roll signing secret** (or locally: restart `stripe listen` for a fresh `whsec_`).
2. Railway → Variables → update `STRIPE_WEBHOOK_SECRET` to the new secret.
3. Verify: send a test event (Dashboard → Send test webhook) and confirm the app answers 200; a stale secret answers **400** (fail-closed HMAC, EB-108) — that is the smoke signal, and Stripe stops retrying deliveries it cannot authenticate after enough 400s.
4. No enrollment impact from a wrong secret beyond delayed finalization: Stripe retries signature-failed deliveries, then the dashboard flags the endpoint.

---

## Post-rotation checklist (all rotations)

- [ ] Health endpoint UP after the automatic redeploy
- [ ] The affected feature verified end-to-end (login / email / upload / meeting / DB)
- [ ] Inventory row touched only if cadence or holder changed (structure never carries values)
- [ ] `JWT-GENERATION-RECORD.md` updated (JWT rotations only)
- [ ] Old value deleted from `ops/secrets/local/` after the verification window
