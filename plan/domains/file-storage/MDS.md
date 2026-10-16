# Domain: File Storage — Implementation Guide

Canonical audit sources: [`mds/file-storage/MDS.md`](../../../mds/file-storage/MDS.md), LEARN-03 context in [`mds/learning-content/tasks-workflow.md`](../../../mds/learning-content/tasks-workflow.md), upload policy entries in [`mds/authentication-security/authorization-rules.md`](../../../mds/authentication-security/authorization-rules.md) (G9/G14).

## 1. Purpose & components owned

Local-disk uploads under `uploads/**` (cwd-relative, `${app.upload.base-dir}`), two storage services with different validation rigor (`FileStorageService` with Tika/MIME allowlist; `ImageStorageService` without detection), `FileController` (typed `/files/**` + legacy `GET /files?filePath=`), `FileAccessService` ownership checks, static serving via **both** `ProjectSecurityProdconfig` chain rules and `WebConfig#addResourceHandlers`.

## 2. Review checklist (mandatory for any PR here)

- [ ] Any new route/surface appears in **both** security chain + resource-handler policies (or is private-only).
- [ ] Content type decided by detected magic bytes (Tika), never by client header or filename extension ([P1-22](../../phases/phase-01-security-stabilization/tasks.md#p1-22) pattern).
- [ ] Ownership checks for private categories via `FileAccessService`; public-category list is a single constant set.
- [ ] Path traversal: canonicalization + containment test present ([P1-20](../../phases/phase-01-security-stabilization/tasks.md#p1-20) keeps existing suite green).
- [ ] Replace/delete ordering: DB commit → afterCommit delete of the old file (P3-03 pattern).

## 3. Implementation requirements (per canonical issue)

1. **FILE-01 (Critical):** public/private split — public = `mentorship-cover-images`, `student`, `mentor`, `chatroom` folders per **D-07**; private = `submissions`, `task-attachment`, `project-attachment`; both chain *and* `WebConfig` constrained identically; submissions reachable only via ownership-checked `/files/**` variants. Task [P1-21](../../phases/phase-01-security-stabilization/tasks.md#p1-21). FE link change for submissions is expected (coordination note required).
2. **FILE-02/03/04 + MSH-06:** Tika magic-byte validation on images, per-surface MIME allowlists, extension from detection, zip octet-stream tightened — [P1-22](../../phases/phase-01-security-stabilization/tasks.md#p1-22) using the **P0-06/P0-07 verdict matrix**.
3. **LEARN-03/G9:** generic `GET /files?filePath=` routed through `FileAccessService` allowlist + ownership branch; retire when FE confirms ([P1-20](../../phases/phase-01-security-stabilization/tasks.md#p1-20), D-06 default guard-first).
4. **FILE-05:** replace-before-delete ordering → afterCommit deletion ([P3-03](../../phases/phase-03-database-integrity/tasks.md#p3-03)).
5. **FILE-06/DEP-06 (D-19):** uploads purged from git history + guards — [P6-06](../../phases/phase-06-environment-and-deployment-automation/tasks.md#p6-06) (infra task in Phase 6; domain responsibity listed here for consistency).
6. **FILE-07/DEP-03:** persistent volume baseline + conditional object storage adapter ([P6-05](../../phases/phase-06-environment-and-deployment-automation/tasks.md#p6-05), [P6-07](../../phases/phase-06-environment-and-deployment-automation/tasks.md#p6-07) conditional per D-25).
7. **TD-09/G14:** folder-name/policy mismatch resolved via constants (no renames) inside P1-21.

## 4. Definition of done (domain)

FILE-* rows on [`ISSUE-MAPPING.md`](../../ISSUE-MAPPING.md) Verified; P0-06 matrix re-run clean; staging file-access checks (P7-02/03 file rows) green; `mds/file-storage/MDS.md` reflects the final public/private policy table.
