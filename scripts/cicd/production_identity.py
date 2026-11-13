#!/usr/bin/env python3
"""Fail-closed EB-115 credential-form check. Reads secrets only from the environment.

This is a syntactic identity guard, not proof of the remote database's identity.
"""
import os
import re
import sys
from urllib.parse import parse_qsl, urlsplit

REF = re.compile(r"[a-z0-9]{20}")
POOLER = re.compile(r"[a-z0-9-]+\.pooler\.supabase\.com")


def check(url: str, user: str, approved: str, staging: str, database: str, schema: str) -> bool:
    # Both policy refs must come from independently protected configuration,
    # never from the connection string being checked.
    if not REF.fullmatch(approved) or not REF.fullmatch(staging) or staging == approved:
        return False
    if database != "postgres" or schema != "public" or not url.startswith("jdbc:postgresql://"):
        return False
    try:
        parsed = urlsplit(url[len("jdbc:"):])
        if (parsed.scheme != "postgresql" or not parsed.hostname or parsed.username is not None
                or parsed.password is not None or parsed.fragment or parsed.port != 5432
                or parsed.path != "/postgres" or "," in parsed.netloc):
            return False
        params = parse_qsl(parsed.query, keep_blank_values=True, strict_parsing=True)
        if params != [("sslmode", "verify-full")]:
            return False
        host = parsed.hostname
        direct = host == f"db.{approved}.supabase.co" and user == "postgres"
        pooler = bool(POOLER.fullmatch(host)) and user == f"postgres.{approved}"
        return direct or pooler
    except (ValueError, TypeError):
        return False


def main() -> int:
    ok = check(*(os.environ.get(key, "") for key in (
        "FLYWAY_URL", "FLYWAY_USER", "PROD_DB_PROJECT_REF", "STAGING_DB_PROJECT_REF",
        "PROD_DB_NAME", "PROD_DB_SCHEMA")))
    print("Production credential-form identity: " + ("PASS" if ok else "BLOCK"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
