"""Flyway Maven adapter; no automatic baseline, repair, clean or undo.

The caller must authenticate target identity, freshness and attestations before
invoking migrate. This adapter is not connected to the production workflow.
"""
import subprocess

from production_gate import GateFailure, migration_states


def parse_info(output):
    """Parse the Flyway 11.7.2 Maven info table; reject unknown table shapes."""
    rows = []
    for line in output.splitlines():
        line = line.strip()
        if not line.startswith("|"):
            continue
        fields = [field.strip() for field in line.strip("|").split("|")]
        if fields and fields[0] == "Category":
            continue
        if len(fields) != 7 or fields[0] != "Versioned" or fields[3] not in ("SQL", "BASELINE"):
            raise GateFailure("unsupported Flyway info table")
        rows.append({"version": fields[1], "state": fields[5]})
    migration_states(rows)
    return rows


class FlywayAdapter:
    def __init__(self, root, environment, runner=subprocess.run):
        self.root = root
        # Drop inherited Flyway/JDBC/Maven overrides and JVM option injection.
        # The caller still has to verify these three credential values against
        # independently protected production policy before any live call.
        allowed = ("PATH", "HOME", "JAVA_HOME", "USERPROFILE", "SYSTEMROOT", "WINDIR", "TEMP", "TMP",
                   "FLYWAY_URL", "FLYWAY_USER", "FLYWAY_PASSWORD")
        self.env = {key: environment[key] for key in allowed if key in environment}
        self.runner = runner
        for key in ("FLYWAY_URL", "FLYWAY_USER", "FLYWAY_PASSWORD"):
            if not self.env.get(key):
                raise GateFailure("Flyway configuration missing")

    def _call(self, *args):
        # Capture output: Flyway and Maven can print JDBC URLs and server
        # diagnostics. Never forward stdout/stderr to logs or artifacts.
        try:
            result = self.runner(["./mvnw", "-B", "-Pdb-migrate", *args], cwd=self.root,
                                 env=self.env, capture_output=True, text=True, timeout=900,
                                 check=False)
        except (OSError, subprocess.TimeoutExpired):
            raise GateFailure("Flyway execution failed or timed out") from None
        if result.returncode != 0:
            raise GateFailure("Flyway execution failed")
        return result.stdout

    def info(self):
        return parse_info(self._call("flyway:info"))

    def validate_pending_allowed(self):
        self._call("-Dflyway.ignoreMigrationPatterns=*:pending", "flyway:validate")

    def validate_strict(self):
        self._call("-Dflyway.ignoreMigrationPatterns=", "flyway:validate")

    def migrate(self):
        self._call("flyway:migrate")
