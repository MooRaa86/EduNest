import subprocess
import unittest
import tempfile
from pathlib import Path
from types import SimpleNamespace
from flyway_adapter import FlywayAdapter, migrate_production, parse_info
from production_gate import GateFailure

TABLE = """[INFO] +-----+-----+-----+-----+-----+-----+-----+
| Category | Version | Description | Type | Installed On | State | Undoable |
+-----+-----+-----+-----+-----+-----+-----+
| Versioned | 1 | initial schema | SQL | 2026-01-01 | Success | No |
| Versioned | 2 | next | SQL | | Pending | No |
+-----+-----+-----+-----+-----+-----+-----+
"""


class FlywayAdapterTests(unittest.TestCase):
    def test_real_flyway_table_shape(self):
        # Maven only prefixes the table as a block; retain an unprefixed header
        # as emitted by Flyway 11.7.2.
        rows = parse_info(TABLE, {"1", "2"})
        self.assertEqual(rows[1], {"version": "2", "state": "Pending"})

    def test_unknown_shapes_and_states_block(self):
        for text in ("", TABLE.replace("Pending", "Failed"), TABLE.rsplit("+", 1)[0],
                     TABLE.replace("| Versioned | 2", "| Versioned | 1")):
            with self.assertRaises(GateFailure): parse_info(text, {"1", "2"})
        with self.assertRaises(GateFailure): parse_info(TABLE, {"1", "2", "3"})
        with self.assertRaises(GateFailure): parse_info(TABLE, {"1"})
        with self.assertRaises(GateFailure): migrate_production()

    def test_commands_and_redaction(self):
        calls = []
        def runner(command, **kwargs):
            calls.append(command)
            return SimpleNamespace(returncode=0, stdout=TABLE, stderr="")
        env = {"FLYWAY_URL": "secret-url", "FLYWAY_USER": "secret-user", "FLYWAY_PASSWORD": "secret-password",
               "FLYWAY_LOCATIONS": "attacker", "JAVA_TOOL_OPTIONS": "attacker"}
        with tempfile.TemporaryDirectory() as directory:
            migrations = Path(directory) / "src/main/resources/db/migration"
            migrations.mkdir(parents=True)
            (migrations / "V1__initial.sql").touch()
            (migrations / "V2__next.sql").touch()
            adapter = FlywayAdapter(directory, env, runner)
            self.assertNotIn("FLYWAY_LOCATIONS", adapter.env)
            self.assertNotIn("JAVA_TOOL_OPTIONS", adapter.env)
            adapter.validate_pending_allowed()
            adapter.info()
            with self.assertRaises(GateFailure): adapter.migrate()
            with self.assertRaises(GateFailure): adapter._call("flyway:migrate")
            adapter.validate_strict()
        self.assertEqual([call[-1] for call in calls], ["flyway:validate", "flyway:info", "flyway:validate"])
        self.assertNotIn("secret-password", str(calls))
        def fail(command, **kwargs):
            return SimpleNamespace(returncode=1, stdout="secret-password", stderr="secret-password")
        with self.assertRaises(GateFailure) as error:
            FlywayAdapter(".", env, fail).validate_strict()
        self.assertNotIn("secret-password", str(error.exception))


if __name__ == "__main__": unittest.main()
