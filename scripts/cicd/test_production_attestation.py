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
        common = {"release_sha": SHA, "target": TARGET, "issued_at": now - 1, "expires_at": now + 60}
        backup = {**common, "kind": "backup", "backup_id": "backup-1", "sha256": "b" * 64, "integrity_verified": True}
        payload, signature = self.signed(backup)
        self.assertTrue(verify(payload, signature, self.public, "backup", SHA, TARGET, now))
        for changed in ({**backup, "release_sha": "c" * 40}, {**backup, "target": "elsewhere"},
                        {**backup, "expires_at": now}, {**backup, "integrity_verified": False}):
            p, s = self.signed(changed)
            with self.assertRaises(GateFailure): verify(p, s, self.public, "backup", SHA, TARGET, now)
        with self.assertRaises(GateFailure): verify(payload + b" ", signature, self.public, "backup", SHA, TARGET, now)
        compat = {**common, "kind": "compatibility", "pending_versions": ["14"], "compatible": True,
                  "previous_image_digest": "sha256:" + "d" * 64}
        p, s = self.signed(compat)
        self.assertTrue(verify(p, s, self.public, "compatibility", SHA, TARGET, now, ("14",)))
        with self.assertRaises(GateFailure): verify(p, s, self.public, "compatibility", SHA, TARGET, now, ("15",))


if __name__ == "__main__": unittest.main()
