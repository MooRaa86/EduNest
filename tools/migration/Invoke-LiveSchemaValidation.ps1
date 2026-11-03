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

function Format-And-Validate-JdbcUrl($rawUrl) {
    if ([string]::IsNullOrWhiteSpace($rawUrl)) {
        throw "DATABASE_URL cannot be empty."
    }
    $trimmed = $rawUrl.Trim()
    if (-not $trimmed.StartsWith("jdbc:postgresql://")) {
        throw "DATABASE_URL must start with 'jdbc:postgresql://'."
    }

    # Reject forbidden/insecure sslmode parameters
    if ($trimmed -match "sslmode=(disable|allow|prefer)") {
        throw "Insecure sslmode detected ($($Matches[0])). Supabase PostgreSQL requires sslmode=require or stricter (verify-ca, verify-full)."
    }

    # Reject conflicting or duplicated sslmode parameters
    if (($trimmed -split "sslmode=").Count -gt 2) {
        throw "Conflicting or duplicate sslmode parameters detected in DATABASE_URL."
    }

    # Clean trailing question mark if empty query
    if ($trimmed.EndsWith("?")) {
        $trimmed = $trimmed.Substring(0, $trimmed.Length - 1)
    }

    # Enforce sslmode=require (or verify-ca / verify-full)
    if (-not ($trimmed -match "sslmode=(require|verify-ca|verify-full)")) {
        if ($trimmed.Contains("?")) {
            $trimmed = $trimmed + "&sslmode=require"
        } else {
            $trimmed = $trimmed + "?sslmode=require"
        }
        Write-Host "Enforced SSL: appended sslmode=require to JDBC URL." -ForegroundColor Yellow
    }

    return $trimmed
}

$expectedRef = "ciogysvcykzxrjuskxmk"

if ([string]::IsNullOrWhiteSpace($env:DATABASE_URL) -or [string]::IsNullOrWhiteSpace($env:DATABASE_PASSWORD)) {
    if ([Environment]::UserInteractive -and -not [Console]::IsInputRedirected) {
        Write-Host "--- Supabase PostgreSQL Target Configuration ---" -ForegroundColor Cyan
        $urlIn = Read-Host "DATABASE_URL (e.g. jdbc:postgresql://aws-0-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require)"
        if (-not [string]::IsNullOrWhiteSpace($urlIn)) {
            try {
                $validatedUrl = Format-And-Validate-JdbcUrl $urlIn
                Set-SecureProcessEnv "DATABASE_URL" $validatedUrl
            } catch {
                Write-Host "`n[BLOCKED] Invalid DATABASE_URL: $($_.Exception.Message)" -ForegroundColor Red
                exit 1
            }
        }

        $userPgIn = Read-Host "DATABASE_NAME (PostgreSQL username, e.g. postgres.ciogysvcykzxrjuskxmk)"
        if (-not [string]::IsNullOrWhiteSpace($userPgIn)) {
            Set-SecureProcessEnv "DATABASE_NAME" $userPgIn.Trim()
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

# If URL already present in environment, validate and normalize it as well
if (-not [string]::IsNullOrWhiteSpace($env:DATABASE_URL)) {
    try {
        $validatedEnvUrl = Format-And-Validate-JdbcUrl $env:DATABASE_URL
        Set-SecureProcessEnv "DATABASE_URL" $validatedEnvUrl
    } catch {
        Write-Host "`n[BLOCKED] Invalid DATABASE_URL in environment: $($_.Exception.Message)" -ForegroundColor Red
        exit 1
    }
}

if ([string]::IsNullOrWhiteSpace($env:DATABASE_URL) -or [string]::IsNullOrWhiteSpace($env:DATABASE_PASSWORD)) {
    Write-Host "`n[BLOCKED] Live validation was SKIPPED because database credentials were not provided. Live Hibernate validation has NOT been executed." -ForegroundColor Red
    exit 1
}

# Enforce project identity check
$urlHasRef = ($env:DATABASE_URL -and $env:DATABASE_URL.Contains($expectedRef))
$userHasRef = ($env:DATABASE_NAME -and $env:DATABASE_NAME.Contains($expectedRef))
if (-not ($urlHasRef -or $userHasRef)) {
    Write-Host "`n[BLOCKED] Neither DATABASE_URL nor DATABASE_NAME contains authorized project reference '$expectedRef'." -ForegroundColor Red
    Write-Host "Live validation is restricted exclusively to the authorized Supabase Development project." -ForegroundColor Red
    exit 1
}

$sanitizedUrl = $env:DATABASE_URL -replace "://[^@/]+@", "://***@"
Write-Host "`nTarget Project Reference: $expectedRef (Verified)" -ForegroundColor Green
Write-Host "Target Endpoint: $sanitizedUrl" -ForegroundColor Cyan

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
