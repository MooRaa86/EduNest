#!/usr/bin/env bash
# ==============================================================================
# Unit and Scenario Tests for EB-77 Repository Hygiene Gate
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GATE_SCRIPT="${SCRIPT_DIR}/repo-hygiene-gate.sh"

pass=0
fail=0

assert_passes() {
    local name="$1"
    shift
    if "$@"; then
        echo "PASS  $name"
        pass=$((pass + 1))
    else
        echo "FAIL  $name (expected success, got exit $?)" >&2
        fail=$((fail + 1))
    fi
}

assert_blocks() {
    local name="$1"
    local expected_pattern="$2"
    shift 2
    local output=""
    local code=0
    output=$("$@" 2>&1) || code=$?

    if [ "$code" -ne 0 ] && echo "$output" | grep -qE "$expected_pattern"; then
        echo "PASS  $name"
        pass=$((pass + 1))
    else
        echo "FAIL  $name (exit=$code, expected non-zero matching '$expected_pattern')" >&2
        echo "--- output ---" >&2
        echo "$output" >&2
        echo "--------------" >&2
        fail=$((fail + 1))
    fi
}

echo "=== Testing EB-77 Repository Hygiene Gate against current repository ==="
assert_passes "Current repository HEAD has zero tracked uploads" bash "$GATE_SCRIPT" check-head

echo "=== Testing Scenarios in Disposable Isolated Git Repositories ==="
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

setup_sandbox() {
    local sandbox="${TMP_DIR}/sandbox_$1"
    rm -rf "$sandbox"
    mkdir -p "$sandbox"
    cd "$sandbox"
    git init -q -b main
    git config user.name "Test Bot"
    git config user.email "bot@example.com"
    echo "# Root" > README.md
    git add README.md
    git commit -q -m "initial commit"
}

# Scenario 1: Root upload file staged in index
setup_sandbox "root_upload"
mkdir -p uploads
echo "dummy" > uploads/photo.png
git add uploads/photo.png
assert_blocks "Staged root uploads/photo.png blocks check-head" "uploads/photo\.png" bash "$GATE_SCRIPT" check-head

# Scenario 2: Nested uploads folder staged in index
setup_sandbox "nested_upload"
mkdir -p sub/nested/uploads
echo "dummy" > sub/nested/uploads/doc.pdf
git add sub/nested/uploads/doc.pdf
assert_blocks "Staged nested uploads file blocks check-head" "sub/nested/uploads/doc\.pdf" bash "$GATE_SCRIPT" check-head

# Scenario 3: Hidden .uploads folder staged in index
setup_sandbox "hidden_upload"
mkdir -p .uploads
echo "dummy" > .uploads/secret.txt
git add -f .uploads/secret.txt
assert_blocks "Staged .uploads file blocks check-head" "\.uploads/secret\.txt" bash "$GATE_SCRIPT" check-head

# Scenario 4: Safe files containing 'upload' in filename do NOT block
setup_sandbox "safe_files"
mkdir -p src ops/runbooks
echo "class UploadPolicy {}" > src/UploadPolicy.java
echo "# Hardening" > ops/runbooks/Invoke-UploadTreeEvidence.ps1
git add src/UploadPolicy.java ops/runbooks/Invoke-UploadTreeEvidence.ps1
assert_passes "Files with 'Upload' in name (not uploads/ dir) pass check-head" bash "$GATE_SCRIPT" check-head

# Scenario 5: Clean commit range passes check-range
setup_sandbox "clean_range"
echo "feature 1" > feature1.txt
git add feature1.txt
git commit -q -m "feature 1"
echo "feature 2" > feature2.txt
git add feature2.txt
git commit -q -m "feature 2"
assert_passes "Clean commit range passes check-range" bash "$GATE_SCRIPT" check-range HEAD~2 HEAD

# Scenario 6: Upload committed in range blocks check-range
setup_sandbox "committed_upload_range"
mkdir -p uploads
echo "test" > uploads/test.jpg
git add uploads/test.jpg
git commit -q -m "add test upload"
assert_blocks "Committed upload in range blocks check-range" "uploads/test\.jpg" bash "$GATE_SCRIPT" check-range HEAD~1 HEAD

# Scenario 7: Upload committed and then deleted later in same range blocks check-range (Requirement 3)
setup_sandbox "add_then_delete_range"
mkdir -p uploads
echo "temp upload" > uploads/temp.pdf
git add uploads/temp.pdf
git commit -q -m "add temporary upload"
git rm -q uploads/temp.pdf
git commit -q -m "remove temporary upload"
# In HEAD, the file is gone:
assert_passes "Deleted upload is no longer in HEAD (check-head passes)" bash "$GATE_SCRIPT" check-head
# But in the range, the introduction must be detected and blocked:
assert_blocks "Upload introduced then deleted within range blocks check-range" "uploads/temp\.pdf" bash "$GATE_SCRIPT" check-range HEAD~2 HEAD

# Scenario 8: Nested upload committed and deleted later in same range blocks check-range
setup_sandbox "nested_add_then_delete"
mkdir -p modules/service/uploads
echo "data" > modules/service/uploads/data.zip
git add modules/service/uploads/data.zip
git commit -q -m "nested upload addition"
git rm -q modules/service/uploads/data.zip
git commit -q -m "cleanup nested upload"
assert_blocks "Nested upload added and deleted blocks check-range" "modules/service/uploads/data\.zip" bash "$GATE_SCRIPT" check-range HEAD~2 HEAD

# Scenario 9: check-ci with pull_request event
setup_sandbox "ci_pr_event"
git checkout -q -b feat/test-branch
echo "pr work" > work.txt
git add work.txt
git commit -q -m "work"
assert_passes "Clean pull_request event passes check-ci" bash "$GATE_SCRIPT" check-ci "pull_request" "main" "" "HEAD"

mkdir -p uploads
echo "leak" > uploads/leak.png
git add uploads/leak.png
git commit -q -m "accidental leak"
assert_blocks "pull_request event with upload blocks check-ci" "uploads/leak\.png" bash "$GATE_SCRIPT" check-ci "pull_request" "main" "" "HEAD"

# Scenario 10: check-ci with push event
setup_sandbox "ci_push_event"
BEFORE_SHA=$(git rev-parse HEAD)
echo "new code" > code.txt
git add code.txt
git commit -q -m "commit 1"
AFTER_SHA=$(git rev-parse HEAD)
assert_passes "Clean push event passes check-ci" bash "$GATE_SCRIPT" check-ci "push" "" "$BEFORE_SHA" "$AFTER_SHA"

mkdir -p uploads
echo "bad" > uploads/bad.png
git add uploads/bad.png
git commit -q -m "commit 2"
BAD_SHA=$(git rev-parse HEAD)
assert_blocks "Push event with upload blocks check-ci" "uploads/bad\.png" bash "$GATE_SCRIPT" check-ci "push" "" "$BEFORE_SHA" "$BAD_SHA"

echo "----------------------------------------"
echo "repo-hygiene-gate tests: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
