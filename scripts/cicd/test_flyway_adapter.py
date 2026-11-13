import subprocess
import unittest
from types import SimpleNamespace
from flyway_adapter import FlywayAdapter, parse_info
from production_gate import GateFailure

TABLE = """[INFO] | Category | Version | Description | Type | Installed On | State | Undoable |
| Versioned | 1 | initial schema | SQL | 2026-01-01 | Success | No |
| Versioned | 2 | next | SQL | | Pending | No |
"""


class FlywayAdapterTests(unittest.TestCase):
    def test_real_flyway_table_shape(self):
        # Maven only prefixes the table as a block; retain an unprefixed header
        # as emitted by Flyway 11.7.2.
        rows = parse_info(TABLE.replace("[INFO] |", "|"))
        self.assertEqual(rows[1], {"version": "2", "state": "Pending"})

    def test_unknown_shapes_and_states_block(self):
        for text in ("", "| Versioned | 1 | bad | SQL | | Failed | No |",
                     "| Versioned | 1 | bad | SQL | Broken |"):
            with self.assertRaises(GateFailure): parse_info(text)

    def test_commands_and_redaction(self):
        calls = []
        def runner(command, **kwargs):
            calls.append(command)
            return SimpleNamespace(returncode=0, stdout=TABLE.replace("[INFO] |", "|"), stderr="")
        env = {"FLYWAY_URL": "secret-url", "FLYWAY_USER": "secret-user", "FLYWAY_PASSWORD": "secret-password",
               "FLYWAY_LOCATIONS": "attacker", "JAVA_TOOL_OPTIONS": "attacker"}
        adapter = FlywayAdapter(".", env, runner)
        self.assertNotIn("FLYWAY_LOCATIONS", adapter.env)
        self.assertNotIn("JAVA_TOOL_OPTIONS", adapter.env)
        adapter.validate_pending_allowed()
        adapter.info()
        adapter.migrate()
        adapter.validate_strict()
        self.assertEqual([call[-1] for call in calls], ["flyway:validate", "flyway:info", "flyway:migrate", "flyway:validate"])
        self.assertNotIn("secret-password", str(calls))
        def fail(command, **kwargs):
            return SimpleNamespace(returncode=1, stdout="secret-password", stderr="secret-password")
        with self.assertRaises(GateFailure) as error:
            FlywayAdapter(".", env, fail).migrate()
        self.assertNotIn("secret-password", str(error.exception))


if __name__ == "__main__": unittest.main()
