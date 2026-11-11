# Staging smoke test (P6-02 / EB-73).
# Verifies a freshly provisioned (or manually redeployed) staging container is healthy:
#   health endpoint UP, Swagger UI reachable, STOMP WebSocket handshake info OK.
# Reads nothing secret; targets the staging base URL only. Exit code 0 = healthy.

param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl
)

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$BaseUrl = $BaseUrl.TrimEnd("/")
$fails = @()

# 1. Liveness + readiness groups (fast, exposed per EB-67; the aggregate
#    /actuator/health includes every indicator and can hang on slow DB checks,
#    so it is reported as a soft warning below, not a failure).
try {
    $live = Invoke-RestMethod -Uri "$BaseUrl/actuator/health/liveness" -Method Get -TimeoutSec 30
    if ($live.status -ne "UP") { $fails += "liveness status was '$($live.status)' (expected UP)" }
    else { Write-Output "OK   /actuator/health/liveness -> UP" }
} catch {
    $fails += "liveness endpoint unreachable: $($_.Exception.Message)"
}
try {
    $ready = Invoke-RestMethod -Uri "$BaseUrl/actuator/health/readiness" -Method Get -TimeoutSec 30
    if ($ready.status -ne "UP") { $fails += "readiness status was '$($ready.status)' (expected UP)" }
    else { Write-Output "OK   /actuator/health/readiness -> UP" }
} catch {
    $fails += "readiness endpoint unreachable: $($_.Exception.Message)"
}

# 2. Swagger UI reachable (public in this deployment)
try {
    $swagger = Invoke-WebRequest -Uri "$BaseUrl/swagger-ui/index.html" -Method Get -TimeoutSec 30 -UseBasicParsing
    if ($swagger.StatusCode -ne 200) { $fails += "swagger-ui returned HTTP $($swagger.StatusCode) (expected 200)" }
    else { Write-Output "OK   /swagger-ui/index.html -> 200" }
} catch {
    $fails += "swagger-ui unreachable: $($_.Exception.Message)"
}

# 3. STOMP handshake info (SockJS info endpoint)
try {
    $ws = Invoke-RestMethod -Uri "$BaseUrl/ws/info" -Method Get -TimeoutSec 30
    if (-not $ws.websocket) { $fails += "ws/info did not advertise websocket support" }
    else { Write-Output "OK   /ws/info -> websocket: true" }
} catch {
    $fails += "ws/info unreachable: $($_.Exception.Message)"
}

# 4. Soft signal: the aggregate health (includes the DB indicator) - a timeout here
#    is worth knowing about (pool/indicator slowness) but is not a staging blocker.
try {
    $healthRaw = Invoke-WebRequest -Uri "$BaseUrl/actuator/health" -Method Get -TimeoutSec 20 -UseBasicParsing
    Write-Output "OK   /actuator/health (aggregate) -> reachable"
} catch {
    Write-Output "WARN /actuator/health (aggregate) not reachable within 20s - typically a slow indicator (DB check); liveness/readiness are the boot-health gates."
}

if ($fails.Count -gt 0) {
    Write-Output "STAGING SMOKE FAILED:"
    $fails | ForEach-Object { Write-Output ("  - " + $_) }
    exit 1
}

Write-Output "STAGING SMOKE PASSED: $BaseUrl is healthy."
exit 0
