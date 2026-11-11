# Rollback Triggers (P6-08 / EB-79)

> What happens when a deploy fails verification. Built by P6-08; the *drill* that exercises it is P7-01 per plan. Companion to [`Invoke-DeployVerification.ps1`](Invoke-DeployVerification.ps1) (the red/green verdict) and [`../backups/restore-procedure.md`](../backups/restore-procedure.md) (data recovery).

## The verdict → action map

| Verification failure | Meaning | Triggered action |
|---|---|---|
| Health poll exhausts budget (container never UP) | Boot crash or failed startup (bad config var, broken migration, missing secret) | 1. Railway → Deployments → **Redeploy the previous deployment** (one-click; Railway keeps history). 2. Read the failed deployment's logs first — if a migration ran and broke boot, see the DB row below before retrying. |
| Aggregate health slow/missing while liveness/readiness UP | The P6-08 fast-aggregate posture regressed (a hang-capable indicator re-enabled) | Do **not** roll back for this alone; investigate the `management.health.*` posture (guard test should have caught it). Roll back only if other gates also fail. |
| Swagger / WS smoke fail while health is UP | Partial runtime failure (routing, static resources, WS broker) | Redeploy previous deployment if it persists after one investigation cycle; otherwise fix-forward with a targeted commit. |
| Version match fails | Railway deployed a different commit than the verified push (queued-push race) | Re-run verification with the deployed commit as `-ExpectedCommit`; if truly wrong build, redeploy the intended commit. Not a rollback trigger by itself. |
| Boot crash **after a migration ran** | Schema/app mismatch (forward-only violation, bad `V{N}`) | **DB is NOT rolled back** (forward-only rule). Two paths: (a) hotfix-forward with a `V{N+1}` script, or (b) if the data itself is damaged, follow the restore decision table in [`../backups/backup-policy.md`](../backups/backup-policy.md) (PO sign-off required for full restore). The pre/post-migration boundary comes from the P3-07 rehearsal artifacts. |
| Boot crash after a config/secret change | Bad variable (wrong secret, typo, missing var) | Revert the variable to the previous value (`ops/secrets/rotation.md` rollback lane); the app boots fail-fast on bad secrets by design. |

## Non-negotiables

1. **Rollback = container-level.** The database is never taken backward (forward-only migrations, `ops/environments.md` §7.2); data recovery goes through the restore procedure, not through schema rollback.
2. **One investigation cycle before container rollback** when health is UP but a smoke gate fails — rolling back a healthy container for a cosmetic failure hides the real signal.
3. **Every rollback event gets a ticket-thread record:** what failed, which gate, what was redeployed, outcome. Rollback history is P7-01 drill input.
4. **Verification runs after every rollback too** — a rollback that doesn't re-verify is just a different outage.

## Fail-path evidence (the plan's test)

The verify script's fail path is exercised by running it against a deliberately failing target: the script must exit **1** with a gate report (dry-run log recorded on the EB-79 ticket). Any change to the script must keep that contract: **exit 0 only when every enabled gate passed.**
