# Restore Procedure (P6-02 / EB-73)

> The weekly-drill and incident path for Tier 2 dumps. Golden rule: **restore into a disposable target first, verify, then act on real data.** Schema changes are forward-only; restores recover *data*, not schema versions.

## Prerequisites

- A Tier 2 dump from `ops/backups/local/` (custom format, `.dump`) — see [`backup-policy.md`](backup-policy.md).
- PostgreSQL client tools (`pg_restore`, `psql`) with a **server version ≥ the dump's source version**.
- A disposable target database. Any of:
  - a throwaway Supabase project (free tier, deleted after the drill),
  - a local Postgres instance (Docker-less machines: any installed local Postgres; CI-style containerized Postgres also works),
  - a staging database — **only if** the drill's goal is precisely "verify this dump before a production restore decision".

## Step 1 — Restore the dump

```bash
# Target must exist as an empty database; create it first if needed:
#   psql "postgresql://user:pass@host:5432/postgres" -c "CREATE DATABASE restore_drill;"
pg_restore --no-owner --no-privileges \
  --dbname "postgresql://user:pass@host:5432/restore_drill" \
  ops/backups/local/<db>-<YYYYMMDD-HHmmss>.dump
```

Notes:
- `--no-owner --no-privileges` (matching the dump flags) keeps the restore role-agnostic.
- If the target is **not empty**, add `--clean --if-exists` to drop-and-recreate objects. Only do that on disposable targets.
- Restore time scales with data size; the dump's custom format is compressed, so expect the restored DB larger than the `.dump` file.

## Step 2 — Verify the restored data

Run against the restore target (psql):

```sql
-- 1. Schema landed completely (expect the 42 application tables):
SELECT count(*) FROM information_schema.tables
 WHERE table_schema = 'public';                      -- expect >= 42

-- 2. Core tables carry plausible row counts:
SELECT 'users' t, count(*) FROM users
UNION ALL SELECT 'enrollments', count(*) FROM enrollments
UNION ALL SELECT 'quiz_submission', count(*) FROM quiz_submission
UNION ALL SELECT 'certificates', count(*) FROM certificates;

-- 3. Constraint integrity sample (any result row = problem):
SELECT conrelid::regclass AS tbl, conname
  FROM pg_constraint WHERE NOT convalidated;
```

## Step 3 — Boot check (application accepts the data)

Point a local (or disposable) application boot at the restore target and let Hibernate validate:

```powershell
$env:DATABASE_URL = "jdbc:postgresql://<restore-host>:5432/restore_drill?sslmode=require"
$env:DATABASE_NAME = "<user>"
$env:DATABASE_PASSWORD = "<pass>"
$env:SPRING_PROFILES_ACTIVE = "prod"
$env:JWT_SECRET = "<any valid 32+ byte secret>"
./mvnw -B spring-boot:run
```

Boot succeeds = `ddl-auto=validate` passed = the restored schema matches the entity model. Watch the log for Flyway validation and the fail-fast provider accepting the secret, then stop the app.

## Step 4 — Verdict

| Drill result | Meaning |
|---|---|
| Restore + data checks + boot all pass | dump verified — record date + dump name in the drill log |
| Restore fails midway | dump corrupted or server-version mismatch — take a fresh dump and re-verify before the next drill cycle |
| Data checks fail (missing tables/rows) | stop — do not trust this dump lineage; investigate the backup script run that produced it |

## Production restore (incident path)

Only after a disposable-target verification passes:

1. **Freeze writes** (announce the downtime window; PO sign-off per [`backup-policy.md`](backup-policy.md) decision table).
2. Choose the restore shape:
   - **Full restore:** Supabase → reset the database (disks → reset) to empty, run Step 1 against the production connection string, re-verify (Step 2), redeploy/restart the container (Railway: Deploy → restart).
   - **Single table:** restore into the drill target, extract (`COPY (SELECT * FROM <table>) TO ...` or pg_restore's `--table` filter into the live DB), then restart the app.
3. **Post-restore:** run the production smoke (`/actuator/health` UP, login works, a real read path), unfreeze writes, and record the incident + timeline in the ticket thread.
4. If the cause was a bad migration: the fix is a **forward-only** `V{N+1}` script (never a schema rollback).
