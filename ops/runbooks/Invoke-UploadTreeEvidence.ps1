# Upload-tree filesystem hardening evidence collector (EB-89 - ops ticket).
# Read-only: collects the written deployment evidence demanded by the ticket,
# NO Docker (D-27), no writes, no network.
#
# Gates (mirroring the ticket's acceptance items):
#   1. ACL/owner read-out of the upload root - who can WRITE it (must be the
#      app-service identity/admins only).
#   2. 8.3 short-name generation status for the host volume (must be disabled;
#      fsutil requires elevation - reported as UNVERIFIED when not admin).
#   3. Junction/symlink/reparse-point scan under the upload root (must be empty:
#      untrusted principals must not be able to create reparse points).
#   4. Effective app.upload.base-dir verdict: ABSOLUTE (pinned) or RELATIVE
#      (CWD-coupled - the thing EB-89 pins away in the deployed image).
#
# Exit contract: 0 = all verifiable gates green (with UNVERIFIED items listed
# when elevation/permissions block a check), 1 = at least one gate FAILED.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File ops/runbooks/Invoke-UploadTreeEvidence.ps1
#   ... -BaseDir "D:\path\to\uploads"          (default: repo-root 'uploads', or $env:APP_UPLOAD_BASE_DIR)

param(
    [string]$BaseDir
)

$ErrorActionPreference = "Stop"
$fails = @()
$unverified = @()

function Get-EffectiveBaseDir([string]$explicit) {
    if ($explicit) { return $explicit }
    if ($env:APP_UPLOAD_BASE_DIR) { return $env:APP_UPLOAD_BASE_DIR }
    return "uploads"
}

Write-Output "=========================================================================="
Write-Output " EB-89: Upload-tree hardening evidence (read-only)"
Write-Output "=========================================================================="

$rawBase = Get-EffectiveBaseDir $BaseDir
$isAbsolute = [System.IO.Path]::IsPathRooted($rawBase)
$evidence = [ordered]@{}
$evidence["effective_base_dir"] = $rawBase
$evidence["base_dir_absolute"] = if ($isAbsolute) { "ABSOLUTE (pinned - OK)" } else { "RELATIVE (CWD-coupled - FAIL in prod; the Dockerfile pins ENV APP_UPLOAD_BASE_DIR=/app/uploads)" }
if (-not $isAbsolute) { $fails += ("base-dir is RELATIVE ('{0}' - couples to the process CWD); prod must pin an absolute path" -f $rawBase) }
Write-Output ("base-dir: {0} -> {1}" -f $rawBase, $evidence["base_dir_absolute"])

$root = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $rawBase))
if (-not (Test-Path $root)) {
    Write-Output "upload root does not exist yet (app creates it on first boot) - creating nothing, evidence limited:"
    $unverified += "ACL/8.3/reparse evidence - upload root '$root' does not exist yet"
    $root = $null
}

# ---- Gate 1: ACL / owner read-out --------------------------------------------
if ($root) {
    Write-Output ""
    Write-Output "--- Gate 1: ACL / owner read-out ------------------------------------"
    try {
        $acl = Get-Acl -Path $root
        Write-Output ("owner: {0}" -f $acl.Owner)
        $evidence["owner"] = $acl.Owner
        foreach ($rule in $acl.Access) {
            $rights = $rule.FileSystemRights.ToString()
            $writable = $rights -match "Write|Modify|FullControl|AppendData|CreateFiles"
            Write-Output ("  {0} : {1} ({2}){3}" -f $rule.IdentityReference, $rights, $rule.AccessControlType, $(if ($writable) {"  <-- WRITABLE"} else {""}))
        }
        $writablePrincipals = @($acl.Access | Where-Object {
            $_.FileSystemRights.ToString() -match "Write|Modify|FullControl|AppendData|CreateFiles" -and
            $_.AccessControlType -eq "Allow" } | ForEach-Object { $_.IdentityReference.ToString() } | Sort-Object -Unique)
        $evidence["writable_principals"] = $writablePrincipals -join "; "
        Write-Output ("WRITABLE PRINCIPALS: {0}" -f ($writablePrincipals -join "; "))
        $suspicious = @($writablePrincipals | Where-Object { $_ -match "Everyone|BUILTIN\\Users|Authenticated Users|ANONYMOUS" })
        if ($suspicious.Count -gt 0) {
            $fails += ("upload root is writable by broad principals: {0} - the tree must be writable ONLY by the app-service identity/admins" -f ($suspicious -join "; "))
        }
    } catch {
        $unverified += ("ACL read-out failed: {0}" -f $_.Exception.Message)
    }
}

# ---- Gate 2: 8.3 short-name generation status ---------------------------------
if ($root) {
    Write-Output ""
    Write-Output "--- Gate 2: 8.3 short-name generation (volume) ----------------------"
    $drive = ([System.IO.Path]::GetPathRoot($root)).TrimEnd("\")
    if (-not $drive) { $drive = "C:" }
    try {
        $out8dot3 = cmd /c "fsutil 8dot3name query $drive" 2>&1 | Out-String
        Write-Output ($out8dot3.Trim())
        if ($out8dot3 -match "disabled|Disabled") {
            Write-Output "8.3 generation: DISABLED (OK)"
        } elseif ($out8dot3 -match "enabled|Enabled") {
            $fails += "8.3 short-name generation is ENABLED on $drive - disable via 'fsutil 8dot3name set $drive 1' + reboot"
        } elseif ($out8dot3 -match "denied|administrator|elevation") {
            $unverified += "8.3 status requires an elevated shell (fsutil): rerun as Administrator"
        } else {
            $unverified += ("8.3 status unparsable output: {0}" -f ($out8dot3 -replace "\r?\n", " | "))
        }
    } catch {
        $unverified += ("8.3 query failed: {0} - rerun as Administrator" -f $_.Exception.Message)
    }
}

# ---- Gate 3: junction/symlink/reparse-point scan -------------------------------
if ($root) {
    Write-Output ""
    Write-Output "--- Gate 3: reparse points under the upload root --------------------"
    $reparse = @()
    try {
        $reparse = @(Get-ChildItem -Path $root -Recurse -Force -ErrorAction SilentlyContinue |
            Where-Object { $_.Attributes -band [System.IO.FileAttributes]::ReparsePoint })
    } catch {
        $unverified += ("reparse scan failed: {0}" -f $_.Exception.Message)
    }
    if ($reparse.Count -eq 0) {
        Write-Output "reparse points: NONE (OK)"
    } else {
        $fails += ("{0} reparse point(s) exist under the upload root: {1} - remove them and restrict who can create links there" -f $reparse.Count, (($reparse | ForEach-Object { $_.FullName }) -join "; "))
    }
}

# ---- Evidence block ------------------------------------------------------------
Write-Output ""
Write-Output "=== EVIDENCE BLOCK (paste into EB-89) ==="
foreach ($k in $evidence.Keys) { Write-Output ("{0}: {1}" -f $k, $evidence[$k]) }
Write-Output ("host: {0} | user: {1} | collected: {2}" -f $env:COMPUTERNAME, $env:USERNAME, (Get-Date -Format "yyyy-MM-dd HH:mm zzz"))
if ($unverified.Count -gt 0) {
    Write-Output "UNVERIFIED (action needed):"
    $unverified | ForEach-Object { Write-Output ("  - " + $_) }
}
if ($fails.Count -gt 0) {
    Write-Output "UPLOAD-TREE EVIDENCE FAILED:"
    $fails | ForEach-Object { Write-Output ("  - " + $_) }
    exit 1
}
Write-Output "UPLOAD-TREE EVIDENCE: all verifiable gates green."
exit 0
