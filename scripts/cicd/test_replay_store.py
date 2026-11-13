#!/usr/bin/env python3
"""Security tests for Attestation Replay Stores (EB-115 Slice 2A)."""
import io
import json
import tempfile
import threading
import unittest
from pathlib import Path
from urllib.error import HTTPError

from production_gate import GateFailure
from replay_store import (
    DatabaseReplayStore,
    HmacDurableReplayStore,
    InMemoryReplayStore,
    RemoteHttpReplayStore,
)

ATTEMPT = "attempt_1234567890abcdef"
HMAC_KEY = b"secure_random_key_with_at_least_32_bytes_length!"


class ReplayStoreSecurityTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.store_path = Path(self.temp_dir.name) / "replay.json"

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_reused_or_expired_evidence(self):
        store = InMemoryReplayStore()
        now = 1000

        # First consumption -> PASS
        self.assertTrue(store.consume(ATTEMPT, "backup", expires_at=now + 60, now=now))

        # Reused consumption of same attempt and kind -> REJECT
        self.assertFalse(store.consume(ATTEMPT, "backup", expires_at=now + 60, now=now))

        # Distinct kind for same attempt can be consumed once -> PASS
        self.assertTrue(store.consume(ATTEMPT, "compatibility", expires_at=now + 60, now=now))

        # Expired evidence -> REJECT (returns False, not consumed)
        self.assertFalse(store.consume("new_attempt_9876543210", "backup", expires_at=now - 1, now=now))
        self.assertFalse(store.consume("new_attempt_9876543210", "backup", expires_at=now, now=now))

    def test_replay_races_and_concurrent_requests(self):
        store = InMemoryReplayStore()
        now = 1000
        results = []
        threads = []

        def worker():
            res = store.consume(ATTEMPT, "backup", expires_at=now + 60, now=now)
            results.append(res)

        for _ in range(25):
            t = threading.Thread(target=worker)
            threads.append(t)

        for t in threads:
            t.start()
        for t in threads:
            t.join()

        # Exactly ONE thread must succeed in consuming the token; 24 must fail
        self.assertEqual(results.count(True), 1)
        self.assertEqual(results.count(False), 24)

    def test_hmac_durable_replay_store_persistence_and_races(self):
        store = HmacDurableReplayStore(self.store_path, HMAC_KEY)
        now = 1000

        # Consume first attempt
        self.assertTrue(store.consume(ATTEMPT, "backup", expires_at=now + 60, now=now))
        self.assertFalse(store.consume(ATTEMPT, "backup", expires_at=now + 60, now=now))

        # Reload store from disk using a fresh store instance (simulating independent runner / invocation)
        fresh_store = HmacDurableReplayStore(self.store_path, HMAC_KEY)
        self.assertFalse(fresh_store.consume(ATTEMPT, "backup", expires_at=now + 60, now=now))

        # New attempt on fresh store -> PASS
        self.assertTrue(fresh_store.consume("another_attempt_123456", "backup", expires_at=now + 60, now=now))

        # Concurrent threads on durable store
        results = []
        threads = []

        def durable_worker():
            s = HmacDurableReplayStore(self.store_path, HMAC_KEY)
            res = s.consume("concurrent_attempt_9999", "compatibility", expires_at=now + 60, now=now)
            results.append(res)

        for _ in range(10):
            t = threading.Thread(target=durable_worker)
            threads.append(t)
        for t in threads:
            t.start()
        for t in threads:
            t.join()

        self.assertEqual(results.count(True), 1)
        self.assertEqual(results.count(False), 9)

    def test_hmac_tamper_detection_and_fail_closed(self):
        # 1. Require strong key (>= 32 bytes)
        with self.assertRaises(GateFailure) as ctx:
            HmacDurableReplayStore(self.store_path, b"short_key")
        self.assertIn("at least 32 bytes", str(ctx.exception))

        # 2. Consume an entry
        store = HmacDurableReplayStore(self.store_path, HMAC_KEY)
        self.assertTrue(store.consume(ATTEMPT, "backup", expires_at=2000, now=1000))

        # 3. Simulate attacker modifying payload in storage file without valid key
        raw_json = json.loads(self.store_path.read_text(encoding="utf-8"))
        payload = json.loads(raw_json["payload"])
        payload["records"].clear()  # Attacker wiped records to replay!
        raw_json["payload"] = json.dumps(payload)
        self.store_path.write_text(json.dumps(raw_json), encoding="utf-8")

        # 4. Verifier must detect tamper and fail closed
        tampered_store = HmacDurableReplayStore(self.store_path, HMAC_KEY)
        with self.assertRaises(GateFailure) as ctx:
            tampered_store.consume("attempt_next_12345678", "backup", expires_at=2000, now=1000)
        self.assertIn("tamper detected", str(ctx.exception))

    def test_remote_http_replay_store(self):
        class MockResponse(io.BytesIO):
            def __init__(self, code, body=b"{}"):
                super().__init__(body)
                self.status = code
            def getcode(self):
                return self.status

        # 1. First consume -> HTTP 201 -> True
        def opener_success(req, timeout):
            self.assertEqual(req.get_header("Authorization"), "Bearer secret_token")
            return MockResponse(201, b'{"status":"consumed"}')

        remote = RemoteHttpReplayStore("https://vault.internal/replay", "secret_token", opener=opener_success)
        self.assertTrue(remote.consume(ATTEMPT, "backup", expires_at=2000, now=1000))

        # 2. Duplicate consume -> HTTP 409 Conflict -> False
        def opener_conflict(req, timeout):
            raise HTTPError(req.full_url, 409, "Conflict", hdrs={}, fp=io.BytesIO(b'{"error":"conflict"}'))

        remote_conflict = RemoteHttpReplayStore("https://vault.internal/replay", "secret_token", opener=opener_conflict)
        self.assertFalse(remote_conflict.consume(ATTEMPT, "backup", expires_at=2000, now=1000))

        # 3. Server error -> HTTP 500 -> GateFailure (fail closed)
        def opener_error(req, timeout):
            raise HTTPError(req.full_url, 500, "Internal Server Error", hdrs={}, fp=io.BytesIO(b'{"error":"fatal"}'))

        remote_err = RemoteHttpReplayStore("https://vault.internal/replay", "secret_token", opener=opener_error)
        with self.assertRaises(GateFailure) as ctx:
            remote_err.consume(ATTEMPT, "backup", expires_at=2000, now=1000)
        self.assertIn("HTTP error: 500", str(ctx.exception))

        # 4. Rejection of insecure non-HTTPS endpoint
        with self.assertRaises(GateFailure) as ctx:
            RemoteHttpReplayStore("http://unencrypted.server.com/replay", "token")
        self.assertIn("must use HTTPS", str(ctx.exception))

    def test_database_replay_store(self):
        class MockCursor:
            def __init__(self, rowcount=1, fail=False):
                self.rowcount = rowcount
                self.fail = fail
                self.executed = []

            def execute(self, sql, params):
                if self.fail:
                    raise RuntimeError("SQL execution error")
                self.executed.append((sql, params))

        class MockConnection:
            def __init__(self, cursor):
                self._cursor = cursor
                self.committed = False
                self.closed = False

            def cursor(self):
                return self._cursor

            def commit(self):
                self.committed = True

            def close(self):
                self.closed = True

        # 1. First consume -> rowcount 1 -> True
        cursor1 = MockCursor(rowcount=1)
        db_store = DatabaseReplayStore(connector=lambda: MockConnection(cursor1))
        self.assertTrue(db_store.consume(ATTEMPT, "backup", expires_at=2000, now=1000))

        # 2. Duplicate consume -> rowcount 0 (ON CONFLICT DO NOTHING) -> False
        cursor0 = MockCursor(rowcount=0)
        db_store_dup = DatabaseReplayStore(connector=lambda: MockConnection(cursor0))
        self.assertFalse(db_store_dup.consume(ATTEMPT, "backup", expires_at=2000, now=1000))

        # 3. Database connection/query error -> GateFailure
        def fail_conn():
            raise RuntimeError("DB pool exhausted")
        db_store_fail = DatabaseReplayStore(connector=fail_conn)
        with self.assertRaises(GateFailure) as ctx:
            db_store_fail.consume(ATTEMPT, "backup", expires_at=2000, now=1000)
        self.assertIn("database replay store error", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
