#!/usr/bin/env python3
"""Security tests for Serving Image Observer (EB-115 Slice 2B)."""
import io
import json
import unittest
from types import SimpleNamespace

from production_gate import GateFailure
from serving_observer import (
    RailwayServingImageObserver,
    ServingImageObservation,
    extract_digest,
)

PROJECT_ID = "railway_proj_123"
ENV_ID = "railway_env_prod"
SERVICE_ID = "railway_srv_backend"
TOKEN = "secret_railway_token"
TARGET = "edunest-production"
GOOD_DIGEST = "sha256:" + "d" * 64
OTHER_DIGEST = "sha256:" + "e" * 64


class ServingObserverSecurityTests(unittest.TestCase):
    def _mock_project_token_opener(self, project_id=PROJECT_ID, environment_id=ENV_ID,
                                   has_error=False, status=200):
        class MockResponse(io.BytesIO):
            def __init__(self, code, body):
                super().__init__(body)
                self.status = code

        def opener(request, timeout):
            if status != 200:
                raise RuntimeError(f"HTTP {status}")
            if has_error:
                return MockResponse(200, b'{"errors":[{"message":"Not Authorized"}]}')
            body = json.dumps({
                "data": {
                    "projectToken": {
                        "projectId": project_id,
                        "environmentId": environment_id,
                    }
                }
            }).encode("utf-8")
            return MockResponse(200, body)

        return opener

    def test_happy_path_observation_and_verification(self):
        now = 1000
        deployments = [
            {
                "id": "dep_001",
                "status": "SUCCESS",
                "createdAt": now - 10,
                "meta": {
                    "image": f"ghcr.io/mooraa86/edunest-production-preparation@{GOOD_DIGEST}",
                }
            }
        ]

        observer = RailwayServingImageObserver(
            project_id=PROJECT_ID,
            environment_id=ENV_ID,
            service_id=SERVICE_ID,
            token=TOKEN,
            opener=self._mock_project_token_opener(),
            deployment_lister=lambda s, e, env: deployments,
            max_age_seconds=60,
        )

        obs = observer.observe(TARGET, now=now)
        self.assertEqual(obs.deployment_id, "dep_001")
        self.assertEqual(obs.image_digest, GOOD_DIGEST)
        self.assertEqual(obs.target, TARGET)
        self.assertTrue(obs.authenticated)

        # Attestation verify interface
        self.assertTrue(observer.verify(TARGET, GOOD_DIGEST, now=now))

    def test_wrong_image_digest(self):
        now = 1000
        deployments = [
            {
                "id": "dep_001",
                "status": "SUCCESS",
                "createdAt": now - 10,
                "meta": {
                    "image": f"ghcr.io/mooraa86/edunest-production-preparation@{OTHER_DIGEST}",
                }
            }
        ]

        observer = RailwayServingImageObserver(
            project_id=PROJECT_ID,
            environment_id=ENV_ID,
            service_id=SERVICE_ID,
            token=TOKEN,
            opener=self._mock_project_token_opener(),
            deployment_lister=lambda s, e, env: deployments,
        )

        # Expected GOOD_DIGEST but serving OTHER_DIGEST -> verify returns False
        self.assertFalse(observer.verify(TARGET, GOOD_DIGEST, now=now))

        # Rejection of mutable tags (e.g. :latest or :main)
        with self.assertRaises(GateFailure) as ctx:
            extract_digest("ghcr.io/mooraa86/edunest-backend:latest")
        self.assertIn("not immutable digest-pinned", str(ctx.exception))

    def test_stale_observation(self):
        now = 1000
        # Observation is 61 seconds old (exceeds max_age_seconds=60)
        deployments_stale = [
            {
                "id": "dep_old",
                "status": "SUCCESS",
                "createdAt": now - 61,
                "meta": {"image": GOOD_DIGEST},
            }
        ]

        observer_stale = RailwayServingImageObserver(
            project_id=PROJECT_ID,
            environment_id=ENV_ID,
            service_id=SERVICE_ID,
            token=TOKEN,
            opener=self._mock_project_token_opener(),
            deployment_lister=lambda s, e, env: deployments_stale,
            max_age_seconds=60,
        )

        with self.assertRaises(GateFailure) as ctx:
            observer_stale.observe(TARGET, now=now)
        self.assertIn("stale deployment observation", str(ctx.exception))
        self.assertFalse(observer_stale.verify(TARGET, GOOD_DIGEST, now=now))

        # Observation in the future (clock skew / spoofing)
        deployments_future = [
            {
                "id": "dep_future",
                "status": "SUCCESS",
                "createdAt": now + 10,
                "meta": {"image": GOOD_DIGEST},
            }
        ]
        observer_future = RailwayServingImageObserver(
            project_id=PROJECT_ID,
            environment_id=ENV_ID,
            service_id=SERVICE_ID,
            token=TOKEN,
            opener=self._mock_project_token_opener(),
            deployment_lister=lambda s, e, env: deployments_future,
            max_age_seconds=60,
        )
        with self.assertRaises(GateFailure) as ctx:
            observer_future.observe(TARGET, now=now)
        self.assertIn("stale deployment observation", str(ctx.exception))

    def test_unauthorized_railway_provider_response(self):
        # A. Wrong project ID in token response
        observer_wrong_proj = RailwayServingImageObserver(
            project_id=PROJECT_ID,
            environment_id=ENV_ID,
            service_id=SERVICE_ID,
            token=TOKEN,
            opener=self._mock_project_token_opener(project_id="other_project"),
            deployment_lister=lambda s, e, env: [],
        )
        with self.assertRaises(GateFailure) as ctx:
            observer_wrong_proj.observe(TARGET, now=1000)
        self.assertIn("Railway project or environment mismatch", str(ctx.exception))

        # B. Wrong environment ID in token response
        observer_wrong_env = RailwayServingImageObserver(
            project_id=PROJECT_ID,
            environment_id=ENV_ID,
            service_id=SERVICE_ID,
            token=TOKEN,
            opener=self._mock_project_token_opener(environment_id="other_env"),
            deployment_lister=lambda s, e, env: [],
        )
        with self.assertRaises(GateFailure) as ctx:
            observer_wrong_env.observe(TARGET, now=1000)
        self.assertIn("Railway project or environment mismatch", str(ctx.exception))

        # C. GraphQL error response
        observer_gql_err = RailwayServingImageObserver(
            project_id=PROJECT_ID,
            environment_id=ENV_ID,
            service_id=SERVICE_ID,
            token=TOKEN,
            opener=self._mock_project_token_opener(has_error=True),
            deployment_lister=lambda s, e, env: [],
        )
        with self.assertRaises(GateFailure) as ctx:
            observer_gql_err.observe(TARGET, now=1000)
        self.assertIn("Railway identity lookup failed", str(ctx.exception))

        # D. HTTP error / failure
        observer_http_err = RailwayServingImageObserver(
            project_id=PROJECT_ID,
            environment_id=ENV_ID,
            service_id=SERVICE_ID,
            token=TOKEN,
            opener=self._mock_project_token_opener(status=401),
            deployment_lister=lambda s, e, env: [],
        )
        with self.assertRaises(GateFailure):
            observer_http_err.observe(TARGET, now=1000)

    def test_provider_timeouts_and_ambiguous_results(self):
        # A. Deployments listing timeout / failure
        def fail_lister(s, e, env):
            raise TimeoutError("Railway CLI timeout after 30s")

        observer_timeout = RailwayServingImageObserver(
            project_id=PROJECT_ID,
            environment_id=ENV_ID,
            service_id=SERVICE_ID,
            token=TOKEN,
            opener=self._mock_project_token_opener(),
            deployment_lister=fail_lister,
        )
        with self.assertRaises(GateFailure):
            observer_timeout.observe(TARGET, now=1000)

        # B. Empty deployments returned
        observer_empty = RailwayServingImageObserver(
            project_id=PROJECT_ID,
            environment_id=ENV_ID,
            service_id=SERVICE_ID,
            token=TOKEN,
            opener=self._mock_project_token_opener(),
            deployment_lister=lambda s, e, env: [],
        )
        with self.assertRaises(GateFailure) as ctx:
            observer_empty.observe(TARGET, now=1000)
        self.assertIn("no deployments returned", str(ctx.exception))

        # C. No SUCCESS deployment (e.g. CRASHED or BUILDING only)
        observer_no_success = RailwayServingImageObserver(
            project_id=PROJECT_ID,
            environment_id=ENV_ID,
            service_id=SERVICE_ID,
            token=TOKEN,
            opener=self._mock_project_token_opener(),
            deployment_lister=lambda s, e, env: [{"id": "dep_1", "status": "CRASHED"}],
        )
        with self.assertRaises(GateFailure) as ctx:
            observer_no_success.observe(TARGET, now=1000)
        self.assertIn("no SUCCESS deployment", str(ctx.exception))

        # D. Malformed deployment metadata (missing image info)
        observer_bad_meta = RailwayServingImageObserver(
            project_id=PROJECT_ID,
            environment_id=ENV_ID,
            service_id=SERVICE_ID,
            token=TOKEN,
            opener=self._mock_project_token_opener(),
            deployment_lister=lambda s, e, env: [{"id": "dep_1", "status": "SUCCESS", "meta": {}}],
        )
        with self.assertRaises(GateFailure) as ctx:
            observer_bad_meta.observe(TARGET, now=1000)
        self.assertIn("missing image metadata", str(ctx.exception))

    def test_serving_identity_recheck_before_migration(self):
        now = 1000
        deployments = [
            {
                "id": "dep_001",
                "status": "SUCCESS",
                "createdAt": now - 5,
                "meta": {"image": GOOD_DIGEST},
            }
        ]

        observer = RailwayServingImageObserver(
            project_id=PROJECT_ID,
            environment_id=ENV_ID,
            service_id=SERVICE_ID,
            token=TOKEN,
            opener=self._mock_project_token_opener(),
            deployment_lister=lambda s, e, env: deployments,
        )

        prior_obs = observer.observe(TARGET, now=now)

        # 1. Recheck when unchanged -> PASS
        self.assertTrue(observer.recheck(TARGET, prior_obs, now=now + 5))

        # 2. Deployment ID changed between observation and migration
        deployments[0] = {
            "id": "dep_002",
            "status": "SUCCESS",
            "createdAt": now,
            "meta": {"image": GOOD_DIGEST},
        }
        with self.assertRaises(GateFailure) as ctx:
            observer.recheck(TARGET, prior_obs, now=now + 5)
        self.assertIn("serving deployment changed", str(ctx.exception))

        # 3. Image digest changed (re-deployed different image)
        deployments[0] = {
            "id": "dep_001",
            "status": "SUCCESS",
            "createdAt": now,
            "meta": {"image": OTHER_DIGEST},
        }
        with self.assertRaises(GateFailure) as ctx:
            observer.recheck(TARGET, prior_obs, now=now + 5)
        self.assertIn("serving image digest changed", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
