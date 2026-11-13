#!/usr/bin/env python3
"""Authenticated PostgreSQL database identity verification for EB-115.

Enforces:
1. Syntactic PostgreSQL JDBC URL verification with sslmode=verify-full.
2. Independently protected policy for approved and staging project refs.
3. Connected runtime identity verification (database, schema, user, SSL status,
   and system_identifier from pg_control_system).
4. Detection of changed identity or configuration.
5. Fail-closed error handling on connection failure, timeout, or ambiguity.
6. Rechecking identity immediately before future migrations.

Does not connect to live production databases during tests; uses dependency
injection for connections and system queries.
"""
from __future__ import annotations

import hashlib
import os
import re
import sys
from dataclasses import dataclass
from typing import Any, Callable, Dict, Mapping, Optional, Protocol, Tuple
from urllib.parse import parse_qsl, urlsplit

from production_gate import GateFailure

REF = re.compile(r"[a-z0-9]{20}\Z")
POOLER = re.compile(r"[a-z0-9-]+\.pooler\.supabase\.com\Z")
HEX64 = re.compile(r"[0-9a-f]{64}\Z")


class DatabaseSession(Protocol):
    """Minimal database session contract for runtime identity verification."""
    def execute_scalar(self, query: str) -> Any:
        ...

    def close(self) -> None:
        ...


@dataclass(frozen=True)
class DatabaseIdentityPolicy:
    """Independently protected configuration policy.
    
    Must never be derived from the connection string being checked.
    """
    approved_project_ref: str
    staging_project_ref: str
    expected_database: str = "postgres"
    expected_schema: str = "public"
    expected_user: str = "postgres"
    expected_system_identifier: Optional[str] = None
    required_sslmode: str = "verify-full"
    allowed_port: int = 5432
    max_timeout_seconds: int = 15

    def __post_init__(self) -> None:
        if not REF.fullmatch(self.approved_project_ref or ""):
            raise GateFailure("invalid approved project ref policy")
        if not REF.fullmatch(self.staging_project_ref or ""):
            raise GateFailure("invalid staging project ref policy")
        if self.approved_project_ref == self.staging_project_ref:
            raise GateFailure("approved and staging project refs cannot be identical")
        if not self.expected_database or not self.expected_schema or not self.expected_user:
            raise GateFailure("invalid expected database, schema, or user policy")
        if self.required_sslmode != "verify-full":
            raise GateFailure("only sslmode=verify-full is permitted")

    @classmethod
    def from_environment(cls, env: Optional[Mapping[str, str]] = None) -> DatabaseIdentityPolicy:
        source = os.environ if env is None else env
        return cls(
            approved_project_ref=source.get("PROD_DB_PROJECT_REF", ""),
            staging_project_ref=source.get("STAGING_DB_PROJECT_REF", ""),
            expected_database=source.get("PROD_DB_NAME", "postgres"),
            expected_schema=source.get("PROD_DB_SCHEMA", "public"),
            expected_user=source.get("PROD_DB_USER", "postgres"),
            expected_system_identifier=source.get("PROD_DB_SYSTEM_IDENTIFIER") or None,
            required_sslmode=source.get("PROD_DB_SSLMODE", "verify-full"),
            allowed_port=int(source.get("PROD_DB_PORT", "5432")),
        )


@dataclass(frozen=True)
class DatabaseIdentityReport:
    """Cryptographically fingerprinted identity report of a verified database."""
    host: str
    port: int
    database: str
    schema: str
    user: str
    system_identifier: str
    ssl_active: bool
    fingerprint: str
    verified_at: int


def compute_fingerprint(host: str, port: int, database: str, schema: str, user: str,
                        system_identifier: str) -> str:
    canonical = f"{host.lower()}:{port}:{database}:{schema}:{user}:{system_identifier}"
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


class DatabaseIdentityVerifier:
    """Verifies syntactic and connected runtime identity of the database."""

    def __init__(self, policy: DatabaseIdentityPolicy,
                 connector: Optional[Callable[[str, str, Optional[str], int], DatabaseSession]] = None) -> None:
        self.policy = policy
        self.connector = connector

    def validate_jdbc_url(self, url: str, user: str) -> Dict[str, Any]:
        """Perform strict fail-closed syntactic and authorization validation."""
        if not isinstance(url, str) or not url.startswith("jdbc:postgresql://"):
            raise GateFailure("invalid JDBC URL scheme")
        if not isinstance(user, str) or not user.strip():
            raise GateFailure("missing or empty database user")

        try:
            parsed = urlsplit(url[len("jdbc:"):])
        except Exception as exc:
            raise GateFailure(f"malformed JDBC URL: {exc}") from None

        if (parsed.scheme != "postgresql" or not parsed.hostname or parsed.username is not None
                or parsed.password is not None or parsed.fragment or parsed.port != self.policy.allowed_port
                or parsed.path != f"/{self.policy.expected_database}" or "," in parsed.netloc):
            raise GateFailure("unauthorized or ambiguous JDBC connection parameters")

        try:
            params = parse_qsl(parsed.query, keep_blank_values=True, strict_parsing=True)
        except Exception as exc:
            raise GateFailure(f"malformed JDBC query parameters: {exc}") from None

        # Disallow conflicting or injected parameters. Only approved TLS parameters allowed.
        allowed_params = [("sslmode", self.policy.required_sslmode)]
        if params != allowed_params:
            raise GateFailure("invalid or unauthorized query parameters in JDBC URL")

        host = parsed.hostname.lower()
        approved = self.policy.approved_project_ref
        staging = self.policy.staging_project_ref

        # Reject any connection targeting the staging database
        if staging in host or (f"postgres.{staging}" == user):
            raise GateFailure("connection targets staging project instead of approved production")

        direct = (host == f"db.{approved}.supabase.co") and (user == self.policy.expected_user)
        pooler = bool(POOLER.fullmatch(host)) and (user == f"postgres.{approved}")

        if not (direct or pooler):
            raise GateFailure("unauthorized database target or mismatched project ref")

        return {
            "host": host,
            "port": parsed.port,
            "database": self.policy.expected_database,
            "user": user,
            "is_pooler": pooler,
        }

    def verify(self, url: str, user: str, password: Optional[str] = None,
               now: Optional[int] = None) -> DatabaseIdentityReport:
        """Connect to database and verify runtime identity against policy."""
        url_info = self.validate_jdbc_url(url, user)

        if self.connector is None:
            raise GateFailure("no connected database authority or connector configured")

        try:
            session = self.connector(url, user, password, self.policy.max_timeout_seconds)
        except Exception as exc:
            raise GateFailure(f"database connection failed: {exc}") from None

        try:
            # Query runtime identity attributes
            db = session.execute_scalar("SELECT current_database()")
            schema = session.execute_scalar("SELECT current_schema()")
            curr_user = session.execute_scalar("SELECT current_user")
            ssl_raw = session.execute_scalar("SHOW ssl")
            ssl_active = str(ssl_raw).strip().lower() in ("on", "true", "1")

            # System identifier: 64-bit uint generated by initdb
            sys_id_raw = session.execute_scalar("SELECT system_identifier FROM pg_control_system()")
            sys_id = str(sys_id_raw).strip() if sys_id_raw is not None else ""
        except Exception as exc:
            raise GateFailure(f"failed to read database runtime identity: {exc}") from None
        finally:
            try:
                session.close()
            except Exception:
                pass

        # Validate runtime attributes against policy
        if db != self.policy.expected_database:
            raise GateFailure(f"connected database '{db}' does not match expected '{self.policy.expected_database}'")
        if schema != self.policy.expected_schema:
            raise GateFailure(f"connected schema '{schema}' does not match expected '{self.policy.expected_schema}'")
        if curr_user != user:
            raise GateFailure(f"connected user '{curr_user}' does not match expected '{user}'")
        if not ssl_active:
            raise GateFailure("connected database session is not using SSL/TLS")
        if not sys_id:
            raise GateFailure("unable to obtain PostgreSQL system_identifier from pg_control_system")
        if self.policy.expected_system_identifier and sys_id != self.policy.expected_system_identifier:
            raise GateFailure("connected system_identifier does not match expected cluster identity")

        fingerprint = compute_fingerprint(
            host=url_info["host"],
            port=url_info["port"],
            database=db,
            schema=schema,
            user=curr_user,
            system_identifier=sys_id,
        )

        verified_time = int(now if now is not None else 0)
        return DatabaseIdentityReport(
            host=url_info["host"],
            port=url_info["port"],
            database=db,
            schema=schema,
            user=curr_user,
            system_identifier=sys_id,
            ssl_active=ssl_active,
            fingerprint=fingerprint,
            verified_at=verified_time,
        )

    def recheck(self, url: str, user: str, prior_report: DatabaseIdentityReport,
                password: Optional[str] = None, now: Optional[int] = None) -> bool:
        """Recheck connected identity immediately before migration execution."""
        if not isinstance(prior_report, DatabaseIdentityReport):
            raise GateFailure("invalid prior database identity report")

        current_report = self.verify(url, user, password, now)

        if current_report.fingerprint != prior_report.fingerprint:
            raise GateFailure("database identity changed between preflight and migration")

        return True
