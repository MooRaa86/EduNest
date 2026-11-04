# EB-49 Migration Rehearsal — Operator Guide

## Purpose

Synthetic, disposable, container-owned migration rehearsal for the EB-44 / V3 uniqueness constraints and dedupe migration using PostgreSQL 17 Testcontainers.

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
| Docker | Compatible running Docker daemon | Linux containers; image pulls may require network access |
| Git | Available on PATH | Used to record HEAD in evidence |

PostgreSQL client tools run inside the `postgres:17-alpine` container; host installations are not required. Maven dependencies and container images must be available locally or downloadable. The PowerShell wrapper requires PowerShell 5.1 or newer and a Docker CLI.

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

The rehearsal evidence omits connection strings and credentials and uses synthetic data. Other Maven or test reports may include environment details; review them before sharing.

## Limitations (by design)

- **Synthetic fixture only** — 3 users, 1 mentorship, 2 enrollments, 2 reviews, 3 conversations, 2 messages.
- **Selected schema metadata** — columns, constraints, indexes, extensions, sequences, table row hashes; not every PostgreSQL object or catalog attribute is compared.
- **CHECK comparison** — PostgreSQL reparses each CHECK on temporary columns copied from its source table; the resulting definitions are compared exactly, as are other constraint definitions. This handles dump/restore deparsing differences without stripping literals, casts or grouping. Reparse is not a general semantic-equivalence proof and may reject equivalent expressions; unsupported expressions fail the rehearsal rather than being ignored.
- **No external object storage** — Supabase S3, MinIO, etc. not exercised.
- **No post-backup writes** — backup is a point-in-time snapshot; no concurrent writes during rehearsal.
- **No Flyway undo** — recovery restores a logical dump, not `flyway undo`.
- **Recovery scope** — the archive is a schema-scoped logical dump, not a physical backup: roles, grants, database-level settings, other schemas, extension creation, and external storage are not captured. The rehearsal pre-installs `pg_trgm` before restoring. It does not establish production-scale recovery or point-in-time recovery.

## Failure Diagnostics

| Stage | Typical Cause | Action |
|-------|---------------|--------|
| `container_start` | Docker unavailable or container startup failure | Check the local Docker daemon and image availability |
| `pre_migration_fixture` | Flyway history mismatch | Check migration files present under `src/main/resources/db/migration` |
| `backup` | Container-side dump failed | Check Docker and the PostgreSQL container image |
| `restore_pre_migration_copy` | Restore or fingerprint comparison failed | Inspect the sanitized failure stage and reason; the container is normally removed during cleanup |
| `migration_and_validation` | V3 validation failed | Run `PostgreSqlMigrationIntegrationTest` for detailed output |
| `recovery` | Fingerprint mismatch | Compare `target/rehearsal-evidence/*.txt` sections |
| `cleanup` | Container not stopped | `docker ps -a --filter label=org.edunest.eb49.run` |

## Safe Cleanup Behavior

- The test owns its container via label `org.edunest.eb49.run=<runId>`.
- `finally` block stops the container and verifies `!db.isRunning()`.
- Testcontainers normally uses Ryuk as a cleanup fallback; abrupt termination or Docker failures can leave resources behind.
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
- A simulated missing command
- Corrupted/missing backup archives
- Failed restores
- Fingerprint comparison failures
- Cleanup verification failures
- Invalid Flyway targets
- Schema corruption before V3 (column drop → V3 fails)
- Distinct CHECK literals, grouping and casts remain distinct after PostgreSQL reparsing

The interruption test demonstrates Java finally semantics; it does not interrupt a real rehearsal or verify container cleanup after interruption.

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

- Disposable passwords are UUIDs generated per run and are provided to the container; Docker administrators may inspect its environment.
- Container binds to `127.0.0.1` only.
- Evidence reports are sanitized (no secrets, no connection strings).
- Never run against shared, staging, or production databases.

## Support

Owner: Hazem Saed
Task: EB-49 / P3-07
Branch: `feat/p3-07-migration-rehearsal`
