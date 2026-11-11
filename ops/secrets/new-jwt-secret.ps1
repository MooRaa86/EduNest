# Generates a cryptographically random JWT_SECRET (>= 32 bytes of entropy; outputs 48 bytes as base64).
# P6-03 / EB-74 - writes NOTHING to disk. Copy the printed value into the Railway dashboard.
# See: ops/secrets/rotation.md (section 1) and ops/secrets/JWT-GENERATION-RECORD.md.

$ErrorActionPreference = "Stop"
$bytes = New-Object byte[] 48
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $rng.GetBytes($bytes)
} finally {
    $rng.Dispose()
}
$secret = [Convert]::ToBase64String($bytes)

Write-Output ""
Write-Output "=== NEW JWT_SECRET (copy once - it will not be shown again or saved anywhere) ==="
Write-Output ""
Write-Output $secret
Write-Output ""
Write-Output "Next steps (ops/secrets/rotation.md section 1):"
Write-Output "  1. Save the CURRENT value to ops/secrets/local/ (gitignored) as the rollback lane."
Write-Output "  2. Railway -> EduNest service -> Variables -> set JWT_SECRET to the value above."
Write-Output "  3. Wait for the automatic redeploy; verify /actuator/health UP and a working login."
Write-Output "  4. Append a provenance row (no value!) to ops/secrets/JWT-GENERATION-RECORD.md."
Write-Output "  5. Delete the old value from ops/secrets/local/ after the verification window."
Write-Output ""
Write-Output "NEVER commit this value, paste it into chat, or store it outside the Railway dashboard."
