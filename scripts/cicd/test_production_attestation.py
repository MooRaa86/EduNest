import base64
import json
import subprocess
import tempfile
import time
import unittest
from pathlib import Path

from production_attestation import verify
from production_gate import GateFailure

SHA = "a" * 40
TARGET = "test-target"
ATTEMPT = "attempt_1234567890"


class ReplayStore:
    def __init__(self): self.used = set()
    def consume(self, attempt, kind, expires):
        if (attempt, kind) in self.used: return False
        self.used.add((attempt, kind))
        return True


class Observer:
    def __init__(self, digest, observed_at):
        self.digest, self.observed_at, self.authenticated = digest, observed_at, True
    def verify(self, target, digest, now):
        return self.authenticated and target == TARGET and digest == self.digest and 0 <= now - self.observed_at <= 60


class AttestationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.private = str(Path(cls.temp.name) / "private.pem")
        cls.public = str(Path(cls.temp.name) / "public.pem")
        subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", cls.private], check=True, capture_output=True)
        subprocess.run(["openssl", "pkey", "-in", cls.private, "-pubout", "-out", cls.public], check=True, capture_output=True)

    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()

    def signed(self, claim):
        payload = json.dumps(claim, separators=(",", ":")).encode()
        result = subprocess.run(["openssl", "dgst", "-sha256", "-sign", self.private], input=payload, capture_output=True, check=True)
        return payload, base64.b64encode(result.stdout).decode()

    def test_backup_and_compatibility_authenticated_and_bound(self):
        now = int(time.time())
        common = {"release_sha": SHA, "target": TARGET, "attempt": ATTEMPT, "issued_at": now - 1, "expires_at": now + 60}
        backup = {**common, "kind": "backup", "backup_id": "backup-1", "sha256": "b" * 64,
                  "integrity_verified": True, "backup_at": now - 2, "pending_versions": ["14"]}
        payload, signature = self.signed(backup)
        store = ReplayStore()
        self.assertTrue(verify(payload, signature, self.public, "backup", SHA, TARGET, now, ("14",), attempt=ATTEMPT, replay_store=store))
        with self.assertRaises(GateFailure): verify(payload, signature, self.public, "backup", SHA, TARGET, now, ("14",), attempt=ATTEMPT, replay_store=store)
        for changed in ({**backup, "release_sha": "c" * 40}, {**backup, "target": "elsewhere"},
                        {**backup, "expires_at": now}, {**backup, "integrity_verified": False}):
            p, s = self.signed(changed)
            with self.assertRaises(GateFailure): verify(p, s, self.public, "backup", SHA, TARGET, now, ("14",), attempt=ATTEMPT, replay_store=ReplayStore())
        with self.assertRaises(GateFailure): verify(payload + b" ", signature, self.public, "backup", SHA, TARGET, now, ("14",), attempt=ATTEMPT, replay_store=ReplayStore())
        with self.assertRaises(GateFailure): verify(payload, signature, self.public, "backup", SHA, TARGET, now, ("14",), attempt="different_attempt_123", replay_store=ReplayStore())
        with self.assertRaises(GateFailure): verify(payload, signature, self.public, "backup", SHA, TARGET, now, ("14",), attempt=ATTEMPT)
        p_old, s_old = self.signed({**backup, "backup_at": now - 86401})
        with self.assertRaises(GateFailure): verify(p_old, s_old, self.public, "backup", SHA, TARGET, now, ("14",), attempt=ATTEMPT, replay_store=ReplayStore())
        compat = {**common, "kind": "compatibility", "pending_versions": ["14"], "compatible": True,
                  "previous_image_digest": "sha256:" + "d" * 64}
        p, s = self.signed(compat)
        observer = Observer("sha256:" + "d" * 64, now)
        self.assertTrue(verify(p, s, self.public, "compatibility", SHA, TARGET, now, ("14",), attempt=ATTEMPT, serving_observer=observer, replay_store=ReplayStore()))
        with self.assertRaises(GateFailure): verify(p, s, self.public, "compatibility", SHA, TARGET, now, ("15",), attempt=ATTEMPT, serving_observer=observer, replay_store=ReplayStore())
        with self.assertRaises(GateFailure): verify(p, s, self.public, "compatibility", SHA, TARGET, now, ("14",), attempt=ATTEMPT, replay_store=ReplayStore())
        observer.digest = "sha256:" + "e" * 64
        with self.assertRaises(GateFailure): verify(p, s, self.public, "compatibility", SHA, TARGET, now, ("14",), attempt=ATTEMPT, serving_observer=observer, replay_store=ReplayStore())
        observer.digest = "sha256:" + "d" * 64
        observer.observed_at = now - 61
        with self.assertRaises(GateFailure): verify(p, s, self.public, "compatibility", SHA, TARGET, now, ("14",), attempt=ATTEMPT, serving_observer=observer, replay_store=ReplayStore())
        observer.observed_at, observer.authenticated = now, False
        with self.assertRaises(GateFailure): verify(p, s, self.public, "compatibility", SHA, TARGET, now, ("14",), attempt=ATTEMPT, serving_observer=observer, replay_store=ReplayStore())


if __name__ == "__main__": unittest.main()
