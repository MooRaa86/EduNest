# EB-115 production deployment pipeline — work in progress

**Status: NOT IMPLEMENTED END-TO-END; NOT ACTIVATED.** When explicitly enabled
on a production push, the workflow can verify the release HEAD, test and build
the commit, publish and inspect its GHCR digest, and rehearse Flyway on isolated
PostgreSQL. The protected production preflight deliberately fails before
accessing production credentials. It cannot migrate or deploy. Railway's existing
independent deployment path is not controlled by this workflow.

## Isolated work completed

- `scripts/cicd/production_identity.py` validates an allowlisted JDBC URL and
  user form without printing credentials. This is syntax validation, not proof
  of the identity of the connected database.
- `scripts/cicd/production_gate.py` contains pure release, image, migration-state
  and deployment-result decisions and a fake-adapter sequencing simulation.
  It is not connected to Flyway, GitHub or Railway and is not a deploy runner.
- `scripts/cicd/flyway_adapter.py` executes Flyway Maven info/validate/migrate
  with captured output. Its rehearsal-only integration test ran against a
  disposable PostgreSQL 17 container; the workflow runs the same test against
  its disposable PostgreSQL service. It also rejects a deliberately invalid
  migration in a temporary copy. No production identity or attestation adapter
  is connected to it; stdout/stderr are withheld to avoid exposing credentials.
- GitHub production-HEAD lookup, Maven tests, image build/push and GHCR digest
  inspection are implemented in the workflow, but have not been exercised by
  an enabled production workflow run. The digest is taken from the build step,
  and the pulled image's OCI revision must match the release SHA.
- `Invoke-DeployVerification.ps1 -StrictIdentity` is a preliminary stricter
  commit check; an authorized runtime identity mechanism has not been wired.
- `production_attestation.py` verifies a detached signature and checks release,
  target, freshness, and backup/compatibility claims. Tests generate disposable
  signing keys. A trusted signer/key provisioning and evidence source are not
  configured; a valid signature alone is not proof of a usable backup.
- `railway_adapter.py` implements the documented project-token identity query
  and deployment-list ID/status checks. Its immutable-image mutation interface
  always fails before issuing a provider command. The protected workflow invokes
  this hard stop; neither the evidence verifier nor the production Flyway or
  Railway read-side adapters are wired to live infrastructure.

## Railway provider contract findings

- The official [`service source connect`](https://docs.railway.com/cli/service)
  documentation supports `--image` and project/service/environment selectors;
  it illustrates a tag, not a verified digest deployment.
- The official [`redeploy`](https://docs.railway.com/cli/redeploy) documentation
  supports `--service`, `--yes`, and `--json`, but does not specify a JSON schema
  establishing the newly created deployment ID. The staging workflow also uses
  additional CLI flags checked against a historical CLI version; its response
  parsing is not proof of a production contract.
- The official [`deployment list`](https://docs.railway.com/cli/deployment)
  documentation exposes IDs, statuses and `--json`; a `SUCCESS` for a selected
  ID establishes terminal status, not that the intended digest actually ran.
- The [`projectToken` query](https://docs.railway.com/reference/public-api)
  documents project/environment identity and the `Project-Access-Token` header.
  GraphQL `errors` can occur in HTTP 200 and must be rejected.
- **Unresolved:** a verified provider contract that correlates the mutation's
  actual deployment ID with the actually deployed immutable digest. Do not
  perform the mutation then guess an ID from the latest deployment. Any timeout
  after a future mutation is an unknown outcome, not proof of no change.

## Blockers before production use

Establish a protected signer, provision and independently verify release-bound
backup and compatibility attestations; integrate them with production Flyway
orchestration, durable cross-system
release serialization, Railway mutation and tracked
deployment adapters, strict runtime identity, and failure evidence. Confirm the
Railway provider's actual deployment-ID and digest response contract without
inferring a successful rollout from existing health. A database-side identity
check and secure credential handling must accompany the syntactic guard.

Before any later activation: confirm production Flyway history, including the
V14 history discrepancy recorded on EB-114; verify backups and old-image schema
compatibility; separately authorize and complete Railway Auto Deploy cutover and
control all other deployment paths. Do not auto-baseline, repair, clean, undo or
roll back schema. A failed or untrackable deployment call can already have caused
an external mutation; treat its outcome as unknown and investigate.

The current manual production release and migration runbook remains in force.
The preparation workflow has not been run with activation enabled, and no CI
run on this branch is available until a reviewable commit is pushed.
