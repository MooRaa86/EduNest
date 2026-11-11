#!/usr/bin/env bash
# EB-75 [P6-04] — local test suite for scripts/cicd/deploy-gate.sh.
# Pure-bash, no network, no deployments: proves the workflow gate logic blocks
# failed tests, foreign branches, commit/image mismatches, missing config,
# production-targeted migrations, mutable tags — and passes the happy path.
set -u

GATE_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=deploy-gate.sh
source "$GATE_DIR/deploy-gate.sh"

pass=0; fail=0
assert_blocks() { # name expected_snippet function args...
    local name="$1" snippet="$2"; shift 2
    local out rc
    out=$("$@" 2>&1); rc=$?
    if [ "$rc" -ne 0 ] && printf '%s' "$out" | grep -qF "$snippet"; then
        pass=$((pass+1)); echo "PASS  $name"
    else
        fail=$((fail+1)); echo "FAIL  $name (rc=$rc, out='$out')"
    fi
}
assert_passes() { # name function args...
    local name="$1"; shift
    local out rc
    out=$("$@" 2>&1); rc=$?
    if [ "$rc" -eq 0 ]; then
        pass=$((pass+1)); echo "PASS  $name"
    else
        fail=$((fail+1)); echo "FAIL  $name (rc=$rc, out='$out')"
    fi
}

SHA="a6608397d1f67610cd0e0b47af7386ca2c9ed9d7"

# G1 — failed/incomplete triggering runs block
assert_blocks "failed test run blocks deploy"        "never deploy from a failed run" gate_verify_trigger failure completed main "$SHA" "$SHA"
assert_blocks "incomplete test run blocks deploy"    "not completed"                  gate_verify_trigger success in_progress main "$SHA" "$SHA"
assert_blocks "cancelled test run blocks deploy"     "never deploy from a failed run" gate_verify_trigger cancelled completed main "$SHA" "$SHA"

# G2 — only main deploys
assert_blocks "non-main branch blocks deploy"        "only approved refs may deploy"  gate_verify_trigger success completed develop "$SHA" "$SHA"

# G3 — commit/image identity
assert_blocks "trigger sha mismatch blocks"          "identity mismatch"              gate_verify_trigger success completed main 0000000000000000000000000000000000000000 "$SHA"
assert_blocks "empty trigger sha blocks"             "identity mismatch"              gate_verify_trigger success completed main "" "$SHA"
assert_blocks "image tag mismatch blocks"            "does not match the verified commit" gate_verify_image_sha_tag "deadbeef" "$SHA"
assert_passes  "image tag match passes"                                               gate_verify_image_sha_tag "$SHA" "$SHA"
assert_passes  "verified trigger passes"                                              gate_verify_trigger success completed main "$SHA" "$SHA"

# G4 — missing configuration blocks
assert_blocks "missing Railway token blocks"         "'RAILWAY_TOKEN'"                gate_require_config RAILWAY_TOKEN "" RAILWAY_PROJECT_ID proj-1
assert_blocks "missing staging URL blocks"           "'STAGING_BASE_URL'"             gate_require_config STAGING_BASE_URL ""
assert_passes  "full config passes"                                                   gate_require_config RAILWAY_TOKEN tok RAILWAY_PROJECT_ID p STAGING_BASE_URL u STAGING_FLYWAY_URL j STAGING_FLYWAY_USER u2 STAGING_FLYWAY_PASSWORD pw2

# G5 — production targeting is refused
assert_blocks "production Supabase URL blocks"       "PRODUCTION Supabase project ref" gate_guard_migration_url "jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:6543/postgres?options=project%3Dciogysvcykzxrjuskxmk"
assert_blocks "non-JDBC URL blocks"                  "PostgreSQL JDBC URL"            gate_guard_migration_url "postgres://host/db"
assert_blocks "empty migration URL blocks"           "migration URL is empty"         gate_guard_migration_url ""
assert_passes  "staging JDBC URL passes"                                              gate_guard_migration_url "jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:6543/postgres"

# G6 — digest pinning only
assert_blocks "latest tag blocks"                    "pin by digest"                  gate_guard_image_ref "ghcr.io/owner/edunest-backend:latest"
assert_blocks "mutable SHA tag blocks"               "digest-pinned"                  gate_guard_image_ref "ghcr.io/owner/edunest-backend:$SHA"
assert_blocks "empty image ref blocks"               "image ref is empty"             gate_guard_image_ref ""
assert_passes  "digest-pinned ref passes"                                             gate_guard_image_ref "ghcr.io/owner/edunest-backend@sha256:abc123"

# tag derivation
out=$(gate_short_tag "$SHA" ""); [ "$out" = "${SHA:0:7}" ] && { pass=$((pass+1)); echo "PASS  short tag falls back to 7-char SHA"; } || { fail=$((fail+1)); echo "FAIL  short tag fallback (got '$out')"; }
out=$(gate_short_tag "$SHA" "v1.2.3-4-ga660839"); [ "$out" = "v1.2.3-4-ga660839" ] && { pass=$((pass+1)); echo "PASS  describe tag preferred when present"; } || { fail=$((fail+1)); echo "FAIL  describe preference (got '$out')"; }

# G7 — explicit activation gate (auto-deploy disabled by default)
STAGE_REF="stgref123example456"
assert_passes  "activation true passes"                                               gate_activation "true"
assert_blocks "activation false blocks"               "NOT activated"                  gate_activation "false"
assert_blocks "activation absent blocks"              "NOT activated"                  gate_activation ""
assert_blocks "activation wrong casing blocks"        "NOT activated"                  gate_activation "True"
assert_blocks "activation garbage blocks"             "NOT activated"                  gate_activation "yes"

# G8 — staging database identity allowlist (never prints credentials)
POOL_STG="jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:6543/postgres?user=postgres.${STAGE_REF}&password=secret"
DIRECT_STG="jdbc:postgresql://db.${STAGE_REF}.supabase.co:5432/postgres"
PROD_REF="ciogysvcykzxrjuskxmk"
POOL_PROD="jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:6543/postgres?user=postgres.${PROD_REF}&password=secret"
DIRECT_PROD="jdbc:postgresql://db.${PROD_REF}.supabase.co:5432/postgres"
assert_passes  "staging pooler identity passes"                                        gate_staging_db_identity "$POOL_STG" "$STAGE_REF" "$PROD_REF"
assert_passes  "staging direct host identity passes"                                   gate_staging_db_identity "$DIRECT_STG" "$STAGE_REF" "$PROD_REF"
assert_blocks "production pooler identity blocks"     "PRODUCTION Supabase project identity" gate_staging_db_identity "$POOL_PROD" "$STAGE_REF" "$PROD_REF"
assert_blocks "production direct host identity blocks" "PRODUCTION Supabase project identity"  gate_staging_db_identity "$DIRECT_PROD" "$STAGE_REF" "$PROD_REF"
assert_blocks "unapproved foreign db blocks"          "does not match the approved staging database identity" gate_staging_db_identity "jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:6543/postgres?user=postgres.someotherref999&password=secret" "$STAGE_REF" "$PROD_REF"
assert_blocks "missing approved ref fails closed"     "not configured"                 gate_staging_db_identity "$POOL_STG" "" "$PROD_REF"
assert_blocks "empty url identity blocks"             "migration URL is empty"         gate_staging_db_identity "" "$STAGE_REF" "$PROD_REF"
assert_blocks "non-jdbc identity blocks"              "PostgreSQL JDBC URL"            gate_staging_db_identity "postgres://db.${STAGE_REF}.supabase.co:5432/postgres" "$STAGE_REF" "$PROD_REF"
out=$(gate_staging_db_identity "$POOL_STG" "$STAGE_REF" "$PROD_REF" 2>&1)
printf '%s' "$out" | grep -qF "secret" && { fail=$((fail+1)); echo "FAIL  identity check leaked password"; } || { pass=$((pass+1)); echo "PASS  identity check never prints credentials"; }
out=$(gate_staging_db_identity "jdbc:postgresql://host/db?user=postgres.${STAGE_REF}&password=with${PROD_REF}inside" "$STAGE_REF" "$PROD_REF" 2>&1)
printf '%s' "$out" | grep -qF "with${PROD_REF}inside" && { fail=$((fail+1)); echo "FAIL  identity check leaked URL"; } || { pass=$((pass+1)); echo "PASS  identity check never prints the URL"; }

# stale-run protection: an older successful run must not deploy over newer main
assert_blocks "stale triggering run blocks deploy"    "identity mismatch"              gate_verify_trigger success completed main 1111111111111111111111111111111111111111 "$SHA"

# G5 coarse check — production ref hidden inside a password is still blocked
assert_blocks "prod ref anywhere in URL blocks"       "PRODUCTION Supabase project ref" gate_guard_migration_url "jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:6543/postgres?user=postgres.${STAGE_REF}&password=xx${PROD_REF}yy"

# safe manual dispatch shape: explicit success+completed+main with matching SHA
assert_passes  "manual dispatch safe path passes"                                      gate_verify_trigger success completed main "$SHA" "$SHA"

echo "-----"
echo "deploy-gate tests: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
