#Requires -Version 5.1
<# Runs the synthetic EB-49 rehearsal twice using only containers created by its test. #>
[CmdletBinding()]
param([Parameter(Mandatory = $true)][switch]$Disposable)
$ErrorActionPreference = 'Stop'
if (-not $Disposable) { throw 'Explicit -Disposable selection is required.' }
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not (Test-Path -LiteralPath (Join-Path $repo 'mvnw.cmd'))) { throw 'Repository Maven wrapper missing.' }
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker is required.' }
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Java is required.' }
Push-Location -LiteralPath $repo
try {
    & .\mvnw.cmd -B '-Dtest=MigrationRehearsalIT' '-Dsurefire.failIfNoSpecifiedTests=true' '-DskipTests=false' test
    if ($LASTEXITCODE -ne 0) { throw 'EB-49 rehearsal failed; inspect sanitized test evidence under target/rehearsal-evidence.' }
} finally {
    Pop-Location
}
