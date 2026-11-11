#!/usr/bin/env bash
# Generates a cryptographically random JWT_SECRET (>= 32 bytes of entropy; outputs 48 bytes as base64).
# P6-03 / EB-74 - writes NOTHING to disk. Copy the printed value into the Railway dashboard.
# See: ops/secrets/rotation.md (section 1) and ops/secrets/JWT-GENERATION-RECORD.md.

set -euo pipefail

SECRET="$(openssl rand -base64 48)"

echo ""
echo "=== NEW JWT_SECRET (copy once - it will not be shown again or saved anywhere) ==="
echo ""
echo "$SECRET"
echo ""
echo "Next steps (ops/secrets/rotation.md section 1):"
echo "  1. Save the CURRENT value to ops/secrets/local/ (gitignored) as the rollback lane."
echo "  2. Railway -> EduNest service -> Variables -> set JWT_SECRET to the value above."
echo "  3. Wait for the automatic redeploy; verify /actuator/health UP and a working login."
echo "  4. Append a provenance row (no value!) to ops/secrets/JWT-GENERATION-RECORD.md."
echo "  5. Delete the old value from ops/secrets/local/ after the verification window."
echo ""
echo "NEVER commit this value, paste it into chat, or store it outside the Railway dashboard."
