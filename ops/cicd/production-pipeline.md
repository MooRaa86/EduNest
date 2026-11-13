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
- `scripts/cicd/database_identity.py` implements `DatabaseIdentityVerifier`,
  `DatabaseIdentityPolicy`, and `DatabaseIdentityReport`. It enforces TLS
  verification (`sslmode=verify-full`), checks connected database, schema, user,
  and cluster `system_identifier` via `pg_control_system()`, computes cryptographic
  identity fingerprints, and rechecks identity immediately before any migration.
  Tests use dependency injection and mocked sessions without touching production.
- `scripts/cicd/replay_store.py` defines the atomic consume-once `ReplayStore`
  contract with expiry enforcement, duplicate rejection, and fail-closed error
  handling. It implements `InMemoryReplayStore`, `HmacDurableReplayStore` (with
  tamper-evident HMAC-SHA256 signatures, atomic writes, and file locking),
  `RemoteHttpReplayStore` (for multi-runner CI persistence), and `DatabaseReplayStore`.
- `scripts/cicd/serving_observer.py` implements `RailwayServingImageObserver`,
  verifying Railway project and environment identity via documented projectToken
  GraphQL, extracting the exact immutable image digest from deployment metadata,
  enforcing a <= 60s freshness window, and rechecking serving identity immediately
  before future migrations.
- `scripts/cicd/production_gate.py` contains pure release, image, migration-state
  and deployment-result decisions and a fake-adapter sequencing simulation.
  It is not connected to Flyway, GitHub or Railway and is not a deploy runner.
- `scripts/cicd/flyway_adapter.py` runs Flyway info/validate with captured output
  and checks report boundaries and release migration manifest coverage. Direct
  `FlywayAdapter.migrate()` and `migrate_production()` refuse all mutation.
  The test-only `RehearsalFlywayAdapter` rechecks an owned container before
  migrating. No production identity or attestation adapter is connected to it.
- GitHub production-HEAD lookup, Maven tests, image build/push and GHCR digest
  inspection are implemented in the workflow, but have not been exercised by
  an enabled production workflow run. The digest is taken from the build step,
  and the pulled image's OCI revision must match the release SHA.
- `Invoke-DeployVerification.ps1 -StrictIdentity` is a preliminary stricter
  commit check; an authorized runtime identity mechanism has not been wired.
- `production_attestation.py` verifies a detached signature and checks release,
  attempt, target, migration set, freshness, and backup/compatibility claims.
  It also requires an independently implemented serving-image observer and an
  atomic replay store; neither exists for production. Tests use disposable keys
  and fake observers/stores. A signature alone is not proof of a usable backup.
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
The preparation workflow has not been run with activation enabled.

## Trust, freshness and action maintenance

- Before any production integration, provision a protected signer and public
  key independently of release inputs. Pin and audit the signer identity and
  key fingerprint in protected configuration; restrict key administration and
  signing access. Rotate by provisioning a new trusted key with a bounded
  overlap; immediately revoke compromised keys and reject their outstanding
  attestations. No key is currently provisioned by this repository.
- Attestations bind the release SHA, target, unique release attempt and pending
  migration set. Backup claims include a creation timestamp and integrity hash;
  compatibility claims include the previous digest. A durable, tamper-resistant
  atomic store must reject reused attempt/kind pairs, including across runners.
  The serving-digest observer must authenticate the provider and return a fresh
  identity; it must be checked again immediately before a future migration. No
  production implementation of the observer or replay store currently exists.
- The protected workflow hard stop remains in place. The Python interfaces are
  not a substitute for independent database credentials, provider access
  restrictions or a cross-system deployment lock.
- GitHub Actions are pinned to immutable commit SHAs. Update by verifying a new
  upstream release/tag-to-commit mapping, reviewing its changes, updating pins in
  one PR and rerunning actionlint and the isolated security workflow.
