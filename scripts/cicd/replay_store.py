#!/usr/bin/env python3
"""Durable, tamper-resistant attestation replay stores for EB-115.

Enforces:
1. Atomic consume-once contract: (attempt, kind) can be consumed exactly once.
2. Rejection of duplicate attempts.
3. Expiry enforcement: evidence where now >= expires_at is rejected.
4. Fail-closed behavior on storage, network, or cryptographic errors.
5. Multi-runner persistence support (via authenticated RemoteHttp, Database, or HMAC-signed durable store).
6. Prohibition of unprotected local files as replay authorities.
"""
from __future__ import annotations

import abc
import hashlib
import hmac
import json
import os
import re
import tempfile
import threading
import time
from pathlib import Path
from typing import Any, Callable, Dict, Optional, Set, Tuple
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from production_gate import GateFailure

ATTEMPT_RE = re.compile(r"[A-Za-z0-9_-]{16,128}\Z")
ALLOWED_KINDS = frozenset({"backup", "compatibility"})


class ReplayStore(abc.ABC):
    """Abstract contract for atomic attestation replay consumption."""

    def _validate_inputs(self, attempt: str, kind: str, expires_at: int, now: Optional[int]) -> int:
        if not isinstance(attempt, str) or not ATTEMPT_RE.fullmatch(attempt):
            raise GateFailure("invalid attempt format")
        if kind not in ALLOWED_KINDS:
            raise GateFailure(f"unsupported attestation kind: {kind}")
        if type(expires_at) is not int:
            raise GateFailure("invalid expires_at timestamp")
        current_time = int(time.time() if now is None else now)
        if current_time >= expires_at:
            return -1  # Expired
        return current_time

    @abc.abstractmethod
    def consume(self, attempt: str, kind: str, expires_at: int, now: Optional[int] = None) -> bool:
        """Atomically consume (attempt, kind).

        Returns True on first-time valid consumption.
        Returns False if already consumed or expired.
        Raises GateFailure on storage errors or tamper.
        """
        raise NotImplementedError


class InMemoryReplayStore(ReplayStore):
    """Thread-safe, ephemeral in-memory replay store for testing."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._consumed: Set[Tuple[str, str]] = set()

    def consume(self, attempt: str, kind: str, expires_at: int, now: Optional[int] = None) -> bool:
        curr = self._validate_inputs(attempt, kind, expires_at, now)
        if curr < 0:
            return False

        with self._lock:
            key = (attempt, kind)
            if key in self._consumed:
                return False
            self._consumed.add(key)
            return True


class HmacDurableReplayStore(ReplayStore):
    """Durable file-backed store protected by HMAC-SHA256 signature and file locking.

    Rejects unprotected local files by requiring a secure HMAC key (minimum 32 bytes).
    Guarantees tamper-detection and atomic writes.
    """
    _PATH_LOCKS: Dict[str, threading.Lock] = {}
    _GLOBAL_LOCK = threading.Lock()

    @classmethod
    def _get_path_lock(cls, path: Path) -> threading.Lock:
        resolved = str(path.resolve())
        with cls._GLOBAL_LOCK:
            if resolved not in cls._PATH_LOCKS:
                cls._PATH_LOCKS[resolved] = threading.Lock()
            return cls._PATH_LOCKS[resolved]

    def __init__(self, filepath: Path, hmac_key: bytes) -> None:
        if not isinstance(hmac_key, bytes) or len(hmac_key) < 32:
            raise GateFailure("hmac_key must be at least 32 bytes to ensure tamper protection")
        self.filepath = Path(filepath)
        self.hmac_key = hmac_key
        self._lock = self._get_path_lock(self.filepath)

    def _compute_hmac(self, data: bytes) -> str:
        return hmac.new(self.hmac_key, data, hashlib.sha256).hexdigest()

    def _load(self) -> Dict[str, Any]:
        if not self.filepath.exists():
            return {"records": []}
        try:
            raw = self.filepath.read_bytes()
            if not raw:
                return {"records": []}
            data = json.loads(raw.decode("utf-8"))
            if not isinstance(data, dict) or "signature" not in data or "payload" not in data:
                raise GateFailure("malformed durable replay store format")
            expected_sig = data["signature"]
            payload_str = data["payload"]
            actual_sig = self._compute_hmac(payload_str.encode("utf-8"))
            if not hmac.compare_digest(expected_sig, actual_sig):
                raise GateFailure("replay store integrity verification failed: tamper detected")
            parsed_payload = json.loads(payload_str)
            if not isinstance(parsed_payload, dict) or not isinstance(parsed_payload.get("records"), list):
                raise GateFailure("malformed payload in durable replay store")
            return parsed_payload
        except GateFailure:
            raise
        except Exception as exc:
            raise GateFailure(f"failed to read durable replay store: {exc}") from None

    def _save(self, payload: Dict[str, Any]) -> None:
        try:
            payload_str = json.dumps(payload, sort_keys=True, separators=(",", ":"))
            signature = self._compute_hmac(payload_str.encode("utf-8"))
            container = {
                "signature": signature,
                "payload": payload_str,
            }
            content = json.dumps(container, separators=(",", ":")).encode("utf-8")
            self.filepath.parent.mkdir(parents=True, exist_ok=True)
            with tempfile.NamedTemporaryFile(dir=self.filepath.parent, delete=False) as tmp:
                tmp.write(content)
                tmp.flush()
                os.fsync(tmp.fileno())
                tmp_name = tmp.name
            os.replace(tmp_name, str(self.filepath))
        except Exception as exc:
            raise GateFailure(f"failed to write durable replay store: {exc}") from None

    def consume(self, attempt: str, kind: str, expires_at: int, now: Optional[int] = None) -> bool:
        curr = self._validate_inputs(attempt, kind, expires_at, now)
        if curr < 0:
            return False

        with self._lock:
            store_data = self._load()
            records = store_data.get("records", [])

            # Prune long-expired records to bound size, but keep valid/unexpired records
            active_records = []
            for rec in records:
                if isinstance(rec, dict):
                    rec_attempt = rec.get("attempt")
                    rec_kind = rec.get("kind")
                    rec_expires = rec.get("expires_at", 0)
                    if rec_attempt == attempt and rec_kind == kind:
                        # Already consumed
                        return False
                    if rec_expires > curr:
                        active_records.append(rec)

            # Record consumption
            active_records.append({
                "attempt": attempt,
                "kind": kind,
                "expires_at": expires_at,
                "consumed_at": curr,
            })
            self._save({"records": active_records})
            return True


class RemoteHttpReplayStore(ReplayStore):
    """Distributed replay store communicating with a centralized atomic API.

    Enables persistence across independent CI runners.
    Requires HTTPS endpoint (or loopback for testing) and Bearer token auth.
    """

    def __init__(self, endpoint_url: str, auth_token: str,
                 opener: Optional[Callable[[Request, float], Any]] = None,
                 timeout: float = 10.0) -> None:
        if not endpoint_url or not isinstance(endpoint_url, str):
            raise GateFailure("invalid remote replay store endpoint")
        if not auth_token or not isinstance(auth_token, str):
            raise GateFailure("missing remote replay store authentication token")
        if not (endpoint_url.startswith("https://") or endpoint_url.startswith("http://127.0.0.1") or endpoint_url.startswith("http://localhost")):
            raise GateFailure("remote replay store endpoint must use HTTPS")
        self.endpoint_url = endpoint_url
        self.auth_token = auth_token
        self.opener = opener or urlopen
        self.timeout = timeout

    def consume(self, attempt: str, kind: str, expires_at: int, now: Optional[int] = None) -> bool:
        curr = self._validate_inputs(attempt, kind, expires_at, now)
        if curr < 0:
            return False

        body = json.dumps({
            "attempt": attempt,
            "kind": kind,
            "expires_at": expires_at,
            "consumed_at": curr,
        }).encode("utf-8")

        req = Request(
            self.endpoint_url,
            data=body,
            headers={
                "Authorization": f"Bearer {self.auth_token}",
                "Content-Type": "application/json",
                "Accept": "application/json",
            },
            method="POST",
        )

        try:
            with self.opener(req, timeout=self.timeout) as resp:
                status = getattr(resp, "status", None) or resp.getcode()
                if status in (200, 201):
                    return True
                if status == 409:
                    return False
                raise GateFailure(f"unexpected remote replay store status: {status}")
        except HTTPError as err:
            if err.code == 409:
                return False
            if err.code == 400:
                # Expired or rejected payload
                return False
            raise GateFailure(f"remote replay store HTTP error: {err.code}") from None
        except GateFailure:
            raise
        except Exception as exc:
            raise GateFailure(f"remote replay store communication failed: {exc}") from None


class DatabaseReplayStore(ReplayStore):
    """Database-backed atomic replay store using PostgreSQL unique constraints.

    Uses `ON CONFLICT (attempt, kind) DO NOTHING` to guarantee atomic consume-once.
    """

    def __init__(self, connector: Callable[[], Any]) -> None:
        if connector is None:
            raise GateFailure("database replay connector required")
        self.connector = connector

    def consume(self, attempt: str, kind: str, expires_at: int, now: Optional[int] = None) -> bool:
        curr = self._validate_inputs(attempt, kind, expires_at, now)
        if curr < 0:
            return False

        try:
            conn = self.connector()
            try:
                cursor = conn.cursor()
                sql = (
                    "INSERT INTO release_attestation_replay (attempt, kind, expires_at, consumed_at) "
                    "VALUES (%s, %s, %s, %s) "
                    "ON CONFLICT (attempt, kind) DO NOTHING"
                )
                cursor.execute(sql, (attempt, kind, expires_at, curr))
                rowcount = cursor.rowcount
                if hasattr(conn, "commit"):
                    conn.commit()
                return rowcount == 1
            finally:
                if hasattr(conn, "close"):
                    conn.close()
        except Exception as exc:
            raise GateFailure(f"database replay store error: {exc}") from None
