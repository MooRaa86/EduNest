# Offline database backup via pg_dump (P6-02 / EB-73 - Tier 2 of the backup policy).
# Produces a custom-format dump in ops/backups/local/ (gitignored) and prunes old copies.
# See: ops/backups/backup-policy.md and ops/backups/restore-procedure.md.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File ops/backups/Invoke-Backup.ps1 -DatabaseUrl "postgresql://user:pass@host:5432/postgres"
#   (or set DATABASE_URL in the environment and omit the parameter)
#
# Requires: pg_dump (PostgreSQL client tools) on PATH - matching or newer than the server major version.

param(
    [string]$DatabaseUrl
)

$ErrorActionPreference = "Stop"

if (-not $DatabaseUrl) {
    $DatabaseUrl = $env:DATABASE_URL
}
if (-not $DatabaseUrl) {
    Write-Output "ERROR: no DATABASE_URL provided (parameter or environment)."
    Write-Output "Never hardcode credentials in this script or its callers."
    exit 1
}

# pg_dump speaks the libpq URL form: accept postgresql:// (and tolerate the jdbc:postgresql:// app form by stripping jdbc:)
if ($DatabaseUrl -like "jdbc:postgresql://*") { $DatabaseUrl = $DatabaseUrl.Substring(5) }

$pgDump = Get-Command pg_dump -ErrorAction SilentlyContinue
if (-not $pgDump) {
    Write-Output "ERROR: pg_dump not found on PATH. Install PostgreSQL client tools (psql/pg_dump)."
    exit 1
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$outDir = Join-Path $repoRoot "ops/backups/local"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$dbName = "database"
if ($DatabaseUrl -match "/([^/?]+)(\?.*)?$") { $dbName = $Matches[1] }
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outFile = Join-Path $outDir ("{0}-{1}.dump" -f $dbName, $stamp)

Write-Output ("Backing up '{0}' -> {1}" -f $dbName, $outFile)
& pg_dump $DatabaseUrl --format=custom --no-owner --no-privileges --file=$outFile
if ($LASTEXITCODE -ne 0) {
    Write-Output "ERROR: pg_dump failed with exit code $LASTEXITCODE. No backup recorded."
    if (Test-Path $outFile) { Remove-Item $outFile -Force }
    exit 1
}

$size = (Get-Item $outFile).Length
if ($size -lt 10KB) {
    Write-Output ("ERROR: dump is suspiciously small ({0} bytes) - investigate before trusting this backup." -f $size)
    exit 1
}
Write-Output ("Backup written: {0} ({1:N0} bytes)" -f $outFile, $size)

# Retention: keep the 14 most recent dumps for this database.
$keep = 14
$dumps = Get-ChildItem $outDir -Filter ("{0}-*.dump" -f $dbName) | Sort-Object LastWriteTime -Descending
if ($dumps.Count -gt $keep) {
    $stale = $dumps | Select-Object -Skip $keep
    $stale | ForEach-Object { Write-Output ("Pruning old dump: " + $_.Name); Remove-Item $_.FullName -Force }
}
Write-Output ("Retention check done: {0} dump(s) on disk for '{1}' (keep {2})." -f $dumps.Count, $dbName, $keep)
Write-Output "Reminder: verify restores weekly (ops/backups/restore-procedure.md); a backup never restored is a hypothesis."
