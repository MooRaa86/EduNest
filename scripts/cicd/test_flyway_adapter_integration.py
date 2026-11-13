"""Opt-in integration test: only for a disposable local rehearsal database."""
import os
import shutil
import tempfile
import unittest
from pathlib import Path

from flyway_adapter import FlywayAdapter
from production_gate import GateFailure


@unittest.skipUnless(os.environ.get("EB115_RUN_REHEARSAL_IT") == "1", "isolated rehearsal only")
class FlywayAdapterIntegrationTests(unittest.TestCase):
    def test_flyway_against_disposable_postgres(self):
        env = dict(os.environ)
        # Do not accept a caller-supplied target or credential in this test.
        env.update(FLYWAY_URL="jdbc:postgresql://127.0.0.1:5432/rehearsal",
                   FLYWAY_USER="postgres", FLYWAY_PASSWORD="rehearsal-only")
        adapter = FlywayAdapter(".", env)
        adapter.validate_pending_allowed()
        before = adapter.info()
        self.assertTrue(any(row["state"] == "Pending" for row in before))
        adapter.migrate()
        adapter.validate_strict()
        after = adapter.info()
        self.assertTrue(after)
        self.assertTrue(all(row["state"] == "Success" for row in after))

        # Add an invalid next migration only to a temporary copy, then exercise
        # the same executable adapter against the disposable DB. It must fail;
        # no Railway adapter is called by this rehearsal test.
        with tempfile.TemporaryDirectory(prefix="eb115-flyway-") as directory:
            root = Path(directory)
            shutil.copy("pom.xml", root / "pom.xml")
            shutil.copy("mvnw", root / "mvnw")
            shutil.copytree(".mvn", root / ".mvn")
            migrations = root / "src/main/resources/db/migration"
            migrations.parent.mkdir(parents=True)
            shutil.copytree("src/main/resources/db/migration", migrations)
            (migrations / "V999__invalid_rehearsal.sql").write_text("THIS IS NOT VALID SQL;\n")
            broken = FlywayAdapter(str(root), env)
            self.assertTrue(any(row["version"] == "999" and row["state"] == "Pending" for row in broken.info()))
            with self.assertRaises(GateFailure):
                broken.migrate()
