# Post-deploy verification job (P6-08 / EB-79 - DEP-01 verification half).
# Gates a deploy on: liveness/readiness UP within budget, fast aggregate health
# (the P6-08 fix: < AggregateTimeoutSeconds), smoke endpoints, optional commit match.
# Exit code 0 = green, 1 = red (the fail-path contract; rollback triggers: ops/runbooks/rollback-triggers.md).
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File ops/runbooks/Invoke-DeployVerification.ps1 -BaseUrl "https://backend.edunest.dpdns.org"
#   ... -ExpectedCommit "<sha>"            (version match against /actuator/info -> git.commit.id)
#   ... -PollMinutes 20 -PollSeconds 5     (health poll budget; default covers a Railway build+deploy window)

param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl,
    [string]$ExpectedCommit,
    [int]$PollMinutes = 20,
    [int]$PollSeconds = 5,
    [int]$AggregateTimeoutSeconds = 5
)

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$BaseUrl = $BaseUrl.TrimEnd("/")
$fails = @()
$warns = @()

# ---- Gate 1: health poll with timeout (liveness + readiness) -----------------
$deadline = (Get-Date).AddMinutes($PollMinutes)
$liveUp = $false
$readyUp = $false
while ((Get-Date) -lt $deadline) {
    try {
        $live = Invoke-RestMethod -Uri "$BaseUrl/actuator/health/liveness" -Method Get -TimeoutSec 15
        $ready = Invoke-RestMethod -Uri "$BaseUrl/actuator/health/readiness" -Method Get -TimeoutSec 15
        if ($live.status -eq "UP" -and $ready.status -eq "UP") { $liveUp = $true; $readyUp = $true; break }
    } catch { } # not up yet - poll again
    Start-Sleep -Seconds $PollSeconds
}
if (-not ($liveUp -and $readyUp)) {
    $fails += "health poll exhausted the ${PollMinutes}-minute budget - container did not reach liveness UP + readiness UP"
} else {
    Write-Output "OK   liveness UP + readiness UP (poll gate)"
}

# ---- Gate 2: aggregate health must be FAST (verifies the P6-08 fix) ----------
if ($liveUp) {
    try {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $agg = Invoke-WebRequest -Uri "$BaseUrl/actuator/health" -Method Get -TimeoutSec ($AggregateTimeoutSeconds * 2) -UseBasicParsing
        $sw.Stop()
        if ($agg.StatusCode -ne 200) { $fails += "aggregate health returned HTTP $($agg.StatusCode) (expected 200)" }
        elseif ($sw.Elapsed.TotalSeconds -gt $AggregateTimeoutSeconds) {
            $fails += ("aggregate health took {0:N1}s (budget {1}s) - the db/mail indicator hang may have been re-enabled; see management.health.* posture" -f $sw.Elapsed.TotalSeconds, $AggregateTimeoutSeconds)
        } else {
            Write-Output ("OK   /actuator/health -> 200 in {0:N1}s (fast-aggregate gate)" -f $sw.Elapsed.TotalSeconds)
        }
    } catch {
        $fails += "aggregate health unreachable: $($_.Exception.Message)"
    }
}

# ---- Gate 3: smoke endpoint set (canonical, same family as the staging smoke) -
if ($liveUp) {
    try {
        $swagger = Invoke-WebRequest -Uri "$BaseUrl/swagger-ui/index.html" -Method Get -TimeoutSec 30 -UseBasicParsing
        if ($swagger.StatusCode -ne 200) { $fails += "swagger-ui returned HTTP $($swagger.StatusCode) (expected 200)" }
        else { Write-Output "OK   /swagger-ui/index.html -> 200" }
    } catch { $fails += "swagger-ui unreachable: $($_.Exception.Message)" }
    try {
        $ws = Invoke-RestMethod -Uri "$BaseUrl/ws/info" -Method Get -TimeoutSec 30
        if (-not $ws.websocket) { $fails += "ws/info did not advertise websocket support" }
        else { Write-Output "OK   /ws/info -> websocket: true" }
    } catch { $fails += "ws/info unreachable: $($_.Exception.Message)" }
}

# ---- Gate 4: version match (optional; full tag scheme lands with P6-04) ------
if ($ExpectedCommit) {
    if (-not ($liveUp)) {
        $warns += "version match skipped - container never came up"
    } else {
        try {
            $info = Invoke-RestMethod -Uri "$BaseUrl/actuator/info" -Method Get -TimeoutSec 30
            $gitId = $info.git.commit.id.full
            if (-not $gitId) { $gitId = $info.git.commit.id }
            if (-not $gitId) {
                $fails += "version match FAILED: /actuator/info exposes no git.commit.id (build-info/git-commit-id plumbing missing from this image)"
            } elseif (-not ($gitId.ToLower().StartsWith($ExpectedCommit.ToLower()))) {
                $fails += "version match FAILED: deployed commit $gitId does not match expected $ExpectedCommit (Railway may have deployed a different push)"
            } else {
                Write-Output ("OK   version match -> deployed commit {0} matches expected" -f $gitId.Substring(0, 12))
            }
        } catch {
            $fails += "version match could not read /actuator/info: $($_.Exception.Message)"
        }
    }
} else {
    $warns += "version match skipped - no -ExpectedCommit provided (wired automatically by the verify-deploy workflow)"
}

Write-Output ""
if ($warns.Count -gt 0) {
    Write-Output "WARNINGS:"
    $warns | ForEach-Object { Write-Output ("  - " + $_) }
}
if ($fails.Count -gt 0) {
    Write-Output "DEPLOY VERIFICATION FAILED:"
    $fails | ForEach-Object { Write-Output ("  - " + $_) }
    Write-Output "Action: run the rollback trigger checklist (ops/runbooks/rollback-triggers.md)."
    exit 1
}

Write-Output "DEPLOY VERIFICATION PASSED: $BaseUrl is live, healthy, and fast."
exit 0
