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

# G6 — reject mutable/implicit tags in the deploy command.
gate_guard_image_ref() {
    local ref="$1"
    case "$ref" in
        *@sha256:*) echo "OK: image ref is digest-pinned"; return 0 ;;
        *:latest) echo "BLOCK: 'latest' tag is not deployable — pin by digest"; return 1 ;;
        "") echo "BLOCK: image ref is empty"; return 1 ;;
        *) echo "BLOCK: image ref must be digest-pinned (@sha256:...); got '$ref'"; return 1 ;;
    esac
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
#   deploy-gate.sh image-ref <ref>
#   deploy-gate.sh require-config <name> <value> [name value...]
# Sourcing this file (as the test suite does) must have no side effects.
case "${1:-}" in
    trigger)        shift; gate_verify_trigger "$@" ;;
    commit-match)   shift; gate_commit_match "$@" ;;
    migration-url)  shift; gate_guard_migration_url "$1" ;;
    image-ref)      shift; gate_guard_image_ref "$1" ;;
    require-config) shift; gate_require_config "$@" ;;
    "")             : ;;
    *)              echo "BLOCK: unknown gate subcommand '$1'"; exit 1 ;;
esac
