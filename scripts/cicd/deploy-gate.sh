#!/usr/bin/env bash
# EB-75 [P6-04] — staging pipeline gate logic.
#
# Pure decision functions shared by the deploy-staging workflow steps and the
# local test suite (scripts/cicd/test-deploy-gate.sh). No side effects: every
# function prints its decision and returns a non-zero exit code when the gate
# blocks. Nothing here deploys anything.
#
# Gate invariants enforced (see ops/cicd/staging-pipeline.md):
#   G1  Only successful, completed triggering runs may proceed.
#   G2  Only main (the protected branch) may deploy to staging.
#   G3  The tested commit must equal the built commit must equal the deployed image.
#   G4  Every required configuration value must be present and non-empty.
#   G5  The migration target must never be the production Supabase project.
#   G6  Image refs are pinned by immutable digest, never by mutable tags.
#   G7  Automatic staging deployment is inert until the repository variable
#       STAGING_AUTO_DEPLOY_ENABLED is exactly 'true' (explicit activation).
#   G8  The migration target must match the explicitly approved staging
#       database identity (Supabase project allowlist) — substring blocking of
#       the production ref is only the first coarse line of defense.

set -u

# Short, immutable-ish tag used for the image (git describe when tags exist,
# short SHA otherwise). Callers pass explicit arguments so this stays testable.
gate_short_tag() {
    local sha="$1" describe="${2:-}"
    if [ -n "$describe" ]; then
        printf '%s' "$describe"
    else
        printf '%s' "${sha:0:7}"
    fi
}

# G1+G2+G3 — verify the workflow_run event that triggered us is trustworthy.
# Args: trigger_conclusion trigger_status trigger_head_branch trigger_head_sha current_sha
gate_verify_trigger() {
    local conclusion="$1" status="$2" head_branch="$3" head_sha="$4" current_sha="$5"

    if [ "$status" != "completed" ]; then
        echo "BLOCK: triggering workflow is not completed (status=$status)"
        return 1
    fi
    if [ "$conclusion" != "success" ]; then
        echo "BLOCK: triggering workflow did not succeed (conclusion=$conclusion) — never deploy from a failed run"
        return 1
    fi
    if [ "$head_branch" != "main" ]; then
        echo "BLOCK: triggering run is not from main (head_branch=$head_branch) — only approved refs may deploy"
        return 1
    fi
    if [ -z "$head_sha" ] || [ "$head_sha" != "$current_sha" ]; then
        echo "BLOCK: triggering run head_sha ($head_sha) does not match default-branch HEAD ($current_sha) — image/commit identity mismatch"
        return 1
    fi
    echo "OK: trigger verified (conclusion=$conclusion, branch=$head_branch, sha=${current_sha:0:7})"
    return 0
}

# G3 — image SHA tag must equal the verified commit (full SHA equality check;
# tags are derived from the same commit so the identity chain is commit == image).
gate_verify_image_sha_tag() {
    local image_sha_tag="$1" expected_sha="$2"
    if [ "$image_sha_tag" != "$expected_sha" ]; then
        echo "BLOCK: image tag $image_sha_tag does not match the verified commit $expected_sha"
        return 1
    fi
    echo "OK: image SHA tag matches verified commit"
    return 0
}

# G4 — ensure every required config value is present and non-empty.
# Args: names+values pairs; values may be empty. Prints the first missing name.
gate_require_config() {
    local name value
    while [ "$#" -ge 2 ]; do
        name="$1"; value="$2"; shift 2
        if [ -z "$value" ]; then
            echo "BLOCK: required configuration '$name' is missing or empty — blocking deployment instead of continuing silently"
            return 1
        fi
    done
    echo "OK: all required configuration present"
    return 0
}

# G5 — the migration (or any) target URL must not point at the production
# Supabase project ref. The production project ref is recorded in
# ops/staging/provision-staging.md section 0 and is not a secret.
gate_guard_migration_url() {
    local url="$1" prod_ref="ciogysvcykzxrjuskxmk"
    if [ -z "$url" ]; then
        echo "BLOCK: migration URL is empty"
        return 1
    fi
    case "$url" in
        *"$prod_ref"*)
            echo "BLOCK: migration target references the PRODUCTION Supabase project ref ($prod_ref) — staging migrations must never touch production"
            return 1
            ;;
    esac
    case "$url" in
        jdbc:postgresql://*) : ;;
        *) echo "BLOCK: migration URL must be a PostgreSQL JDBC URL (jdbc:postgresql://...)"; return 1 ;;
    esac
    echo "OK: migration URL is a non-production PostgreSQL JDBC target"
    return 0
}

# G6 — reject mutable/implicit tags in the deploy command. With an optional
# expected image prefix (e.g. "ghcr.io/moora86/edunest-backend@sha256:") the
# ref must additionally start with exactly that prefix, proving the image
# belongs to the approved GHCR repository.
gate_guard_image_ref() {
    local ref="$1" expected_prefix="${2:-}"
    case "$ref" in
        *@sha256:*) : ;;
        *:latest) echo "BLOCK: 'latest' tag is not deployable — pin by digest"; return 1 ;;
        "") echo "BLOCK: image ref is empty"; return 1 ;;
        *) echo "BLOCK: image ref must be digest-pinned (@sha256:...); got '$ref'"; return 1 ;;
    esac
    local digest="${ref##*@}"
    if [[ "$digest" =~ ^sha256:[0-9a-f]{64}$ ]]; then
        : 
    else
        echo "BLOCK: digest must be sha256 followed by exactly 64 lowercase hex characters"
        return 1
    fi
    if [ -n "$expected_prefix" ]; then
        case "$ref" in
            "$expected_prefix"*) echo "OK: image ref is digest-pinned and matches the approved repository prefix"; return 0 ;;
            *) echo "BLOCK: image ref does not belong to the approved GHCR repository (expected prefix '${expected_prefix}...')"; return 1 ;;
        esac
    fi
    echo "OK: image ref is digest-pinned"; return 0
}

# G3 — full commit SHA format check: exactly 40 lowercase hex characters.
gate_sha40() {
    local sha="$1"
    case "$sha" in
        [0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f]\
[0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f]\
[0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f]\
[0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f])
            echo "OK: commit SHA is a full 40-character lowercase hex SHA"; return 0 ;;
        *)
            echo "BLOCK: commit SHA must be a full 40-character lowercase hex SHA"
            return 1
            ;;
    esac
}

# G7 — explicit activation gate. Automatic staging deployment is disabled by
# default: the repository variable STAGING_AUTO_DEPLOY_ENABLED must be exactly
# 'true'. Any other value (absent, false, wrong casing) means NO migration and
# NO deployment. The check reads a non-secret repository variable, so skipping
# does not require an approval.
gate_activation() {
    local auto_deploy_enabled="$1"
    if [ "$auto_deploy_enabled" = "true" ]; then
        echo "OK: automatic staging deployment is activated (STAGING_AUTO_DEPLOY_ENABLED=true)"
        return 0
    fi
    echo "BLOCK: automatic staging deployment is NOT activated (STAGING_AUTO_DEPLOY_ENABLED is not exactly 'true') — no migration and no deployment will run; nothing external is touched"
    return 1
}

# G7m — explicit manual authorization gate. Manual dispatch deployments are
# ALSO disabled by default: the repository variable
# STAGING_MANUAL_DEPLOY_ENABLED must be exactly 'true'. Enabling it still does
# not bypass provenance verification (G3/G6, main-history ancestry, approved
# pipeline-run provenance, OCI revision label) or the 'staging' environment
# protection. Recommended prerequisite before enabling: configure the
# 'staging' GitHub Environment with required reviewers.
gate_manual_activation() {
    local manual_deploy_enabled="$1"
    if [ "$manual_deploy_enabled" = "true" ]; then
        echo "OK: manual staging deployment is authorized (STAGING_MANUAL_DEPLOY_ENABLED=true) — provenance and environment gates still apply"
        return 0
    fi
    echo "BLOCK: manual staging deployment is NOT authorized (STAGING_MANUAL_DEPLOY_ENABLED is not exactly 'true') — no migration and no deployment will run; nothing external is touched"
    return 1
}

# G8 — staging database identity allowlist, based on the ACTUAL identity
# Flyway connects with: FLYWAY_URL (host form) and FLYWAY_USER (the separate,
# actually-supplied username — the pom's db-migrate profile wires
# ${env.FLYWAY_URL}/${env.FLYWAY_USER}/${env.FLYWAY_PASSWORD}, so FLYWAY_USER
# is the real credential identity, not a URL parameter). Two Supabase
# connection forms are accepted:
#   pooler:  FLYWAY_USER ends with ".<approved_ref>"  AND host *.pooler.supabase.com
#   direct:  FLYWAY_USER == "postgres"                AND host db.<approved_ref>.supabase.co
# An approved project reference present only in an unrelated URL parameter is
# NOT accepted. Credential/URL values are parsed but NEVER printed. Args:
#   $1 jdbc url (jdbc:postgresql://...)
#   $2 actually-supplied DB username (FLYWAY_USER)
#   $3 approved staging Supabase project ref (repository variable
#      STAGING_DB_PROJECT_REF; empty => fail closed)
#   $4 production Supabase project ref (must never appear)
gate_staging_db_identity() {
    local url="$1" db_user="$2" approved_ref="$3" prod_ref="$4"
    if [ -z "$approved_ref" ]; then
        echo "BLOCK: approved staging database identity (STAGING_DB_PROJECT_REF) is not configured — refusing migration rather than trusting an unverified target"
        return 1
    fi
    if [ -z "$url" ]; then
        echo "BLOCK: migration URL is empty — no database identity to verify"
        return 1
    fi
    if [ -z "$db_user" ]; then
        echo "BLOCK: supplied database username is empty — refusing to migrate without a verifiable identity"
        return 1
    fi
    case "$url" in
        jdbc:postgresql://*) : ;;
        *) echo "BLOCK: staging database identity requires a PostgreSQL JDBC URL"; return 1 ;;
    esac
    # Host = everything between // and the first following '/', minus any
    # embedded userinfo and port. Parsed silently; never printed.
    local host
    host=$(printf '%s' "$url" | sed -n 's|^jdbc:postgresql://\([^/]*\)/.*|\1|p' | sed 's|.*@||' | sed 's|:.*||')
    # Defense in depth: the production ref must not appear in any identity
    # position (the coarse G5 check blocks it anywhere in the URL already).
    case "|$db_user|$host|" in
        *"$prod_ref"*)
            echo "BLOCK: migration target carries the PRODUCTION Supabase project identity — refusing"
            return 1
            ;;
    esac
    # Positive identity proof (the two allowed Supabase connection forms).
    local form=""
    case "$db_user" in
        *".${approved_ref}")
            case "$host" in
                *.pooler.supabase.com) form="pooler" ;;
            esac
            ;;
    esac
    if [ -z "$form" ] && [ "$db_user" = "postgres" ]; then
        case "$host" in
            "db.${approved_ref}.supabase.co") form="direct" ;;
        esac
    fi
    if [ -z "$form" ]; then
        echo "BLOCK: migration target does not match the approved staging database identity (FLYWAY_USER/host pair matches neither the approved pooler nor the approved direct connection form) — refusing migration"
        return 1
    fi
    echo "OK: migration target identity matches the approved staging database ($form form; credentials withheld)"
    return 0
}

# G3b — simple equality check used for artifact-vs-trigger commit comparison.
gate_commit_match() {
    local a="$1" b="$2" label="${3:-commit}"
    if [ -z "$a" ] || [ "$a" != "$b" ]; then
        echo "BLOCK: $label mismatch ('$a' vs '$b') — image identity cannot be proven"
        return 1
    fi
    echo "OK: $label matches"
    return 0
}

# CLI dispatcher used by GitHub Actions steps (and reusable by operators):
#   deploy-gate.sh trigger <conclusion> <status> <head_branch> <head_sha> <current_sha>
#   deploy-gate.sh migration-url <jdbc_url>
#   deploy-gate.sh staging-db-identity <jdbc_url> <db_user> <approved_staging_ref> <prod_ref>
#   deploy-gate.sh image-ref <ref> [expected_repo_prefix]
#   deploy-gate.sh sha40 <commit_sha>
#   deploy-gate.sh require-config <name> <value> [name value...]
#   deploy-gate.sh activation <STAGING_AUTO_DEPLOY_ENABLED value>
#   deploy-gate.sh manual-activation <STAGING_MANUAL_DEPLOY_ENABLED value>
# Sourcing this file (as the test suite does) must have no side effects.
case "${1:-}" in
    trigger)            shift; gate_verify_trigger "$@" ;;
    commit-match)       shift; gate_commit_match "$@" ;;
    migration-url)      shift; gate_guard_migration_url "$1" ;;
    staging-db-identity) shift; gate_staging_db_identity "$1" "$2" "$3" "$4" ;;
    image-ref)          shift; gate_guard_image_ref "$1" "$2" ;;
    sha40)              shift; gate_sha40 "$1" ;;
    require-config)     shift; gate_require_config "$@" ;;
    activation)         shift; gate_activation "$1" ;;
    manual-activation)  shift; gate_manual_activation "$1" ;;
    "")                 : ;;
    *)                  echo "BLOCK: unknown gate subcommand '$1'"; exit 1 ;;
esac
