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
GOOD_DIGEST="sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
assert_passes  "digest-pinned ref passes"                                             gate_guard_image_ref "ghcr.io/owner/edunest-backend@$GOOD_DIGEST"
assert_blocks "short digest blocks"                  "64 lowercase hex"               gate_guard_image_ref "ghcr.io/owner/edunest-backend@sha256:abc123"
assert_blocks "uppercase digest blocks"              "64 lowercase hex"               gate_guard_image_ref "ghcr.io/owner/edunest-backend@sha256:ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789"

# G6+provenance — image must belong to the approved GHCR repository
EXPECTED_PREFIX="ghcr.io/moora86/edunest-backend@sha256:"
GOOD_DIGEST="sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
assert_passes  "image ref with approved prefix passes"                                gate_guard_image_ref "ghcr.io/moora86/edunest-backend@$GOOD_DIGEST" "$EXPECTED_PREFIX"
assert_blocks "foreign repository image blocks"      "approved GHCR repository"       gate_guard_image_ref "ghcr.io/attacker/edunest-backend@$GOOD_DIGEST" "$EXPECTED_PREFIX"
assert_blocks "uppercase owner image blocks"         "approved GHCR repository"       gate_guard_image_ref "ghcr.io/MooRaa86/edunest-backend@$GOOD_DIGEST" "$EXPECTED_PREFIX"

# G3 — full SHA format (manual dispatch provenance)
assert_passes  "40-char lowercase sha passes"                                         gate_sha40 "$SHA"
assert_blocks "short sha blocks"                     "40-character lowercase hex"     gate_sha40 "a660839"
assert_blocks "uppercase sha blocks"                 "40-character lowercase hex"     gate_sha40 "A6608397D1F67610CD0E0B47AF7386CA2C9ED9D7"
assert_blocks "non-hex sha blocks"                   "40-character lowercase hex"     gate_sha40 "zzzz8397d1f67610cd0e0b47af7386ca2c9ed9d7"
assert_blocks "empty sha blocks"                     "40-character lowercase hex"     gate_sha40 ""

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

# G7m — explicit manual authorization gate (manual dispatch disabled by default)
assert_passes  "manual activation true passes"                                         gate_manual_activation "true"
assert_blocks "manual activation false blocks"        "manual staging deployment is NOT authorized" gate_manual_activation "false"
assert_blocks "manual activation absent blocks"       "manual staging deployment is NOT authorized" gate_manual_activation ""
assert_blocks "manual activation wrong casing blocks" "manual staging deployment is NOT authorized" gate_manual_activation "TRUE"
assert_blocks "manual activation garbage blocks"      "manual staging deployment is NOT authorized" gate_manual_activation "1"

# G8 — staging database identity allowlist (never prints credentials).
# Identity = FLYWAY_USER (actual supplied username) + URL host form.
STAGE_REF="stgref123example456"
PROD_REF="ciogysvcykzxrjuskxmk"
POOL_STG="jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:6543/postgres?user=postgres.${STAGE_REF}&password=secret"
POOL_STG_PLAIN="jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:6543/postgres"
DIRECT_STG="jdbc:postgresql://db.${STAGE_REF}.supabase.co:5432/postgres"
assert_passes  "staging pooler identity passes"                                        gate_staging_db_identity "$POOL_STG" "postgres.${STAGE_REF}" "$STAGE_REF" "$PROD_REF"
assert_passes  "staging pooler without inline user passes"                             gate_staging_db_identity "$POOL_STG_PLAIN" "postgres.${STAGE_REF}" "$STAGE_REF" "$PROD_REF"
assert_passes  "staging direct host identity passes"                                   gate_staging_db_identity "$DIRECT_STG" "postgres" "$STAGE_REF" "$PROD_REF"
assert_blocks "prod pooler identity blocks"           "PRODUCTION Supabase project identity" gate_staging_db_identity "$POOL_STG" "postgres.${PROD_REF}" "$STAGE_REF" "$PROD_REF"
assert_blocks "prod direct host identity blocks"      "PRODUCTION Supabase project identity"  gate_staging_db_identity "jdbc:postgresql://db.${PROD_REF}.supabase.co:5432/postgres" "postgres" "$STAGE_REF" "$PROD_REF"
assert_blocks "foreign db user blocks"                "does not match the approved staging database identity" gate_staging_db_identity "$POOL_STG" "postgres.someotherref999" "$STAGE_REF" "$PROD_REF"
assert_blocks "foreign host blocks"                   "does not match the approved staging database identity" gate_staging_db_identity "jdbc:postgresql://db.evil.example.com:5432/postgres" "postgres.${STAGE_REF}" "$STAGE_REF" "$PROD_REF"
assert_blocks "approved ref only in URL param does not prove identity" "does not match the approved staging database identity" gate_staging_db_identity "jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:6543/postgres?user=postgres.${STAGE_REF}" "postgres" "$STAGE_REF" "$PROD_REF"
assert_blocks "missing approved ref fails closed"     "not configured"                 gate_staging_db_identity "$POOL_STG" "postgres.${STAGE_REF}" "" "$PROD_REF"
assert_blocks "empty supplied username blocks"        "database username is empty"     gate_staging_db_identity "$POOL_STG" "" "$STAGE_REF" "$PROD_REF"
assert_blocks "empty url identity blocks"             "migration URL is empty"         gate_staging_db_identity "" "postgres.${STAGE_REF}" "$STAGE_REF" "$PROD_REF"
assert_blocks "non-jdbc identity blocks"              "PostgreSQL JDBC URL"            gate_staging_db_identity "postgres://db.${STAGE_REF}.supabase.co:5432/postgres" "postgres" "$STAGE_REF" "$PROD_REF"
out=$(gate_staging_db_identity "$POOL_STG" "postgres.${STAGE_REF}" "$STAGE_REF" "$PROD_REF" 2>&1)
printf '%s' "$out" | grep -qF "secret" && { fail=$((fail+1)); echo "FAIL  identity check leaked password"; } || { pass=$((pass+1)); echo "PASS  identity check never prints credentials"; }
out=$(gate_staging_db_identity "$POOL_STG" "postgres.${STAGE_REF}" "$STAGE_REF" "$PROD_REF" 2>&1)
printf '%s' "$out" | grep -qF "stgref123example456" && { fail=$((fail+1)); echo "FAIL  identity check leaked the staging ref"; } || { pass=$((pass+1)); echo "PASS  identity check never prints the staging ref"; }

# stale-run protection: an older successful run must not deploy over newer main
assert_blocks "stale triggering run blocks deploy"    "identity mismatch"              gate_verify_trigger success completed main 1111111111111111111111111111111111111111 "$SHA"

# G5 coarse check — production ref hidden inside a password is still blocked
assert_blocks "prod ref anywhere in URL blocks"       "PRODUCTION Supabase project ref" gate_guard_migration_url "jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:6543/postgres?user=postgres.${STAGE_REF}&password=xx${PROD_REF}yy"

# safe manual dispatch shape: explicit success+completed+main with matching SHA
assert_passes  "manual dispatch safe path passes"                                      gate_verify_trigger success completed main "$SHA" "$SHA"

# ==============================================================================
# EB-115: Production Database Target Guard & Approval Tests
# ==============================================================================
PROD_DIRECT="jdbc:postgresql://db.${PROD_REF}.supabase.co:5432/postgres?sslmode=verify-full"
PROD_DIRECT_NO_PORT="jdbc:postgresql://db.${PROD_REF}.supabase.co/postgres?sslmode=verify-full"
PROD_POOLER="jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:6543/postgres?sslmode=verify-full"
PROD_POOLER_EXTRA="jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:6543/postgres?sslmode=verify-full&connectTimeout=10"

# Positive target identity tests
assert_passes  "prod direct host with sslmode=verify-full passes"                      gate_production_db_identity "$PROD_DIRECT" "postgres" "$PROD_REF" "$STAGE_REF"
assert_passes  "prod direct host without port passes"                                  gate_production_db_identity "$PROD_DIRECT_NO_PORT" "postgres" "$PROD_REF" "$STAGE_REF"
assert_passes  "prod pooler host with coupled user passes"                             gate_production_db_identity "$PROD_POOLER" "postgres.${PROD_REF}" "$PROD_REF" "$STAGE_REF"
assert_passes  "prod pooler host with extra safe query params passes"                  gate_production_db_identity "$PROD_POOLER_EXTRA" "postgres.${PROD_REF}" "$PROD_REF" "$STAGE_REF"

# Negative: sslmode verification
assert_blocks "sslmode=require is rejected for production"   "exact sslmode=verify-full" gate_production_db_identity "jdbc:postgresql://db.${PROD_REF}.supabase.co:5432/postgres?sslmode=require" "postgres" "$PROD_REF" "$STAGE_REF"
assert_blocks "sslmode=prefer is rejected for production"    "exact sslmode=verify-full" gate_production_db_identity "jdbc:postgresql://db.${PROD_REF}.supabase.co:5432/postgres?sslmode=prefer" "postgres" "$PROD_REF" "$STAGE_REF"
assert_blocks "missing sslmode parameter blocks"             "missing query parameters"  gate_production_db_identity "jdbc:postgresql://db.${PROD_REF}.supabase.co:5432/postgres" "postgres" "$PROD_REF" "$STAGE_REF"
assert_blocks "duplicate sslmode parameters block"           "duplicate sslmode"         gate_production_db_identity "jdbc:postgresql://db.${PROD_REF}.supabase.co:5432/postgres?sslmode=verify-full&sslmode=verify-full" "postgres" "$PROD_REF" "$STAGE_REF"
assert_blocks "conflicting sslmode parameters block"         "duplicate sslmode"         gate_production_db_identity "jdbc:postgresql://db.${PROD_REF}.supabase.co:5432/postgres?sslmode=verify-full&sslmode=require" "postgres" "$PROD_REF" "$STAGE_REF"
assert_blocks "ssl=false parameter blocks"                   "ssl=false is prohibited"   gate_production_db_identity "jdbc:postgresql://db.${PROD_REF}.supabase.co:5432/postgres?sslmode=verify-full&ssl=false" "postgres" "$PROD_REF" "$STAGE_REF"

# Negative: embedded credentials & authority injection
assert_blocks "embedded userinfo in authority blocks"        "embedded userinfo"         gate_production_db_identity "jdbc:postgresql://user:pass@db.${PROD_REF}.supabase.co:5432/postgres?sslmode=verify-full" "postgres" "$PROD_REF" "$STAGE_REF"
assert_blocks "embedded password query param blocks"         "credentials in JDBC query" gate_production_db_identity "jdbc:postgresql://db.${PROD_REF}.supabase.co:5432/postgres?sslmode=verify-full&password=secret" "postgres" "$PROD_REF" "$STAGE_REF"
assert_blocks "embedded user query param blocks"             "credentials in JDBC query" gate_production_db_identity "jdbc:postgresql://db.${PROD_REF}.supabase.co:5432/postgres?sslmode=verify-full&user=postgres" "postgres" "$PROD_REF" "$STAGE_REF"

# Negative: ambiguous multi-host URLs
assert_blocks "ambiguous multi-host URL blocks"              "ambiguous multi-host URL"  gate_production_db_identity "jdbc:postgresql://db.${PROD_REF}.supabase.co:5432,backup.supabase.co:5432/postgres?sslmode=verify-full" "postgres" "$PROD_REF" "$STAGE_REF"

# Negative: staging & unknown project identity
assert_blocks "staging project ref in host blocks"           "staging project identity"  gate_production_db_identity "jdbc:postgresql://db.${STAGE_REF}.supabase.co:5432/postgres?sslmode=verify-full" "postgres" "$PROD_REF" "$STAGE_REF"
assert_blocks "staging project ref in db_user blocks"        "staging project identity"  gate_production_db_identity "$PROD_POOLER" "postgres.${STAGE_REF}" "$PROD_REF" "$STAGE_REF"
assert_blocks "unknown project ref in pooler user blocks"    "does not match approved"   gate_production_db_identity "$PROD_POOLER" "postgres.unknown999ref" "$PROD_REF" "$STAGE_REF"

# Negative: user and host coupling
assert_blocks "direct host with pooler user blocks"          "does not match approved"   gate_production_db_identity "$PROD_DIRECT" "postgres.${PROD_REF}" "$PROD_REF" "$STAGE_REF"
assert_blocks "pooler host with plain postgres user blocks"  "does not match approved"   gate_production_db_identity "$PROD_POOLER" "postgres" "$PROD_REF" "$STAGE_REF"
assert_blocks "unexpected foreign host blocks"               "does not match approved"   gate_production_db_identity "jdbc:postgresql://evil.supabase.co:5432/postgres?sslmode=verify-full" "postgres" "$PROD_REF" "$STAGE_REF"

# Negative: inputs validation
assert_blocks "empty prod URL blocks"                        "migration URL is empty"    gate_production_db_identity "" "postgres" "$PROD_REF" "$STAGE_REF"
assert_blocks "empty prod db_user blocks"                    "username is empty"         gate_production_db_identity "$PROD_DIRECT" "" "$PROD_REF" "$STAGE_REF"
assert_blocks "empty approved prod ref blocks"               "not configured"            gate_production_db_identity "$PROD_DIRECT" "postgres" "" "$STAGE_REF"
assert_blocks "non-postgresql JDBC url blocks"               "PostgreSQL JDBC URL"       gate_production_db_identity "jdbc:mysql://db.${PROD_REF}.supabase.co:3306/postgres?sslmode=verify-full" "postgres" "$PROD_REF" "$STAGE_REF"

# Credentials leakage test
out=$(gate_production_db_identity "jdbc:postgresql://db.${PROD_REF}.supabase.co:5432/postgres?sslmode=verify-full&password=supersecretpassword" "postgres" "$PROD_REF" "$STAGE_REF" 2>&1)
printf '%s' "$out" | grep -qF "supersecretpassword" && { fail=$((fail+1)); echo "FAIL  production identity check leaked password"; } || { pass=$((pass+1)); echo "PASS  production identity check never prints credentials"; }

# Pre-migration backup confirmation tests
assert_passes  "matching backup confirmation SHA passes"                               gate_backup_readiness "$SHA" "$SHA"
assert_blocks "empty backup confirmation SHA blocks"         "not set"                   gate_backup_readiness "" "$SHA"
assert_blocks "mismatched backup confirmation SHA blocks"    "mismatch"                  gate_backup_readiness "0000000000000000000000000000000000000000" "$SHA"

# Schema backward compatibility confirmation tests
assert_passes  "matching compatibility approval SHA passes"                            gate_schema_compatibility "$SHA" "$SHA"
assert_blocks "empty compatibility approval SHA blocks"      "not set"                   gate_schema_compatibility "" "$SHA"
assert_blocks "mismatched compatibility approval SHA blocks" "mismatch"                  gate_schema_compatibility "0000000000000000000000000000000000000000" "$SHA"

# Production activation label check
assert_passes  "prod activation true passes"                                           gate_activation "true" "production" "PROD_AUTO_DEPLOY_ENABLED"
assert_blocks "prod activation false blocks"                 "PROD_AUTO_DEPLOY_ENABLED"  gate_activation "false" "production" "PROD_AUTO_DEPLOY_ENABLED"

# G2m — manual dispatch ancestry compare tests
assert_passes  "ancestry status identical passes"                                      gate_ancestry_status "identical" "production"
assert_passes  "ancestry status behind passes"                                         gate_ancestry_status "behind" "production"
assert_blocks "ancestry status ahead blocks"                 "not part of"               gate_ancestry_status "ahead" "production"
assert_blocks "ancestry status diverged blocks"              "not part of"               gate_ancestry_status "diverged" "production"
assert_blocks "empty ancestry status blocks"                 "not part of"               gate_ancestry_status "" "production"

echo "-----"
echo "deploy-gate tests: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
