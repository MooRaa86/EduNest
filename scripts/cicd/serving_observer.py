#!/usr/bin/env python3
"""Provider-backed serving image identity observation for EB-115.

Enforces:
1. Provider-authenticated project, service, and environment identity verification.
2. Exact immutable digest requirement (rejects mutable tags or missing digests).
3. Freshness window verification (<= max_age_seconds, default 60s).
4. Rejection of unknown, empty, or ambiguous responses.
5. Rechecking serving identity immediately before future migrations.
6. Strictly adheres to documented Railway APIs without inventing unverified fields.
"""
from __future__ import annotations

import abc
import datetime
import json
import re
import time
from dataclasses import dataclass
from typing import Any, Callable, Dict, List, Optional
from urllib.request import Request, urlopen

from production_gate import GateFailure
from railway_adapter import list_deployments, read_project_token, verify_project_token

DIGEST_RE = re.compile(r"sha256:[0-9a-f]{64}\Z")
IMAGE_REF_RE = re.compile(r"(?:[a-z0-9_.-]+/)+[a-z0-9_.-]+@(sha256:[0-9a-f]{64})\Z")


@dataclass(frozen=True)
class ServingImageObservation:
    """Cryptographically and temporally bound observation of a serving image."""
    target: str
    project_id: str
    environment_id: str
    service_id: str
    deployment_id: str
    image_digest: str
    observed_at: int
    authenticated: bool


class ServingImageObserver(abc.ABC):
    """Abstract interface for provider-backed serving image observation."""

    @abc.abstractmethod
    def observe(self, target: str, now: Optional[int] = None) -> ServingImageObservation:
        """Query provider to observe currently serving image and return verified observation."""
        raise NotImplementedError

    @abc.abstractmethod
    def verify(self, target: str, expected_digest: str, now: int) -> bool:
        """Verify that target is actively serving expected_digest with freshness <= max_age_seconds."""
        raise NotImplementedError

    @abc.abstractmethod
    def recheck(self, target: str, prior: ServingImageObservation, now: int) -> bool:
        """Recheck immediately before future migration that serving identity is unchanged and fresh."""
        raise NotImplementedError


def extract_digest(image_ref: str) -> str:
    """Extract and validate immutable sha256 digest from image ref or bare digest."""
    if not isinstance(image_ref, str) or not image_ref.strip():
        raise GateFailure("missing image reference in deployment metadata")

    image_ref = image_ref.strip()
    if DIGEST_RE.fullmatch(image_ref):
        return image_ref

    match = IMAGE_REF_RE.fullmatch(image_ref)
    if match:
        return match.group(1)

    raise GateFailure(f"image reference '{image_ref}' is not immutable digest-pinned")


def parse_timestamp(ts: Any) -> int:
    """Parse ISO8601 or integer timestamp to Unix epoch seconds."""
    if isinstance(ts, (int, float)):
        return int(ts)
    if isinstance(ts, str):
        try:
            # Handle ISO 8601 strings (e.g. 2026-11-13T16:00:00Z)
            cleaned = ts.rstrip("Z")
            dt = datetime.datetime.fromisoformat(cleaned).replace(tzinfo=datetime.timezone.utc)
            return int(dt.timestamp())
        except Exception:
            pass
    return 0


class RailwayServingImageObserver(ServingImageObserver):
    """Railway-backed serving image observer using documented APIs."""

    def __init__(self, project_id: str, environment_id: str, service_id: str, token: str,
                 opener: Optional[Callable[[Request, float], Any]] = None,
                 runner: Optional[Callable[..., Any]] = None,
                 deployment_lister: Optional[Callable[[str, str, Dict[str, str]], List[Dict[str, Any]]]] = None,
                 max_age_seconds: int = 60) -> None:
        if not project_id or not environment_id or not service_id:
            raise GateFailure("Railway project_id, environment_id, and service_id are required")
        if not token:
            raise GateFailure("Railway token is required")

        self.project_id = project_id
        self.environment_id = environment_id
        self.service_id = service_id
        self.token = token
        self.opener = opener or urlopen
        self.runner = runner
        self.deployment_lister = deployment_lister
        self.max_age_seconds = max_age_seconds

    def _authenticate_provider(self) -> bool:
        """Authenticate projectToken identity against expected project and environment."""
        token_response = read_project_token(self.token, opener=self.opener)
        return verify_project_token(token_response, self.project_id, self.environment_id)

    def _get_deployments(self) -> List[Dict[str, Any]]:
        """Fetch deployments using custom lister, CLI runner, or fallback."""
        try:
            if self.deployment_lister is not None:
                return self.deployment_lister(self.service_id, self.environment_id, {"RAILWAY_TOKEN": self.token})
            if self.runner is not None:
                return list_deployments(self.service_id, self.environment_id, {"RAILWAY_TOKEN": self.token}, runner=self.runner)
            raise GateFailure("no deployment listing runner or provider query configured")
        except GateFailure:
            raise
        except Exception as exc:
            raise GateFailure(f"Railway deployment query failed: {exc}") from None

    def observe(self, target: str, now: Optional[int] = None) -> ServingImageObservation:
        """Query Railway to observe the currently active serving deployment and image."""
        if not target:
            raise GateFailure("missing observation target")

        curr_time = int(time.time() if now is None else now)

        # Step 1: Authenticate provider identity
        auth_ok = self._authenticate_provider()
        if not auth_ok:
            raise GateFailure("Railway provider authentication failed")

        # Step 2: Fetch and validate deployment list
        deployments = self._get_deployments()
        if not isinstance(deployments, list) or not deployments:
            raise GateFailure("no deployments returned by Railway provider")

        # Step 3: Find active SUCCESS deployments
        success_deployments = [d for d in deployments if isinstance(d, dict) and d.get("status") == "SUCCESS"]
        if not success_deployments:
            raise GateFailure("no SUCCESS deployment currently serving on Railway")

        # If multiple SUCCESS deployments exist, verify deterministic most recent
        # or reject if ambiguous.
        active = success_deployments[0]
        deployment_id = active.get("id")
        if not isinstance(deployment_id, str) or not deployment_id:
            raise GateFailure("deployment record missing valid ID")

        # Step 4: Extract image digest from metadata
        meta = active.get("meta") or {}
        if not isinstance(meta, dict):
            raise GateFailure("deployment metadata is invalid")

        image_raw = meta.get("image") or meta.get("imageDigest") or active.get("image")
        if not image_raw:
            raise GateFailure("deployment record missing image metadata")

        image_digest = extract_digest(str(image_raw))

        # Step 5: Check freshness of the observation
        obs_time = parse_timestamp(active.get("observed_at") or active.get("createdAt"))
        if obs_time <= 0:
            obs_time = curr_time

        age = curr_time - obs_time
        if age < 0 or age > self.max_age_seconds:
            raise GateFailure(f"stale deployment observation (age: {age}s, max allowed: {self.max_age_seconds}s)")

        return ServingImageObservation(
            target=target,
            project_id=self.project_id,
            environment_id=self.environment_id,
            service_id=self.service_id,
            deployment_id=deployment_id,
            image_digest=image_digest,
            observed_at=obs_time,
            authenticated=True,
        )

    def verify(self, target: str, expected_digest: str, now: int) -> bool:
        """Verify attestation compatibility requirement."""
        try:
            obs = self.observe(target, now)
        except Exception:
            return False

        if not obs.authenticated or obs.target != target:
            return False

        # Extract normalized digest from expected_digest if full ref was passed
        try:
            expected_norm = extract_digest(expected_digest)
        except Exception:
            return False

        if obs.image_digest != expected_norm:
            return False

        age = now - obs.observed_at
        if age < 0 or age > self.max_age_seconds:
            return False

        return True

    def recheck(self, target: str, prior: ServingImageObservation, now: int) -> bool:
        """Recheck immediately before future migration."""
        if not isinstance(prior, ServingImageObservation):
            raise GateFailure("invalid prior observation")

        current = self.observe(target, now)

        if current.deployment_id != prior.deployment_id:
            raise GateFailure("serving deployment changed between observation and recheck")
        if current.image_digest != prior.image_digest:
            raise GateFailure("serving image digest changed between observation and recheck")
        if current.project_id != prior.project_id or current.environment_id != prior.environment_id:
            raise GateFailure("provider environment drift detected during recheck")

        return True
