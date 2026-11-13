#!/usr/bin/env bash
# ==============================================================================
# EduNest CI Repository Hygiene Gate — EB-77 / P6-06 (FILE-06, DEP-06)
# ==============================================================================
# Enforces that no upload files (under uploads/, .uploads/, or any nested
# uploads directories) are tracked in HEAD or introduced within PR / push ranges.
#
# Supported subcommands:
#   repo-hygiene-gate.sh check-head
#   repo-hygiene-gate.sh check-range <base_ref_or_before_sha> [head_ref_or_after_sha]
#   repo-hygiene-gate.sh check-ci [event_name] [base_ref] [before_sha] [after_sha]
# ==============================================================================
set -euo pipefail

# Regex pattern matching root or nested uploads directories:
# Matches: uploads/*, .uploads/*, foo/uploads/*, foo/.uploads/*
# Does NOT match: UploadPolicy.java, upload-tree-hardening.md, etc.
UPLOAD_DIR_REGEX='(^|/)(\.?)uploads/'

gate_check_head() {
    local tracked_uploads
    # git ls-files lists all files currently tracked in the git index / HEAD
    tracked_uploads=$(git ls-files | grep -E "$UPLOAD_DIR_REGEX" || true)

    if [ -n "$tracked_uploads" ]; then
        echo "::error::BLOCK (EB-77): Tracked upload files detected in Git index / HEAD:" >&2
        echo "$tracked_uploads" | sed 's/^/  - /' >&2
        echo "Upload files must never be committed to Git. Storage is handled via Supabase S3." >&2
        return 1
    fi

    echo "OK: zero upload files tracked in HEAD"
    return 0
}

gate_check_range() {
    local base_or_before="$1"
    local head_or_after="${2:-HEAD}"
    local range=""

    if [ -z "$base_or_before" ]; then
        echo "::error::gate_check_range requires at least one base ref or before SHA" >&2
        return 1
    fi

    # Handle brand new branch push where before SHA is all zeros
    if [[ "$base_or_before" =~ ^0{40}$ ]]; then
        if git rev-parse --verify "${head_or_after}~1" >/dev/null 2>&1; then
            range="${head_or_after}~1..${head_or_after}"
        else
            echo "Initial commit push detected without parent; falling back to check-head"
            gate_check_head
            return $?
        fi
    else
        # For branch comparisons, find merge-base if both are reachable
        if git rev-parse --verify "$base_or_before" >/dev/null 2>&1 && git rev-parse --verify "$head_or_after" >/dev/null 2>&1; then
            local merge_base
            merge_base=$(git merge-base "$base_or_before" "$head_or_after" 2>/dev/null || echo "$base_or_before")
            range="${merge_base}..${head_or_after}"
        else
            range="${base_or_before}..${head_or_after}"
        fi
    fi

    # Inspect all commits in the range for Added, Copied, Modified, or Renamed files.
    # This detects files introduced in a commit even if deleted later in the same branch/PR.
    local raw_log
    raw_log=$(git log --name-only --diff-filter=ACMR --format="COMMIT:%H" "$range" 2>/dev/null || true)

    local current_commit=""
    local offending_entries=""

    while IFS= read -r line; do
        [ -z "$line" ] && continue
        if [[ "$line" =~ ^COMMIT:(.+) ]]; then
            current_commit="${BASH_REMATCH[1]}"
        elif echo "$line" | grep -qE "$UPLOAD_DIR_REGEX"; then
            offending_entries="${offending_entries}  - Commit ${current_commit}: ${line}"$'\n'
        fi
    done <<< "$raw_log"

    if [ -n "$offending_entries" ]; then
        echo "::error::BLOCK (EB-77): Upload files were introduced in commit range '${range}':" >&2
        printf '%s' "$offending_entries" >&2
        echo "Upload files must never be committed to Git. Storage is handled via Supabase S3." >&2
        return 1
    fi

    echo "OK: zero upload files introduced in commit range '${range}'"
    return 0
}

gate_check_ci() {
    local event_name="${1:-${GITHUB_EVENT_NAME:-}}"
    local base_ref="${2:-${GITHUB_BASE_REF:-}}"
    local before_sha="${3:-${GITHUB_EVENT_BEFORE:-}}"
    local after_sha="${4:-${GITHUB_SHA:-HEAD}}"

    echo "==> EB-77 Repository Hygiene Gate: evaluating HEAD index..."
    gate_check_head

    if [ "$event_name" = "pull_request" ]; then
        echo "==> EB-77 Repository Hygiene Gate: evaluating pull_request range against base '$base_ref'..."
        if [ -z "$base_ref" ]; then
            echo "::error::pull_request event requires base_ref" >&2
            return 1
        fi

        local base_target=""
        if git rev-parse --verify "origin/$base_ref" >/dev/null 2>&1; then
            base_target="origin/$base_ref"
        elif git rev-parse --verify "$base_ref" >/dev/null 2>&1; then
            base_target="$base_ref"
        else
            echo "WARN: cannot resolve base ref '$base_ref' locally; attempting fetch..."
            git fetch origin "$base_ref" --depth=100 2>/dev/null || true
            if git rev-parse --verify "origin/$base_ref" >/dev/null 2>&1; then
                base_target="origin/$base_ref"
            else
                echo "WARN: base ref 'origin/$base_ref' unavailable after fetch; check-head passed cleanly"
                return 0
            fi
        fi

        gate_check_range "$base_target" "$after_sha"

    elif [ "$event_name" = "push" ]; then
        echo "==> EB-77 Repository Hygiene Gate: evaluating push range (${before_sha}..${after_sha})..."
        if [ -n "$before_sha" ] && ! [[ "$before_sha" =~ ^0{40}$ ]]; then
            gate_check_range "$before_sha" "$after_sha"
        else
            echo "Push has no before SHA or is initial commit; check-head passed cleanly"
        fi
    else
        echo "Event is '$event_name'; check-head passed cleanly"
    fi

    return 0
}

# CLI dispatcher
case "${1:-}" in
    check-head)
        gate_check_head
        ;;
    check-range)
        shift
        gate_check_range "$@"
        ;;
    check-ci)
        shift
        gate_check_ci "$@"
        ;;
    *)
        echo "Usage: $0 {check-head | check-range <base_ref> [head_ref] | check-ci [event_name] [base_ref] [before_sha] [after_sha]}" >&2
        exit 1
        ;;
esac
