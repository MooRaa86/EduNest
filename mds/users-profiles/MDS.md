# Users & Profiles — Primary Domain Document

## 1. Overview

Identity/role model, user lifecycle flags, profile self-service (student/mentor/admin), profile consumption (mentor public profile, mentor→student views, student portfolio/profile), and the skills system backing public profiles.

**Scope:** `entity/users/**`, `repository/users/**` (+11 projection interfaces), `controller/profile/**`, `service/profile/**`, `controller/skill`, `service/skill`, `entity/skill/StudentSkill`, related DTOs.

## 2. Data model (as built)

```text
BaseEntity (id, createdAt, updatedAt, createdBy, updatedBy — audited via AuditorAwareImpl)
└── UserEntity (JOINED inheritance, table users)
    ├── email (unique), password (BCrypt), firstName, lastName, phoneNumber,
    ├── profileImageUrl, enabled=false, deleted=false (soft delete, lines 61–63)
    ├── @ManyToOne(role) Roles{ADMIN|MENTOR|STUDENT} (nullable=false)
    ├── List<SocialMedia> (name ∈ Media enum, url) — cascade ALL, orphanRemoval
    └── List<ChatRoom> rooms
        ├── Student (table students, PK join student_id): educationalLevel enum, address,
        │   jobTitle, bio(500); collections: enrollments(JsonIgnore), reviews,
        │   badgeAwards, Set<StudentSkill> skills
        └── Mentor (table mentors, PK join mentor_id): jobTitle, bio(500),
            yearsOfExperience; List<MentorShip> mentorships (JsonIgnore)
Admin (SEPARATE root entity, table admins): role, email, firstName, lastName,
password, profilePicture, headline — NO enabled/deleted flags
Roles: id + name (plain, no auditing)
StudentSkill (plain class, no BaseEntity): student FK + skill name
```

Repository read-models (verified list): `AuthUserProjection` (logins), `UserRoleProjection`, `UserListProjection` (admin directory, paged), `UserNameProjection` (chat/mail), `UserFullNameProjection` (JWT claims), `MonthlyUsersProjection` (users-per-month chart), `StudentStatsProjection`, `MentorStatsProjection` (**`totalBadges` hard-coded `0`**), `TopMentorProjection` (distinct students + gross revenue), `BadgeProjection`, `AdminBadgeProjection`.

## 3. Profiles — endpoints & rules

| Surface | Controller | Guard | Notes |
|---|---|---|---|
| Student self-view/update, image | `StudentProfileController` (`/student/profile`) | class STUDENT | compose: badges, 10 latest graded projects (hardcoded `PageRequest.of(0,10)`), skills, certificates (first page of 10); social links upsert; skill-set diff-sync; image saved to `uploads/student/` via `ImageStorageService.saveImage("student", ...)` |
| Mentor self-view/update, image | `MentorProfileInfoController` (`/mentor/profile`) | class MENTOR (+ service-level) | strict `UpdateMentorProfileRequest` (`ignoreUnknown=false`); touches UserEntity + Mentor; image → `uploads/mentor/` |
| Mentor **public** profile | `MentorProfileForStudent` (`/api/v1/profile/mentor/**`) | **none (public)** | constructor-projection query (counts + AVG rating + bio), paginated mentorships/reviews; exposes mentor **email** as identifier (privacy note) |
| Mentor views **student** | `MentorViewStudentProfileController` (`/profile/students/**`) | class MENTOR + `ProfileService#validateMentorHasAccessToStudent` (must have ≥1 enrollment of that student) | KPIs (active/completed), progress list, projects, full-profile composition; `status` filter param **ignored** (param dropped — suspected bug) |
| Admin self-profile/settings | `AdminProfileAndSettingsController` (`/admin/**`) | URL + class ADMIN | name/email/password/image, OTP email change; commission read/set |

## 4. Data visibility & privacy

- Public mentor profile: names, bio, counts, reviews, **email**.
- Public reviews: reviewer **emails** + images (see [../mentorship-flow/explore-and-homepage.md](../mentorship-flow/explore-and-homepage.md)).
- Mentor→student profile: names, bio, address, social links, points, project artifacts — legitimate, mentor-gated by enrollment drastically reduces scope; ownership check verified.
- Image uploads via `ImageStorageService`: content-type **header** trust + no magic-byte validation for covers/profile (contrast: `FileStorageService`/Tika for submissions) — [../file-storage/MDS.md](../file-storage/MDS.md).

## 5. Known Issues (IDs in ../ISSUES.md)

- **USR-01 (Medium):** `ProfileService#getStudentProjects` ignores the `status` request param (dropped — filter silently broken).
- **USR-02 (Low):** user list/leaderboard surfaces expose emails to broader audiences than necessary (admin directory admin-gated ✓; mentorship leaderboard guard disabled — MSH-03).
- **USR-03 (Low):** `MentorStatsProjection.totalBadges` hard-coded `0`; admin "students count" counts enrollments (double-counts repeat students; `TopMentorProjection` uses DISTINCT — inconsistent).
- **USR-04 (Low):** Student/KPI compositions hardcode page 0 size 10 caps (profile cutting off data arbitrarily).
- **USR-05 (Medium, xref = NOTIF-04):** `AdminRepository#findTopByOrderByCreatedAtDesc` — all admin-targeted notifications go to the **newest** admin only (`NotificationService#sendToAdmin`).

## 6. Open Questions

- Are student profiles intended to be **public** (portfolio showcase) or only mentor/cohort-visible? Currently: student profile endpoints require authentication; public mentor profiles only. Confirm product intent.
- Should deleting/deactivating users hide their reviews/leaderboard entries? (Soft-delete flag exists but is not applied in read queries — e.g., leaderboard queries don't filter `deleted`.)

## 7. Related Documentation

[profiles.md](profiles.md) (endpoint deep-dive) · [../database/MDS.md](../database/MDS.md) (model) · [../file-storage/MDS.md](../file-storage/MDS.md) (image rules) · [../ISSUES.md](../ISSUES.md)
