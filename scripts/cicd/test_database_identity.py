#!/usr/bin/env python3
"""Security tests for Authenticated Database Identity (EB-115 Slice 1)."""
import unittest
from typing import Any, Dict, Optional

from database_identity import (
    DatabaseIdentityPolicy,
    DatabaseIdentityReport,
    DatabaseIdentityVerifier,
    compute_fingerprint,
)
from production_gate import GateFailure

PROD_REF = "p" * 20
STAGING_REF = "s" * 20
VALID_URL = f"jdbc:postgresql://db.{PROD_REF}.supabase.co:5432/postgres?sslmode=verify-full"
VALID_POOLER_URL = f"jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:5432/postgres?sslmode=verify-full"


class MockDatabaseSession:
    """Mock database session returning configured scalar results."""
    def __init__(self, database: str = "postgres", schema: str = "public",
                 user: str = "postgres", ssl: str = "on",
                 system_identifier: str = "7361234567890123456",
                 should_fail: bool = False, fail_query: Optional[str] = None):
        self.database = database
        self.schema = schema
        self.user = user
        self.ssl = ssl
        self.system_identifier = system_identifier
        self.should_fail = should_fail
        self.fail_query = fail_query
        self.closed = False

    def execute_scalar(self, query: str) -> Any:
        if self.should_fail:
            raise RuntimeError("simulated database query failure")
        if self.fail_query and self.fail_query in query:
            raise RuntimeError(f"simulated query failure for: {query}")

        if "current_database()" in query:
            return self.database
        if "current_schema()" in query:
            return self.schema
        if "current_user" in query:
            return self.user
        if "SHOW ssl" in query:
            return self.ssl
        if "system_identifier" in query:
            return self.system_identifier
        raise ValueError(f"unexpected mock query: {query}")

    def close(self) -> None:
        self.closed = True


class MockConnector:
    """Mock connector factory for dependency injection."""
    def __init__(self, session: Optional[MockDatabaseSession] = None, fail_connect: bool = False):
        self.session = session or MockDatabaseSession()
        self.fail_connect = fail_connect
        self.connected_args = []

    def __call__(self, url: str, user: str, password: Optional[str] = None, timeout: int = 15):
        if self.fail_connect:
            raise ConnectionError("simulated connection timeout or failure")
        self.connected_args.append((url, user, timeout))
        return self.session


class DatabaseIdentitySecurityTests(unittest.TestCase):
    def setUp(self):
        self.policy = DatabaseIdentityPolicy(
            approved_project_ref=PROD_REF,
            staging_project_ref=STAGING_REF,
            expected_database="postgres",
            expected_schema="public",
            expected_user="postgres",
            expected_system_identifier="7361234567890123456",
            required_sslmode="verify-full",
            allowed_port=5432,
        )

    def test_happy_path_direct_and_pooler(self):
        # 1. Direct host
        mock_conn = MockConnector()
        verifier = DatabaseIdentityVerifier(self.policy, connector=mock_conn)
        report = verifier.verify(VALID_URL, "postgres", now=1000)

        self.assertEqual(report.database, "postgres")
        self.assertEqual(report.schema, "public")
        self.assertEqual(report.user, "postgres")
        self.assertTrue(report.ssl_active)
        self.assertEqual(report.system_identifier, "7361234567890123456")
        self.assertTrue(mock_conn.session.closed)

        # 2. Pooler host with postgres.<ref>
        mock_conn_pooler = MockConnector(session=MockDatabaseSession(user=f"postgres.{PROD_REF}"))
        verifier_pooler = DatabaseIdentityVerifier(self.policy, connector=mock_conn_pooler)
        report_pooler = verifier_pooler.verify(VALID_POOLER_URL, f"postgres.{PROD_REF}", now=1000)
        self.assertEqual(report_pooler.user, f"postgres.{PROD_REF}")

    def test_wrong_db_identity(self):
        # A. Wrong database name
        verifier = DatabaseIdentityVerifier(self.policy, connector=MockConnector(
            session=MockDatabaseSession(database="wrong_database")))
        with self.assertRaises(GateFailure) as ctx:
            verifier.verify(VALID_URL, "postgres")
        self.assertIn("does not match expected 'postgres'", str(ctx.exception))

        # B. Wrong schema
        verifier = DatabaseIdentityVerifier(self.policy, connector=MockConnector(
            session=MockDatabaseSession(schema="private_schema")))
        with self.assertRaises(GateFailure) as ctx:
            verifier.verify(VALID_URL, "postgres")
        self.assertIn("connected schema 'private_schema' does not match", str(ctx.exception))

        # C. Wrong user
        verifier = DatabaseIdentityVerifier(self.policy, connector=MockConnector(
            session=MockDatabaseSession(user="attacker")))
        with self.assertRaises(GateFailure) as ctx:
            verifier.verify(VALID_URL, "postgres")
        self.assertIn("connected user 'attacker' does not match", str(ctx.exception))

        # D. Wrong system identifier (cluster identity mismatch)
        verifier = DatabaseIdentityVerifier(self.policy, connector=MockConnector(
            session=MockDatabaseSession(system_identifier="9999999999999999999")))
        with self.assertRaises(GateFailure) as ctx:
            verifier.verify(VALID_URL, "postgres")
        self.assertIn("does not match expected cluster identity", str(ctx.exception))

        # E. URL targeting staging project ref instead of production
        staging_url = f"jdbc:postgresql://db.{STAGING_REF}.supabase.co:5432/postgres?sslmode=verify-full"
        verifier_valid = DatabaseIdentityVerifier(self.policy, connector=MockConnector())
        with self.assertRaises(GateFailure) as ctx:
            verifier_valid.verify(staging_url, "postgres")
        self.assertIn("staging project", str(ctx.exception))

    def test_invalid_tls_configuration(self):
        verifier = DatabaseIdentityVerifier(self.policy, connector=MockConnector())

        # A. sslmode=require instead of verify-full
        insecure_url = VALID_URL.replace("verify-full", "require")
        with self.assertRaises(GateFailure) as ctx:
            verifier.verify(insecure_url, "postgres")
        self.assertIn("invalid or unauthorized query parameters", str(ctx.exception))

        # B. sslmode=disable
        disabled_url = VALID_URL.replace("verify-full", "disable")
        with self.assertRaises(GateFailure):
            verifier.verify(disabled_url, "postgres")

        # C. Connected session reports SSL is off
        verifier_ssl_off = DatabaseIdentityVerifier(self.policy, connector=MockConnector(
            session=MockDatabaseSession(ssl="off")))
        with self.assertRaises(GateFailure) as ctx:
            verifier_ssl_off.verify(VALID_URL, "postgres")
        self.assertIn("not using SSL/TLS", str(ctx.exception))

    def test_unauthorized_jdbc_overrides(self):
        verifier = DatabaseIdentityVerifier(self.policy, connector=MockConnector())

        injected_urls = [
            VALID_URL + "&user=attacker",
            VALID_URL + "&options=-c%20search_path=evil",
            VALID_URL + "&targetServerType=primary",
            VALID_URL + "&sslfactory=org.postgresql.ssl.NonValidatingFactory",
            VALID_URL.replace("//db.", "//user:password@db."),
            VALID_URL.replace(":5432", ":6543"),  # Non-standard unapproved port
            VALID_URL + "#fragment",
            "postgres://db." + PROD_REF + ".supabase.co:5432/postgres",  # Non-JDBC
            VALID_URL.replace(PROD_REF, "unknown_ref_12345678"),
        ]

        for bad_url in injected_urls:
            with self.subTest(bad_url=bad_url):
                with self.assertRaises(GateFailure):
                    verifier.verify(bad_url, "postgres")

    def test_missing_connected_identity(self):
        # A. Missing connector
        verifier_no_conn = DatabaseIdentityVerifier(self.policy, connector=None)
        with self.assertRaises(GateFailure) as ctx:
            verifier_no_conn.verify(VALID_URL, "postgres")
        self.assertIn("no connected database authority", str(ctx.exception))

        # B. Connection error / timeout
        verifier_fail = DatabaseIdentityVerifier(self.policy, connector=MockConnector(fail_connect=True))
        with self.assertRaises(GateFailure) as ctx:
            verifier_fail.verify(VALID_URL, "postgres")
        self.assertIn("database connection failed", str(ctx.exception))

        # C. Query failure on system_identifier
        verifier_query_fail = DatabaseIdentityVerifier(self.policy, connector=MockConnector(
            session=MockDatabaseSession(fail_query="system_identifier")))
        with self.assertRaises(GateFailure) as ctx:
            verifier_query_fail.verify(VALID_URL, "postgres")
        self.assertIn("failed to read database runtime identity", str(ctx.exception))

        # D. Empty / None system_identifier returned
        verifier_empty_sys = DatabaseIdentityVerifier(self.policy, connector=MockConnector(
            session=MockDatabaseSession(system_identifier="")))
        with self.assertRaises(GateFailure) as ctx:
            verifier_empty_sys.verify(VALID_URL, "postgres")
        self.assertIn("unable to obtain PostgreSQL system_identifier", str(ctx.exception))

    def test_changed_db_identity_and_recheck(self):
        # Policy with dynamic cluster identity (no fixed expected_system_identifier)
        dynamic_policy = DatabaseIdentityPolicy(
            approved_project_ref=PROD_REF,
            staging_project_ref=STAGING_REF,
            expected_database="postgres",
            expected_schema="public",
            expected_user="postgres",
            expected_system_identifier=None,
        )
        session = MockDatabaseSession()
        mock_conn = MockConnector(session=session)
        verifier = DatabaseIdentityVerifier(dynamic_policy, connector=mock_conn)

        initial_report = verifier.verify(VALID_URL, "postgres", now=1000)
        self.assertTrue(initial_report.fingerprint)

        # 1. Recheck when identity is unchanged -> PASS
        self.assertTrue(verifier.recheck(VALID_URL, "postgres", initial_report, now=1010))

        # 2. Database cluster swapped during deployment (new system_identifier detected by fingerprint)
        session.system_identifier = "8888888888888888888"
        with self.assertRaises(GateFailure) as ctx:
            verifier.recheck(VALID_URL, "postgres", initial_report, now=1020)
        self.assertIn("database identity changed between preflight and migration", str(ctx.exception))

        # 3. Database changed (e.g. database swapped from postgres to test)
        session.system_identifier = "7361234567890123456"  # restore
        session.database = "unexpected_db"
        with self.assertRaises(GateFailure) as ctx:
            verifier.recheck(VALID_URL, "postgres", initial_report, now=1030)
        self.assertIn("connected database", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
