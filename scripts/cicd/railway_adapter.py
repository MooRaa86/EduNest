"""Railway read-side adapter; mutation deliberately unavailable.

Railway documents projectToken identity and deployment list id/status, but the
currently published CLI documentation does not establish a mutation response
that binds a newly created deployment ID to the immutable image digest.
"""
import json
import subprocess
from urllib.request import Request, urlopen

from production_gate import GateFailure


def read_project_token(token, opener=urlopen):
    """Read documented projectToken identity without emitting the token."""
    if not token:
        raise GateFailure("Railway project token missing")
    body = json.dumps({"query": "query { projectToken { projectId environmentId } }"}).encode()
    request = Request("https://backboard.railway.com/graphql/v2", data=body,
                      headers={"Project-Access-Token": token, "Content-Type": "application/json"}, method="POST")
    try:
        with opener(request, timeout=15) as response:
            if response.status != 200:
                raise ValueError("unexpected HTTP status")
            result = json.load(response)
            if not isinstance(result, dict) or result.get("errors"):
                raise ValueError("GraphQL failure")
            return result
    except Exception:
        raise GateFailure("Railway identity lookup failed") from None


def verify_project_token(response, project, environment):
    if not isinstance(response, dict) or response.get("errors"):
        raise GateFailure("Railway identity unverified")
    data = response.get("data")
    identity = data.get("projectToken") if isinstance(data, dict) else None
    if (not project or not environment or not isinstance(identity, dict)
            or identity.get("projectId") != project or identity.get("environmentId") != environment):
        raise GateFailure("Railway project or environment mismatch")
    return True


def deployment_status(rows, deployment_id):
    if not isinstance(rows, list) or not isinstance(deployment_id, str) or not deployment_id:
        raise GateFailure("Railway deployment unverified")
    matches = [row for row in rows if isinstance(row, dict) and row.get("id") == deployment_id]
    if len(matches) != 1:
        raise GateFailure("Railway deployment ID missing or ambiguous")
    status = matches[0].get("status")
    if status != "SUCCESS":
        raise GateFailure("Railway deployment not confirmed successful")
    # This proves terminal status only, not the actually deployed image digest.
    return status


def list_deployments(service, environment, env, runner=subprocess.run):
    """Read a pre-linked project's documented CLI JSON listing, without logs."""
    if not service or not environment or not env.get("RAILWAY_TOKEN"):
        raise GateFailure("Railway configuration missing")
    try:
        result = runner(["railway", "deployment", "list", "--service", service,
                         "--environment", environment, "--limit", "100", "--json"],
                        env=env, capture_output=True, text=True, timeout=30, check=False)
        if result.returncode != 0:
            raise ValueError("CLI failed")
        rows = json.loads(result.stdout)
        if not isinstance(rows, list):
            raise ValueError("unexpected response")
        return rows
    except (OSError, ValueError, subprocess.TimeoutExpired):
        raise GateFailure("Railway deployment listing unavailable") from None


def deploy_immutable_image(*_args, **_kwargs):
    # No live call is safe until a documented/tested provider contract binds
    # the created deployment ID and actually deployed digest. Do not try a
    # source change and then guess which deployment belongs to this run.
    raise GateFailure("Railway immutable-image deployment identity contract unverified; no mutation attempted")


try:
    from serving_observer import RailwayServingImageObserver, ServingImageObservation, ServingImageObserver
except ImportError:
    pass


if __name__ == "__main__":
    import sys
    try:
        deploy_immutable_image()
    except GateFailure as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
