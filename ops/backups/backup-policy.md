# Backup Policy & Recovery (P6-02 / EB-73 — DEP-08 policy half)

> Two-tier policy (approved 2026-11-11, Omar): Tier 1 = Supabase native snapshots; Tier 2 = offline `pg_dump` copies. Rationale: Tier 1 lives **inside the same vendor as the primary database** — a vendor-side failure would take primary *and* only backup at once. Tier 2 removes that single point of failure.

## Scope

| Environment | Policy |
|---|---|
| **Production Supabase** | both tiers — this is the real data |
| Staging Supabase | Tier 1 only (disposable by design; rebuild via [`../staging/provision-staging.md`](../staging/provision-staging.md) instead of restoring) |
| Local dev | none (throwaway by design) |

## Tier 1 — Supabase native snapshots

- **Schedule:** daily, automatic (Supabase-managed).
- **Retention:** 7 days on the current free tier. **Action item when real user data accrues:** upgrade the plan tier to extend retention (target: 14–30 days) — cost decision, PO sign-off.
- **Includes:** database only. **Supabase Storage objects (uploaded files) are NOT in DB snapshots** — file-level durability rides on Supabase Storage replication; the Tier 2 dump script may be extended with a storage sync step when file volume justifies it (noted as deferred scope).
- **Access:** Supabase dashboard → Database → Backups; restore = point-in-time snapshot restore per Supabase's flow.

## Tier 2 — Offline pg_dump copies

- **Tool:** [`Invoke-Backup.ps1`](Invoke-Backup.ps1) / [`invoke-backup.sh`](invoke-backup.sh) — `pg_dump` (custom format `-Fc`) against `DATABASE_URL`.
- **Schedule:** daily, run manually or by any scheduler the operator prefers (local Task Manager / cron). Supabase's pooler connection string is used; `--no-owner --no-privileges` keeps the dump restore-agnostic.
- **Output:** `ops/backups/local/<db>-<YYYYMMDD-HHmmss>.dump` — **gitignored**; keep the directory outside any synced/secret-shared location if data is sensitive.
- **Retention:** keep the **14 most recent** dumps; the script prunes older ones automatically.
- **Integrity:** the script records file size and asserts a non-trivial dump size (> 10 KB); a suspiciously small dump means something failed — investigate before trusting the day's backup.
- **Transfer off-machine (recommended when data is real):** copy the newest dump to a second physical location (external drive / separate cloud account). The script never uploads anywhere by itself — it only produces the file.

## Verification (weekly drill)

1. Pick the newest dump in `ops/backups/local/`.
2. Restore into a **disposable target** per [`restore-procedure.md`](restore-procedure.md).
3. Confirm: row counts on the biggest tables (`users`, `enrollments`), boot check (`ddl-auto=validate` passes against the restored schema).
4. Log the drill result in the ticket/comment thread. A backup that has never been restored is a hypothesis, not a backup.

## Restore decision table

| Situation | Action |
|---|---|
| Accidental data mutation (bad migration, bad bulk edit) | Restore the newest Tier 2 dump into staging first, verify, then decide: targeted data fix vs. full restore into production (full restore = downtime window, PO sign-off) |
| Vendor incident / project loss | Recreate project (`provision-staging.md` §1 pattern, prod flavor) → restore newest Tier 2 dump per `restore-procedure.md` → redeploy container |
| Single table damaged | Restore Tier 2 dump into a disposable instance, extract the table, re-import only that table |
| Storage (files) lost | Out of Tier 1/2 scope today — see deferred storage-sync note above |

## Non-negotiables

1. Migrations are **forward-only** (`ops/environments.md` §7.2) — restore is for *data* recovery, not schema rollback.
2. Backups never enter git (`ops/backups/local/` is gitignored; `SecretsHygieneGuardTest` guards the pattern family).
3. Every restore drill starts from the *newest verified dump*, never from memory of what should be in it.
