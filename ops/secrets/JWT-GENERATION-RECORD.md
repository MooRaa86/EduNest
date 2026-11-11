# JWT_SECRET Generation Record (P6-03 / EB-74)

> Provenance register for the `JWT_SECRET` (HS256 signing key, **Critical** classification). **Values are never recorded here** — only generation metadata. The value of record lives solely in the Railway dashboard. Companion to [rotation.md §1](rotation.md).

## Record

| # | Generated (UTC) | By | Method | Length | Held in | Rotation trigger | Notes |
|---|---|---|---|---|---|---|---|
| 1 | 2026-10 (during Railway production provisioning, exact date to be confirmed by repo admin) | Omar (repo admin) | Manually generated random value (≥32 bytes) | ≥32 bytes | Railway dashboard only | Initial production provisioning (P6-01 / EB-72 era) | Accepted by the fail-fast `JwtSecretProvider`; app boots healthy in production — proves injection works. Provenance row to be date-confirmed by the holder. |

## Row template (append on every rotation)

```
| <n> | <YYYY-MM-DD UTC> | <name> | new-jwt-secret.ps1/.sh or <method> | <bytes> | Railway dashboard only | <scheduled 90d / compromise / incident ref> | <notes> |
```

## Rules

1. Record a new row **on every rotation** (rotation.md §1, step 5). No value, ever.
2. The generation method must be cryptographically random — use `new-jwt-secret.ps1` / `new-jwt-secret.sh`, or an equivalent CSPRNG; never a passphrase, dictionary word, or hand-typed string.
3. Minimum length: 32 bytes (the provider refuses shorter; base64 output of the scripts is 48 bytes of entropy).
4. The retired hardcoded default (pre-SEL-01) is rejected by hash-match — if a generated secret ever equals it (impossible in practice), the app refuses to boot.
5. Sessions: rotating invalidates all tokens → the re-auth window is expected and documented, not an incident.
