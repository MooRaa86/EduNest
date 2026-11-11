#!/usr/bin/env bash
# Offline database backup via pg_dump (P6-02 / EB-73 - Tier 2 of the backup policy).
# Produces a custom-format dump in ops/backups/local/ (gitignored) and prunes old copies.
# See: ops/backups/backup-policy.md and ops/backups/restore-procedure.md.
#
# Usage:
#   ./ops/backups/invoke-backup.sh "postgresql://user:pass@host:5432/postgres"
#   (or set DATABASE_URL in the environment and omit the argument)
#
# Requires: pg_dump (PostgreSQL client tools) on PATH.

set -euo pipefail

DATABASE_URL="${1:-${DATABASE_URL:-}}"
if [ -z "$DATABASE_URL" ]; then
  echo "ERROR: no DATABASE_URL provided (argument or environment)."
  echo "Never hardcode credentials in this script or its callers."
  exit 1
fi

# pg_dump speaks the libpq URL form: tolerate the jdbc:postgresql:// app form by stripping jdbc:
case "$DATABASE_URL" in
  jdbc:postgresql://*) DATABASE_URL="postgresql://${DATABASE_URL#jdbc:postgresql://}" ;;
esac

if ! command -v pg_dump >/dev/null 2>&1; then
  echo "ERROR: pg_dump not found on PATH. Install PostgreSQL client tools (psql/pg_dump)."
  exit 1
fi

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT_DIR="$REPO_ROOT/ops/backups/local"
mkdir -p "$OUT_DIR"

DB_NAME="database"
case "$DATABASE_URL" in
  */?*/?*) : ;;
esac
DB_NAME="$(echo "$DATABASE_URL" | sed -E 's#.*/([^/?]+)(\?.*)?$#\1#')"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_FILE="$OUT_DIR/${DB_NAME}-${STAMP}.dump"

echo "Backing up '$DB_NAME' -> $OUT_FILE"
pg_dump "$DATABASE_URL" --format=custom --no-owner --no-privileges --file="$OUT_FILE"

SIZE=$(stat -c%s "$OUT_FILE" 2>/dev/null || stat -f%z "$OUT_FILE")
if [ "$SIZE" -lt 10240 ]; then
  echo "ERROR: dump is suspiciously small ($SIZE bytes) - investigate before trusting this backup."
  exit 1
fi
echo "Backup written: $OUT_FILE ($SIZE bytes)"

# Retention: keep the 14 most recent dumps for this database.
KEEP=14
cd "$OUT_DIR"
LIST=($(ls -t "${DB_NAME}"-*.dump 2>/dev/null || true))
COUNT=${#LIST[@]}
if [ "$COUNT" -gt "$KEEP" ]; then
  for OLD in "${LIST[@]:$KEEP}"; do
    echo "Pruning old dump: $OLD"
    rm -f "$OLD"
  done
fi
echo "Retention check done: $COUNT dump(s) on disk for '$DB_NAME' (keep $KEEP)."
echo "Reminder: verify restores weekly (ops/backups/restore-procedure.md); a backup never restored is a hypothesis."
