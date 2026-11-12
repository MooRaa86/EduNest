#!/usr/bin/env bash
# Upload-tree filesystem hardening evidence collector (EB-89 - ops ticket).
# Read-only, NO Docker (D-27), no writes, no network.
# POSIX port of Invoke-UploadTreeEvidence.ps1 (same gates, same exit contract):
#   0 = all verifiable gates green (UNVERIFIED items listed when blocked),
#   1 = at least one gate FAILED.
#
# Usage:
#   ./ops/runbooks/invoke-upload-tree-evidence.sh [BASE_DIR]
#   BASE_DIR defaults to $APP_UPLOAD_BASE_DIR or ./uploads
set -u

BASE_DIR="${1:-${APP_UPLOAD_BASE_DIR:-uploads}}"
FAILS=()
UNVERIFIED=()

echo "=========================================================================="
echo " EB-89: Upload-tree hardening evidence (read-only, POSIX)"
echo "=========================================================================="

case "$BASE_DIR" in
  /*) ABSOLUTE=yes ;;
  *)  ABSOLUTE=no ;;
esac
if [ "$ABSOLUTE" = "yes" ]; then
  VERDICT="ABSOLUTE (pinned - OK)"
else
  VERDICT="RELATIVE (CWD-coupled - FAIL in prod; the Dockerfile pins ENV APP_UPLOAD_BASE_DIR=/app/uploads)"
  FAILS+=("base-dir is RELATIVE ('$BASE_DIR'); prod must pin an absolute path")
fi
echo "base-dir: $BASE_DIR -> $VERDICT"

# Resolve to an absolute path for the scan
ROOT="$BASE_DIR"
if [ "$ABSOLUTE" = "no" ]; then
  ROOT="$(pwd)/$BASE_DIR"
fi

if [ ! -d "$ROOT" ]; then
  echo "upload root does not exist yet (app creates it on first boot) - evidence limited:"
  UNVERIFIED+=("ACL/symlink evidence - upload root '$ROOT' does not exist yet")
  ROOT=""
fi

# ---- Gate 1: owner + permission read-out --------------------------------------
if [ -n "$ROOT" ]; then
  echo ""
  echo "--- Gate 1: owner / permission read-out ------------------------------"
  OWNER=$(stat -c '%U (uid %u)' "$ROOT" 2>/dev/null || stat -f '%Su (uid %u)' "$ROOT" 2>/dev/null)
  echo "owner: ${OWNER:-<stat unavailable>}"
  PERM=$(stat -c '%A' "$ROOT" 2>/dev/null || stat -f '%Sp' "$ROOT" 2>/dev/null)
  echo "permissions: ${PERM:-<unavailable>}"
  echo "WRITABLE CLASSES (from permission bits):"
  echo "  user-writable : $(echo "$PERM" | cut -c2-3 | grep -q 'w' && echo YES || echo no)"
  echo "  group-writable: $(echo "$PERM" | cut -c5-6 | grep -q 'w' && echo YES || echo no)"
  echo "  other-writable: $(echo "$PERM" | cut -c8-9 | grep -q 'w' && echo YES || echo no)"
  if echo "$PERM" | cut -c8-9 | grep -q 'w'; then
    FAILS+=("upload root is OTHER-writable ($PERM) - the tree must be writable ONLY by the app-service identity")
  fi
  if echo "$PERM" | cut -c5-6 | grep -q 'w'; then
    echo "  note: group-writable - verify the group contains ONLY the app-service identity"
    UNVERIFIED+=("group membership of the upload root must be checked manually (getent group <gid of '$ROOT'>)")
  fi
fi

# ---- Gate 3: symlink scan (8.3 is a Windows-only concept: N/A on POSIX) --------
if [ -n "$ROOT" ]; then
  echo ""
  echo "--- Gate 3: symlinks under the upload root (8.3 N/A on POSIX) --------"
  LINKS=$(find "$ROOT" -type l 2>/dev/null || true)
  if [ -z "$LINKS" ]; then
    echo "symlinks: NONE (OK)"
  else
    FAILS+=("symlinks exist under the upload root - remove them and restrict link creation: $LINKS")
  fi
fi

echo ""
echo "=== EVIDENCE BLOCK (paste into EB-89) ==="
echo "base_dir: $BASE_DIR"
echo "base_dir_absolute: $VERDICT"
[ -n "$ROOT" ] && echo "root_resolved: $ROOT"
[ -n "${OWNER:-}" ] && echo "owner: $OWNER"
[ -n "${PERM:-}" ] && echo "permissions: $PERM"
echo "host: $(hostname) | user: $(id -un 2>/dev/null || echo unknown) | collected: $(date '+%Y-%m-%d %H:%M %Z')"
if [ ${#UNVERIFIED[@]} -gt 0 ]; then
  echo "UNVERIFIED (action needed):"
  for u in "${UNVERIFIED[@]}"; do echo "  - $u"; done
fi
if [ ${#FAILS[@]} -gt 0 ]; then
  echo "UPLOAD-TREE EVIDENCE FAILED:"
  for f in "${FAILS[@]}"; do echo "  - $f"; done
  exit 1
fi
echo "UPLOAD-TREE EVIDENCE: all verifiable gates green."
exit 0
