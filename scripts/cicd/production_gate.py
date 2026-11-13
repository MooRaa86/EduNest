"""EB-115 pure safety decisions and dependency-injected migration orchestration.

This module deliberately does not connect to a database or deploy an image.
Adapters must authenticate evidence and normalize their provider's data before
calling it; unchecked JSON or operator-supplied strings are not attestations.
"""
import re

SHA = re.compile(r"[0-9a-f]{40}\Z")
DIGEST = re.compile(r"ghcr\.io/mooraa86/edunest-backend@sha256:[0-9a-f]{64}\Z")


class GateFailure(Exception):
    """Safe-to-log failure with no credential-bearing external output."""


def release(event, branch, sha, current):
    if event != "push" or branch != "refs/heads/production":
        raise GateFailure("unauthorized release event")
    if not isinstance(sha, str) or not SHA.fullmatch(sha) or sha != current:
        raise GateFailure("invalid or stale release")


def image(ref, revision, sha):
    if not isinstance(ref, str) or not DIGEST.fullmatch(ref) or revision != sha or not SHA.fullmatch(sha):
        raise GateFailure("image provenance mismatch")


def migration_states(rows):
    """Return pending versions; reject anything outside a known-good state.

    Input must be a complete, adapter-validated Flyway report with one row per
    resolved/applied migration, not an inferred list of pending migrations.
    """
    if not isinstance(rows, list) or not rows:
        raise GateFailure("empty or invalid migration report")
    versions = set()
    pending = []
    for row in rows:
        if not isinstance(row, dict) or set(row) != {"version", "state"}:
            raise GateFailure("invalid migration report row")
        version, state = row["version"], row["state"]
        if not isinstance(version, str) or not re.fullmatch(r"[0-9]+(?:\.[0-9]+)*", version):
            raise GateFailure("invalid migration version")
        if version in versions:
            raise GateFailure("duplicate migration version")
        versions.add(version)
        if state == "Pending":
            pending.append(version)
        elif state not in ("Success", "Baseline"):
            raise GateFailure("unsafe or unknown migration state")
    return pending


def migrate(adapter, sha):
    """Enforce gate ordering; external adapter exceptions are sanitized.

    The adapter is responsible for genuine DB identity, current-head lookup,
    trusted backup/compatibility attestation verification, and Flyway calls.
    It MUST return booleans, not truthy strings, from its check methods.
    """
    try:
        if adapter.identity() is not True:
            raise GateFailure("database identity unverified")
        adapter.validate_pending_allowed()
        pending = migration_states(adapter.info())
        if pending:
            if adapter.backup_verified(sha) is not True:
                raise GateFailure("backup evidence unverified")
            if adapter.compatibility_verified(sha, tuple(pending)) is not True:
                raise GateFailure("compatibility evidence unverified")
            if adapter.current_sha() != sha:
                raise GateFailure("stale before migration")
            if adapter.identity() is not True:
                raise GateFailure("database identity changed")
            adapter.migrate()
        adapter.validate_strict()
        if migration_states(adapter.info()):
            raise GateFailure("pending migrations after validation")
    except GateFailure:
        raise
    except Exception:
        raise GateFailure("migration adapter failed; inspect protected diagnostics") from None


def deployment_result(expected_id, expected_digest, observed):
    """Require a positively identified terminal deployment, not old health."""
    if (not expected_id or not DIGEST.fullmatch(expected_digest or "")
            or not isinstance(observed, dict) or observed.get("id") != expected_id
            or observed.get("status") != "SUCCESS" or observed.get("image_digest") != expected_digest):
        raise GateFailure("deployment outcome or artifact identity unverified")


def simulate_release(adapter, event, branch, sha, digest):
    """Exercise the complete ordering with an injected, non-production adapter.

    This function is intentionally not a production entrypoint: provider
    adapters, evidence attestation and a cross-system lock remain unverified.
    Never wire it to real credentials without those controls.
    """
    try:
        if adapter.activation() is not True:
            raise GateFailure("automation disabled")
        release(event, branch, sha, adapter.current_sha())
        if adapter.tests(sha) is not True:
            raise GateFailure("release tests failed")
        if adapter.build(sha) != digest:
            raise GateFailure("built digest mismatch")
        image(digest, adapter.revision(digest), sha)
        if adapter.rehearsal(sha) is not True:
            raise GateFailure("migration rehearsal failed")
        migrate(adapter, sha)
        if adapter.activation() is not True or adapter.current_sha() != sha or adapter.identity() is not True:
            raise GateFailure("pre-deployment recheck failed")
        deploy_id = adapter.deploy(digest)
        if not isinstance(deploy_id, str) or not deploy_id:
            raise GateFailure("deployment id unverified; mutation outcome unknown")
        deployment_result(deploy_id, digest, adapter.track(deploy_id))
        if adapter.runtime_identity(sha, digest) is not True:
            raise GateFailure("runtime identity unverified")
        if adapter.health() is not True:
            raise GateFailure("health verification failed")
    except GateFailure:
        raise
    except Exception:
        raise GateFailure("release adapter failed; mutation outcome may be unknown") from None
