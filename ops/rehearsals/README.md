# EB-49 Migration Rehearsal — Operator Guide

## Purpose

Synthetic, disposable, container-owned migration rehearsal for the EB-44 / V3 uniqueness constraints and dedupe migration. Runs entirely in PostgreSQL 17 Testcontainers with zero external dependencies.

## What It Verifies

1. **Pre-migration state**: Flyway V1+V2 history established; synthetic fixture loaded.
2. **Backup**: Custom-format `pg_dump` of the pre-migration database.
3. **Restore (pre-migration copy)**: Restore into a fresh database; schema fingerprint matches source.
4. **Migration**: Flyway applies V3; all 7 expected indexes/constraints created; archive tables populated; relationships verified.
5. **Recovery**: Fresh restore from backup into another new database; schema fingerprint matches pre-migration source; all row hashes match.

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 21+ | Tested with 23 |
| Maven | Wrapper included | `./mvnw` |
| Docker | 24+ | Desktop or Engine; Linux containers |
| PostgreSQL client tools | 17 | `pg_dump`, `pg_restore`, `createdb`, `psql` in PATH |

> **Windows**: PostgreSQL client tools must be on PATH (e.g. `C:\Program Files\PostgreSQL\17\bin`). Docker Desktop must run Linux containers.

## Running the Rehearsal

### Option 1: PowerShell Wrapper (recommended)

```powershell
# From repository root
.\ops\rehearsals\Invoke-MigrationRehearsal.ps1 -Disposable
```

The script enforces `-Disposable` to prevent accidental use against shared environments.

### Option 2: Maven Direct

```bash
./mvnw -B -Dtest=MigrationRehearsalIT -Dsurefire.failIfNoSpecifiedTests=true -DskipTests=false test
```

## Evidence

Each run writes a sanitized report to `target/rehearsal-evidence/EB-49-<runId>.txt` containing:

- `task=EB-49`
- `run_id=<uuid>`
- `run_number=1|2`
- `started_utc=<ISO timestamp>`
- `git_commit=<40-char sha>`
- `fixture_sha256=<fixture content hash>`
- `postgres_version=postgres_(PostgreSQL)_17.x`
- Per-step timing and result (PASS/FAIL)
- Migration history entries (version, type, checksum, success)
- `recovery_schema_and_all_public_table_row_hashes=MATCH`
- `cleanup=PASS`
- `result=PASS|FAIL`
- `limitations=...`

**No connection strings, passwords, or production data are ever written.**

## Limitations (by design)

- **Synthetic fixture only** — 3 users, 1 mentorship, 2 enrollments, 2 reviews, 3 conversations, 2 messages.
- **Selected schema metadata** — columns, constraints (normalized), indexes, extensions, sequences, table row hashes.
- **Constraint definition normalization** — `::character varying[]` and `::text[]` casts stripped; whitespace/parentheses removed. Covered by migration integration suite.
- **No external object storage** — Supabase S3, MinIO, etc. not exercised.
- **No post-backup writes** — backup is a point-in-time snapshot; no concurrent writes during rehearsal.
- **No Flyway undo** — recovery is a physical restore, not `flyway undo`.

## Failure Diagnostics

| Stage | Typical Cause | Action |
|-------|---------------|--------|
| `container_start` | Docker unavailable / port conflict | Ensure Docker Desktop running; no other process on 5432 |
| `pre_migration_fixture` | Flyway history mismatch | Check migration files present under `src/main/resources/db/migration` |
| `backup` | `pg_dump` not in PATH | Install PostgreSQL 17 client tools |
| `restore_pre_migration_copy` | `pg_restore` list filtering failed | Inspect `/tmp/eb49-*.list` inside container |
| `migration_and_validation` | V3 validation failed | Run `PostgreSqlMigrationIntegrationTest` for detailed output |
| `recovery` | Fingerprint mismatch | Compare `target/rehearsal-evidence/*.txt` sections |
| `cleanup` | Container not stopped | `docker ps -a --filter label=org.edunest.eb49.run` |

## Safe Cleanup Behavior

- The test owns its container via label `org.edunest.eb49.run=<runId>`.
- `finally` block stops the container and verifies `!db.isRunning()`.
- Ryuk (Testcontainers reaper) guarantees cleanup on JVM exit.
- No persistent volumes or named containers are created.

## Negative / Failure-Injection Tests

Run separately:

```bash
./mvnw -B -Dtest=MigrationRehearsalNegativeIT test
```

Covers:
- Unsafe database targets (e.g. `postgres`)
- Ownership label mismatch
- Missing credentials
- Missing tools (`pg_dump`, `pg_restore`)
- Corrupted/missing backup archives
- Failed restores
- Fingerprint comparison failures
- Cleanup verification failures
- Invalid Flyway targets
- Schema corruption after migration (column drop → V3 fails)

## Extending the Rehearsal

Do not modify `fixture-pre-v3.sql` to include production-like data. The synthetic fixture is intentionally minimal and deterministic.

To test a future migration (V4+):
1. Add the migration file to `src/main/resources/db/migration/`.
2. Update `MigrationRehearsalIT.migrate()` target version and `expected` count.
3. Extend `validateMigrated()` with new assertions.
4. Run the rehearsal; update `limitations` if new assumptions are added.

## Related Documentation

- `src/main/resources/db/migration/README.md` — Flyway conventions, version registry, baselining runbook.
- `docs/migration/EB-44-PRODUCTION-ADOPTION-RUNBOOK.md` — Production adoption steps for V3.
- `docs/migration/EB-44-SUPABASE-FLYWAY-BASELINE-PROCEDURE.md` — Supabase-specific baselining.

## Security

- All passwords are UUIDs generated per-run; never logged or persisted.
- Container binds to `127.0.0.1` only.
- Evidence reports are sanitized (no secrets, no connection strings).
- Never run against shared, staging, or production databases.

## Support

Owner: Hazem Saed
Task: EB-49 / P3-07
Branch: `feat/p3-07-migration-rehearsal`