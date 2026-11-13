"""Flyway Maven adapter; no automatic baseline, repair, clean or undo.

The caller must authenticate target identity, freshness and attestations before
invoking migrate. This adapter is not connected to the production workflow.
"""
import subprocess
import re
from pathlib import Path

from production_gate import GateFailure, migration_states


def release_manifest(root):
    directory = Path(root) / "src/main/resources/db/migration"
    if not directory.is_dir():
        raise GateFailure("migration directory missing")
    versions = set()
    for path in directory.glob("V*__*.sql"):
        match = re.fullmatch(r"V([1-9][0-9]*)__[A-Za-z0-9_]+\.sql", path.name)
        if not match or match.group(1) in versions:
            raise GateFailure("invalid or duplicate release migration")
        versions.add(match.group(1))
    if not versions:
        raise GateFailure("release migration manifest empty")
    return versions


def parse_info(output, manifest):
    """Parse the Flyway 11.7.2 Maven info table; reject unknown table shapes."""
    rows = []
    header = False
    borders = 0
    finished = False
    for line in output.splitlines():
        line = line.strip()
        if line.startswith("[INFO] "):
            line = line[7:]
        if line.startswith("+") and line.endswith("+") and re.fullmatch(r"[+\-]+", line):
            borders += 1
            if header and rows and borders >= 3:
                finished = True
            continue
        if not line.startswith("|"):
            continue
        if finished:
            raise GateFailure("multiple or malformed Flyway tables")
        fields = [field.strip() for field in line.strip("|").split("|")]
        if fields and fields[0] == "Category":
            if header or borders != 1 or fields != ["Category", "Version", "Description", "Type", "Installed On", "State", "Undoable"]:
                raise GateFailure("unsupported Flyway info header")
            header = True
            continue
        if not header or borders != 2 or len(fields) != 7 or fields[0] != "Versioned" or fields[3] not in ("SQL", "BASELINE"):
            raise GateFailure("unsupported Flyway info table")
        rows.append({"version": fields[1], "state": fields[5]})
    migration_states(rows)
    if not finished or borders != 3 or {row["version"] for row in rows} != manifest:
        raise GateFailure("incomplete Flyway report or migration manifest mismatch")
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
        if any("flyway:migrate" in arg for arg in args):
            raise GateFailure("direct Flyway mutation is disabled")
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
        return parse_info(self._call("flyway:info"), release_manifest(self.root))

    def validate_pending_allowed(self):
        self._call("-Dflyway.ignoreMigrationPatterns=*:pending", "flyway:validate")

    def validate_strict(self):
        self._call("-Dflyway.ignoreMigrationPatterns=", "flyway:validate")

    def migrate(self):
        raise GateFailure("production mutation is disabled; no connected identity authority exists")


class RehearsalFlywayAdapter(FlywayAdapter):
    """Disposable-only adapter; verified harness must supply its own container."""
    def __init__(self, root, environment, container_verifier, runner=subprocess.run):
        super().__init__(root, environment, runner)
        self.container_verifier = container_verifier

    def migrate(self):
        # Verification is re-run immediately before the mutation. Production
        # credentials must never be supplied to this rehearsal-only adapter.
        url = self.env.get("FLYWAY_URL", "")
        if (not re.fullmatch(r"jdbc:postgresql://127\.0\.0\.1:[0-9]{1,5}/rehearsal", url)
                or self.env.get("FLYWAY_USER") != "postgres"
                or self.container_verifier.verify(self.env) is not True):
            raise GateFailure("disposable database identity unverified")
        try:
            result = self.runner(["./mvnw", "-B", "-Pdb-migrate", "flyway:migrate"], cwd=self.root,
                                 env=self.env, capture_output=True, text=True, timeout=900, check=False)
        except (OSError, subprocess.TimeoutExpired):
            raise GateFailure("rehearsal execution failed or timed out") from None
        if result.returncode != 0:
            raise GateFailure("rehearsal migration failed")


def migrate_production(*_args, **_kwargs):
    # No authenticated connected-database identity, replay store, or serving
    # digest observer exists yet. Do not implement a caller-supplied bypass.
    raise GateFailure("production migration unavailable until independent authorities are implemented")
