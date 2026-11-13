"""Opt-in integration test that creates, verifies and removes its own DB."""
import json
import os
import secrets
import shutil
import subprocess
import tempfile
import time
import unittest
from pathlib import Path

from flyway_adapter import FlywayAdapter, RehearsalFlywayAdapter
from production_gate import GateFailure

IMAGE = "postgres@sha256:266d3c257594e195b309ae9d1805e54c788f1d7b375f56bf6c367aac6c917e7b"


class ContainerVerifier:
    def __init__(self, container, nonce, password, port):
        self.container, self.nonce, self.password, self.port = container, nonce, password, port

    def verify(self, env):
        try:
            result = subprocess.run(["docker", "inspect", self.container], capture_output=True, text=True, timeout=10, check=True)
            entry = json.loads(result.stdout)[0]
            ports = entry["NetworkSettings"]["Ports"]["5432/tcp"]
            return (entry["Id"] == self.container and entry["State"]["Running"] is True
                    and entry["Config"]["Image"] == IMAGE
                    and entry["Config"]["Labels"].get("eb115.rehearsal") == self.nonce
                    and len(ports) == 1 and ports[0]["HostIp"] == "127.0.0.1" and ports[0]["HostPort"] == self.port
                    and env.get("FLYWAY_URL") == f"jdbc:postgresql://127.0.0.1:{self.port}/rehearsal"
                    and env.get("FLYWAY_USER") == "postgres" and env.get("FLYWAY_PASSWORD") == self.password)
        except (OSError, subprocess.SubprocessError, ValueError, KeyError, IndexError, TypeError):
            return False


@unittest.skipUnless(os.environ.get("EB115_RUN_REHEARSAL_IT") == "1", "isolated rehearsal only")
class FlywayAdapterIntegrationTests(unittest.TestCase):
    def test_flyway_against_owned_disposable_postgres(self):
        nonce = secrets.token_hex(16)
        password = secrets.token_hex(24)
        result = subprocess.run(["docker", "run", "-d", "--rm", "--label", f"eb115.rehearsal={nonce}",
                                 "-p", "127.0.0.1::5432", "-e", "POSTGRES_USER=postgres",
                                 "-e", f"POSTGRES_PASSWORD={password}", "-e", "POSTGRES_DB=rehearsal", IMAGE],
                                capture_output=True, text=True, timeout=60, check=True)
        container = result.stdout.strip()
        self.addCleanup(subprocess.run, ["docker", "rm", "-f", container], capture_output=True, timeout=30)
        for _ in range(30):
            ready = subprocess.run(["docker", "exec", container, "pg_isready", "-U", "postgres", "-d", "rehearsal"], capture_output=True)
            if ready.returncode == 0: break
            time.sleep(1)
        self.assertEqual(ready.returncode, 0, "disposable DB never became ready")
        details = json.loads(subprocess.run(["docker", "inspect", container], capture_output=True, text=True, check=True).stdout)[0]
        port = details["NetworkSettings"]["Ports"]["5432/tcp"][0]["HostPort"]
        env = dict(os.environ)
        env.update(FLYWAY_URL=f"jdbc:postgresql://127.0.0.1:{port}/rehearsal", FLYWAY_USER="postgres", FLYWAY_PASSWORD=password)
        proof = ContainerVerifier(container, nonce, password, port)
        self.assertTrue(proof.verify(env))
        adapter = RehearsalFlywayAdapter(".", env, proof)
        adapter.validate_pending_allowed()
        self.assertTrue(any(row["state"] == "Pending" for row in adapter.info()))
        with self.assertRaises(GateFailure): FlywayAdapter(".", env).migrate()
        adapter.migrate()
        adapter.validate_strict()
        self.assertTrue(all(row["state"] == "Success" for row in adapter.info()))
        proof.nonce = "wrong"
        with self.assertRaises(GateFailure): adapter.migrate()
        proof.nonce = nonce
        with tempfile.TemporaryDirectory(prefix="eb115-flyway-") as directory:
            root = Path(directory)
            shutil.copy("pom.xml", root / "pom.xml")
            shutil.copy("mvnw", root / "mvnw")
            shutil.copytree(".mvn", root / ".mvn")
            migrations = root / "src/main/resources/db/migration"
            migrations.parent.mkdir(parents=True)
            shutil.copytree("src/main/resources/db/migration", migrations)
            (migrations / "V999__invalid_rehearsal.sql").write_text("THIS IS NOT VALID SQL;\n")
            broken = RehearsalFlywayAdapter(str(root), env, proof)
            self.assertTrue(any(row["version"] == "999" and row["state"] == "Pending" for row in broken.info()))
            with self.assertRaises(GateFailure): broken.migrate()
