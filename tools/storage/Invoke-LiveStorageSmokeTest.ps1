<#
.SYNOPSIS
    Runs the opt-in live Supabase Storage (S3) Smoke Test against Supabase Project ciogysvcykzxrjuskxmk.

.DESCRIPTION
    Prompts for S3 Access Key ID and Secret Access Key (masked), exports them to the process scope,
    and runs the JUnit live smoke test suite with -Dedunest.storage.live.test=true.

.EXAMPLE
    .\tools\storage\Invoke-LiveStorageSmokeTest.ps1
#>

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  EDUNEST EB-83: LIVE SUPABASE STORAGE (S3) SMOKE TEST      " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "Target Project: ciogysvcykzxrjuskxmk"
Write-Host "S3 Endpoint:    https://ciogysvcykzxrjuskxmk.storage.supabase.co/storage/v1/s3"
Write-Host "Region:         eu-west-1"
Write-Host "Public Bucket:  edunest-public"
Write-Host "Private Bucket: edunest-private"
Write-Host ""

if (-not $env:SUPABASE_S3_ACCESS_KEY) {
    $accessKey = Read-Host "Enter Supabase S3 Access Key ID"
    if ([string]::IsNullOrWhiteSpace($accessKey)) {
        Write-Error "S3 Access Key ID cannot be empty."
        exit 1
    }
    $env:SUPABASE_S3_ACCESS_KEY = $accessKey.Trim()
}

if (-not $env:SUPABASE_S3_SECRET_KEY) {
    $secretKeySecure = Read-Host -AsSecureString "Enter Supabase S3 Secret Access Key (masked)"
    $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secretKeySecure)
    $secretKey = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    if ([string]::IsNullOrWhiteSpace($secretKey)) {
        Write-Error "S3 Secret Access Key cannot be empty."
        exit 1
    }
    $env:SUPABASE_S3_SECRET_KEY = $secretKey.Trim()
}

$env:SUPABASE_STORAGE_ENABLED = "true"
$env:SUPABASE_S3_ENDPOINT = "https://ciogysvcykzxrjuskxmk.storage.supabase.co/storage/v1/s3"
$env:SUPABASE_S3_REGION = "eu-west-1"
$env:SUPABASE_STORAGE_PUBLIC_BUCKET = "edunest-public"
$env:SUPABASE_STORAGE_PRIVATE_BUCKET = "edunest-private"
$env:SUPABASE_URL = "https://ciogysvcykzxrjuskxmk.supabase.co"

Write-Host ""
Write-Host "Executing live smoke test suite..." -ForegroundColor Yellow

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Push-Location $repoRoot
try {
    & .\mvnw.cmd test "-Dtest=SupabaseStorageLiveSmokeTest" "-Dedunest.storage.live.test=true"
} finally {
    Pop-Location
}
