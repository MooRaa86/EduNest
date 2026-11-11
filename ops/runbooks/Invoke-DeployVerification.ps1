# Post-deploy verification job (P6-08 / EB-79 - DEP-01 verification half).
# Gates a deploy on: liveness/readiness UP within budget, FAST aggregate health
# (the P6-08 fix: < AggregateTimeoutSeconds - polled until the new image is live),
# smoke endpoints, optional commit match (soft-warns while /actuator/info is
# admin-only per EB-67). Exit code 0 = green, 1 = red (the fail-path contract;
# rollback triggers: ops/runbooks/rollback-triggers.md).
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File ops/runbooks/Invoke-DeployVerification.ps1 -BaseUrl "https://backend.edunest.dpdns.org"
#   ... -ExpectedCommit "<sha>"                (version match against /actuator/info -> git.commit.id)
#   ... -PollMinutes 20 -PollSeconds 5         (health poll budget; covers a Railway build+deploy window)
#   ... -AggregateWaitMinutes 15               (how long to wait for the aggregate to become fast - i.e. for the new image to take over)

param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl,
    [string]$ExpectedCommit,
    [int]$PollMinutes = 20,
    [int]$PollSeconds = 5,
    [int]$AggregateWaitMinutes = 15,
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
while ((Get-Date) -lt $deadline) {
    try {
        $live = Invoke-RestMethod -Uri "$BaseUrl/actuator/health/liveness" -Method Get -TimeoutSec 15
        $ready = Invoke-RestMethod -Uri "$BaseUrl/actuator/health/readiness" -Method Get -TimeoutSec 15
        if ($live.status -eq "UP" -and $ready.status -eq "UP") { $liveUp = $true; break }
    } catch { } # not up yet - poll again
    Start-Sleep -Seconds $PollSeconds
}
if (-not $liveUp) {
    $fails += "health poll exhausted the ${PollMinutes}-minute budget - container did not reach liveness UP + readiness UP"
} else {
    Write-Output "OK   liveness UP + readiness UP (poll gate)"
}

# ---- Gate 2: aggregate health must be FAST (polled until the new image wins) -
# The fast aggregate doubles as the "new deployment is live" detector: a stale
# container hangs (>5s), the fixed image answers fast. Poll until fast or budget.
if ($liveUp) {
    $aggDeadline = (Get-Date).AddMinutes($AggregateWaitMinutes)
    $aggFast = $false
    $aggSeconds = 0.0
    while ((Get-Date) -lt $aggDeadline) {
        try {
            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            $agg = Invoke-WebRequest -Uri "$BaseUrl/actuator/health" -Method Get -TimeoutSec ($AggregateTimeoutSeconds + 10) -UseBasicParsing
            $sw.Stop()
            if ($agg.StatusCode -eq 200 -and $sw.Elapsed.TotalSeconds -le $AggregateTimeoutSeconds) {
                $aggFast = $true
                $aggSeconds = $sw.Elapsed.TotalSeconds
                break
            }
        } catch { } # stale/hanging container - keep waiting for the new image
        Start-Sleep -Seconds 30
    }
    if ($aggFast) {
        Write-Output ("OK   /actuator/health -> 200 in {0:N1}s (fast-aggregate gate; new image confirmed live)" -f $aggSeconds)
    } else {
        $fails += ("aggregate health never became fast within the {0}-minute wait budget - either the fix posture regressed (management.health.* re-enabled) or the new image never took over; see rollback-triggers.md" -f $AggregateWaitMinutes)
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

# ---- Gate 4: version match (optional; SOFT while /actuator/info is admin-only)
# EB-67 keeps /actuator/info administrator-only, so an unauthenticated CI probe
# gets 401 by design. Until an authorized path exists (public build-marker
# endpoint or a CI-held verify token - posture decision), a 401 is a warning.
if ($ExpectedCommit -and $aggFast) {
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
        $status = $null
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        if ($status -eq 401 -or $status -eq 403) {
            $warns += "version match deferred: /actuator/info is admin-only (EB-67 posture) - authorized commit match lands when a build-marker/token path is decided"
        } else {
            $fails += "version match could not read /actuator/info: $($_.Exception.Message)"
        }
    }
} elseif ($ExpectedCommit) {
    $warns += "version match skipped - the fast-aggregate gate never confirmed a live image"
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
