# Invoke-LiveSchemaValidation.ps1
# Runs the standalone PostgreSqlLiveSchemaValidationTest against Supabase Development.
# Strictly read-only: No application startup, no RolesSeeder, no OtpCleanupService, no writes.

$ErrorActionPreference = "Stop"

Write-Host "==========================================================================" -ForegroundColor Cyan
Write-Host " EduNest EB-82: Standalone Live PostgreSQL Schema Validation Runner" -ForegroundColor Cyan
Write-Host "==========================================================================" -ForegroundColor Cyan

function Set-SecureProcessEnv($name, $val) {
    Set-Item -Path "env:$name" -Value $val
    [System.Environment]::SetEnvironmentVariable($name, $val, [System.EnvironmentVariableTarget]::Process)
}

if ([string]::IsNullOrWhiteSpace($env:DATABASE_URL) -or [string]::IsNullOrWhiteSpace($env:DATABASE_PASSWORD)) {
    if ([Environment]::UserInteractive -and -not [Console]::IsInputRedirected) {
        Write-Host "--- Supabase PostgreSQL Target Configuration ---" -ForegroundColor Cyan
        $urlIn = Read-Host "DATABASE_URL (e.g. jdbc:postgresql://aws-0-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require)"
        if (-not [string]::IsNullOrWhiteSpace($urlIn)) {
            Set-SecureProcessEnv "DATABASE_URL" $urlIn
        }

        $userPgIn = Read-Host "DATABASE_NAME (PostgreSQL username, e.g. postgres.ciogysvcykzxrjuskxmk)"
        if (-not [string]::IsNullOrWhiteSpace($userPgIn)) {
            Set-SecureProcessEnv "DATABASE_NAME" $userPgIn
        }

        $secPassPg = Read-Host -AsSecureString "DATABASE_PASSWORD (input masked)"
        if ($secPassPg -ne $null -and $secPassPg.Length -gt 0) {
            $bstrPg = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secPassPg)
            $plainPassPg = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstrPg)
            [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstrPg)
            Set-SecureProcessEnv "DATABASE_PASSWORD" $plainPassPg
        }
    }
}

if ([string]::IsNullOrWhiteSpace($env:DATABASE_URL) -or [string]::IsNullOrWhiteSpace($env:DATABASE_PASSWORD)) {
    Write-Host "`n[BLOCKED] Live validation was SKIPPED because database credentials were not provided. Live Hibernate validation has NOT been executed." -ForegroundColor Red
    exit 1
}

$repoPath = "C:\Users\Dell\AppData\Local\Temp\opencode\edunest-eb82"
Set-Location $repoPath

# Clean previous surefire reports for this test
$reportPath = Join-Path $repoPath "target\surefire-reports\TEST-com.example.gradproj.EduNest.migration.PostgreSqlLiveSchemaValidationTest.xml"
if (Test-Path $reportPath) {
    Remove-Item -Path $reportPath -Force
}

Write-Host "`nRunning PostgreSqlLiveSchemaValidationTest via Maven..." -ForegroundColor Cyan
& ".\mvnw.cmd" test "-Dtest=PostgreSqlLiveSchemaValidationTest"
$mvnExit = $LASTEXITCODE

if (-not (Test-Path $reportPath)) {
    Write-Host "`n[BLOCKED] Surefire test report not found at $reportPath (Maven exit code: $mvnExit)." -ForegroundColor Red
    exit 1
}

[xml]$xml = Get-Content $reportPath
$tests = [int]$xml.testsuite.tests
$skipped = [int]$xml.testsuite.skipped
$failures = [int]$xml.testsuite.failures
$errors = [int]$xml.testsuite.errors

Write-Host "`n--- Test Suite Summary ---" -ForegroundColor Cyan
Write-Host "Tests run: $tests, Failures: $failures, Errors: $errors, Skipped: $skipped" -ForegroundColor Cyan

if ($skipped -gt 0) {
    Write-Host "`n[BLOCKED] Live validation was SKIPPED because database credentials were not provided. Live Hibernate validation has NOT been executed." -ForegroundColor Red
    exit 1
}

if ($failures -gt 0 -or $errors -gt 0 -or $tests -eq 0 -or $mvnExit -ne 0) {
    Write-Host "`n[FAILED] Live Hibernate schema validation FAILED (Maven exit: $mvnExit, failures: $failures, errors: $errors)." -ForegroundColor Red
    exit 1
}

Write-Host "`n[SUCCESS] Live Hibernate schema validation PASSED against Supabase Development." -ForegroundColor Green
exit 0
