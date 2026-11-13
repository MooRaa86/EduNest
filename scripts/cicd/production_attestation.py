"""Verify detached signed, release-bound operational evidence.

The public key must be supplied from independently protected configuration. A
valid signature proves the signer made the claim; operators must separately
ensure the signer verifies backup existence, integrity and restore capability.
"""
import base64
import json
import re
import subprocess
import tempfile
from pathlib import Path

from production_gate import GateFailure, SHA

HEX64 = re.compile(r"[0-9a-f]{64}\Z")


def verify(payload, signature, public_key, expected_kind, sha, target, now, pending=()):
    """Authenticate exact bytes then validate purpose, identity and expiry."""
    if not isinstance(payload, bytes) or len(payload) > 65536 or not SHA.fullmatch(sha or ""):
        raise GateFailure("invalid attestation input")
    try:
        sig = base64.b64decode(signature, validate=True)
        if not sig or len(sig) > 8192:
            raise ValueError("invalid signature")
        with tempfile.TemporaryDirectory(prefix="eb115-attestation-") as directory:
            sigpath = Path(directory) / "signature"
            sigpath.write_bytes(sig)
            result = subprocess.run(["openssl", "dgst", "-sha256", "-verify", public_key,
                                     "-signature", str(sigpath)], input=payload,
                                    capture_output=True, timeout=10, check=False)
        if result.returncode != 0:
            raise ValueError("signature mismatch")
        # Reject duplicate keys rather than letting a parser select one value.
        def unique(pairs):
            result = {}
            for key, value in pairs:
                if key in result:
                    raise ValueError("duplicate claim")
                result[key] = value
            return result
        claim = json.loads(payload, object_pairs_hook=unique)
        if not isinstance(claim, dict) or claim.get("kind") != expected_kind or claim.get("release_sha") != sha:
            raise ValueError("wrong release or kind")
        if not target or claim.get("target") != target:
            raise ValueError("wrong target")
        issued, expires = claim.get("issued_at"), claim.get("expires_at")
        if type(issued) is not int or type(expires) is not int or not issued <= now < expires or expires - issued > 86400:
            raise ValueError("invalid evidence lifetime")
        if expected_kind == "backup":
            if (not isinstance(claim.get("backup_id"), str) or not claim["backup_id"].strip()
                    or not HEX64.fullmatch(claim.get("sha256", "")) or claim.get("integrity_verified") is not True):
                raise ValueError("invalid backup evidence")
        elif expected_kind == "compatibility":
            versions = claim.get("pending_versions")
            if (not isinstance(versions, list) or any(not isinstance(v, str) for v in versions)
                    or len(set(versions)) != len(versions) or sorted(versions) != sorted(pending)
                    or claim.get("compatible") is not True
                    or not isinstance(claim.get("previous_image_digest"), str)
                    or not re.fullmatch(r"sha256:[0-9a-f]{64}", claim["previous_image_digest"])):
                raise ValueError("invalid compatibility evidence")
        else:
            raise ValueError("unknown evidence type")
    except (OSError, ValueError, TypeError, subprocess.TimeoutExpired, json.JSONDecodeError):
        raise GateFailure("attestation invalid or unverifiable") from None
    return True
