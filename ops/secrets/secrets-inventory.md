# Secret Inventory (P6-03 / EB-74)

> Register of **secret-classified** environment variables. Derived from the canonical matrix (`mds/deployment/environment-configuration.md` §8, built by P5-04/EB-70). **This file records structure, ownership, and cadence only — never values.**

Classification: **Critical** (compromise = full auth takeover), **High** (compromise = data or channel access).

| Variable | Classification | Binding | Where held | Holder | Rotation cadence | Procedure |
|---|---|---|---|---|---|---|
| `JWT_SECRET` | **Critical** | `JwtSecretProvider` (fail-fast, ≥32 bytes, retired-default rejected) | Railway dashboard only | Repo admin | Scheduled (recommended 90 days) or immediately on any suspicion | [rotation.md §1](rotation.md) + [generation record](JWT-GENERATION-RECORD.md) |
| `DATABASE_PASSWORD` | High | `spring.datasource.password` | Supabase (source of truth) → mirrored into Railway | Repo admin | On need / on compromise / when a team member with DB access leaves | [rotation.md §2](rotation.md) |
| `MAIL_PASSWORD` (Gmail app password) | High | `spring.mail.password` | Railway only | Repo admin | On need / on compromise; regenerate in the Google account | [rotation.md §3](rotation.md) |
| `ZOOM_CLIENT_SECRET` | High | `app.zoom.client-secret` | Railway only (after Zoom Marketplace provisioning) | Repo admin | On need; regenerate in the Zoom Marketplace app settings | [rotation.md §4](rotation.md) |
| `SUPABASE_S3_ACCESS_KEY` | High | `supabase.storage.access-key` | Supabase S3 keys → mirrored into Railway | Repo admin | On need; always rotate the pair together | [rotation.md §5](rotation.md) |
| `SUPABASE_S3_SECRET_KEY` | High | `supabase.storage.secret-key` | Supabase S3 keys → mirrored into Railway | Repo admin | On need; always rotate the pair together | [rotation.md §5](rotation.md) |

## Not secret-classified (context)

`DATABASE_URL`, `DATABASE_NAME`, `SUPABASE_URL`, `SUPABASE_S3_ENDPOINT`, `SUPABASE_S3_REGION`, `ZOOM_ACCOUNT_ID`, `ZOOM_CLIENT_ID`, `MAIL_HOST/PORT/USERNAME` — configuration, not credentials (the JDBC URL does not embed the password; username/password are separate variables). Full list: §8 matrix.

## Addition checklist (new secret)

1. Add to `.env.example` with a placeholder + description (guard test 1 asserts parity).
2. Add a row here (classification, holder, cadence, procedure reference).
3. Add to the §8 canonical matrix with class + binding + default.
4. If code-side only, add to `EnvContractGuardTest.DOCUMENTED_CODE_SIDE_VARS`.
5. Decide rotation cadence *at registration time*, not after first compromise.
6. Never paste a real value into any of these files — dashboards and local `.env` only.
