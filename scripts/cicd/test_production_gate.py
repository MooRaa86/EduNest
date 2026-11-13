import unittest
from production_gate import GateFailure, deployment_result, image, migrate, migration_states, release, simulate_release

SHA = "a" * 40
REF = "ghcr.io/mooraa86/edunest-backend@sha256:" + "b" * 64


class Adapter:
    def __init__(self, pending):
        self.events = []
        self.pending = pending
        self.identity_ok = self.backup_ok = self.compat_ok = True
        self.sha = SHA
        self.failure = False

    def identity(self):
        self.events.append("identity")
        return self.identity_ok

    def validate_pending_allowed(self): self.events.append("validate-pre")
    def validate_strict(self): self.events.append("validate-strict")
    def info(self):
        self.events.append("info")
        return [{"version": "1", "state": "Success"},
                {"version": "2", "state": "Pending" if self.pending else "Success"}]
    def backup_verified(self, sha):
        self.events.append("backup")
        return self.backup_ok
    def compatibility_verified(self, sha, pending):
        self.events.append("compatibility")
        return self.compat_ok
    def current_sha(self):
        self.events.append("freshness")
        return self.sha
    def migrate(self):
        self.events.append("migrate")
        if self.failure: raise RuntimeError("password=secret-test-value")
        self.pending = False


class FakeRelease(Adapter):
    def __init__(self, pending):
        super().__init__(pending)
        self.enabled = self.test_ok = self.rehearsal_ok = self.runtime_ok = self.health_ok = True
        self.tracked = {"id": "deploy-1", "status": "SUCCESS", "image_digest": REF}
    def activation(self): self.events.append("activation"); return self.enabled
    def tests(self, sha): self.events.append("tests"); return self.test_ok
    def build(self, sha): self.events.append("build"); return REF
    def revision(self, digest): self.events.append("revision"); return SHA
    def rehearsal(self, sha): self.events.append("rehearsal"); return self.rehearsal_ok
    def deploy(self, digest): self.events.append("deploy"); return "deploy-1"
    def track(self, deploy_id): self.events.append("track"); return self.tracked
    def runtime_identity(self, sha, digest): self.events.append("runtime"); return self.runtime_ok
    def health(self): self.events.append("health"); return self.health_ok


class ProductionGateTests(unittest.TestCase):
    def test_release_and_digest(self):
        release("push", "refs/heads/production", SHA, SHA)
        image(REF, SHA, SHA)
        for args in (("pull_request", "refs/heads/production", SHA, SHA),
                     ("push", "refs/heads/main", SHA, SHA),
                     ("push", "refs/heads/production", SHA, "c" * 40)):
            with self.assertRaises(GateFailure): release(*args)
        for ref in (REF[:-1], "ghcr.io/mooraa86/edunest-backend:latest", REF.replace("mooraa86", "other")):
            with self.assertRaises(GateFailure): image(ref, SHA, SHA)
        with self.assertRaises(GateFailure): image(REF, "c" * 40, SHA)

    def test_unknown_flyway_states_block(self):
        for state in ("Failed", "Future", "Missing", "Checksum mismatch", "Unknown", "Out of Order"):
            with self.subTest(state=state), self.assertRaises(GateFailure):
                migration_states([{"version": "1", "state": state}])
        with self.assertRaises(GateFailure): migration_states([])
        with self.assertRaises(GateFailure): migration_states([{"version": "1", "state": "Success"}] * 2)

    def test_no_pending_never_migrates(self):
        adapter = Adapter(False)
        migrate(adapter, SHA)
        self.assertEqual(adapter.events, ["identity", "validate-pre", "info", "validate-strict", "info"])

    def test_pending_requires_evidence_and_freshness(self):
        adapter = Adapter(True)
        migrate(adapter, SHA)
        self.assertEqual(adapter.events, ["identity", "validate-pre", "info", "backup", "compatibility",
                                          "freshness", "identity", "migrate", "validate-strict", "info"])
        for attribute, value in (("identity_ok", False), ("backup_ok", False), ("compat_ok", False),
                                 ("backup_ok", "true"), ("sha", "c" * 40)):
            adapter = Adapter(True)
            setattr(adapter, attribute, value)
            with self.assertRaises(GateFailure): migrate(adapter, SHA)
            self.assertNotIn("migrate", adapter.events)

    def test_failure_redacts_adapter_output(self):
        adapter = Adapter(True)
        adapter.failure = True
        with self.assertRaises(GateFailure) as error: migrate(adapter, SHA)
        self.assertNotIn("secret-test-value", str(error.exception))
        self.assertNotIn("validate-strict", adapter.events)

    def test_deployment_requires_id_digest_and_terminal_success(self):
        good = {"id": "deploy-1", "status": "SUCCESS", "image_digest": REF}
        deployment_result("deploy-1", REF, good)
        for update in ({"id": "other"}, {"status": "FAILED"}, {"status": "ACTIVE"},
                       {"image_digest": "unknown"}):
            with self.assertRaises(GateFailure): deployment_result("deploy-1", REF, {**good, **update})

    def test_simulated_release_sequence(self):
        adapter = FakeRelease(True)
        simulate_release(adapter, "push", "refs/heads/production", SHA, REF)
        self.assertLess(adapter.events.index("migrate"), adapter.events.index("deploy"))
        self.assertLess(adapter.events.index("validate-strict"), adapter.events.index("deploy"))
        self.assertLess(adapter.events.index("track"), adapter.events.index("health"))

    def test_failed_simulation_does_not_deploy(self):
        for attr, value in (("enabled", False), ("test_ok", False), ("rehearsal_ok", False),
                            ("backup_ok", False), ("compat_ok", False), ("failure", True)):
            with self.subTest(attr=attr):
                adapter = FakeRelease(True)
                setattr(adapter, attr, value)
                with self.assertRaises(GateFailure):
                    simulate_release(adapter, "push", "refs/heads/production", SHA, REF)
                self.assertNotIn("deploy", adapter.events)

    def test_unverified_deployment_never_reports_success(self):
        adapter = FakeRelease(False)
        adapter.tracked = {"id": "deploy-1", "status": "SUCCESS"}  # missing actual digest
        with self.assertRaises(GateFailure):
            simulate_release(adapter, "push", "refs/heads/production", SHA, REF)
        self.assertNotIn("health", adapter.events)


if __name__ == "__main__": unittest.main()
