# Live Sessions & Attendance — Primary Domain Document

## 1. Overview

Mentors schedule sessions inside curriculum weeks; each session is a **public Jitsi Meet room** whose URL becomes available only through this backend; attendance is mentor-driven via manual snapshot posting; ending a session compiles attendance percentages, awards a flat 5 points to attendees, and notifies everyone.

**Business value:** real-time teaching moments, attendance-based engagement points feeding the leaderboard.

## 2. Current Implementation

### 2.1 Entities & enums
- `entity/livesession/Session` (table `sessions`, index `idx_sessions_week_id`): `title`, `scheduledAt`, `actualStartTime`, `actualEndTime`, `meetingUrl`, `status` (`enums/livesession/SessionStatus`: `SCHEDULED, LIVE, ENDED, CANCELLED`), `week` FK; `@OneToMany` `attendances`, `attendanceResults` (cascade ALL).
- `entity/livesession/SessionAttendance` (session_id, student_id, `snapshotTime`) — raw snapshots; **no unique constraint** on (session, student) — repeats by design.
- `entity/livesession/SessionAttendanceResult` (`attended` boolean) — one final row per (session, student) written at end.
- No status enum on lectures/others; sessions are the only content with a runtime state machine (manual).

### 2.2 Jitsi integration — reality check

`service/livesession/JitsiService` is **15 lines**: `createRoomLink(sessionId)` → `https://meet.jit.si/EduNest_Session_{id}_{UUID}`.

- **No JWT, no token, no secret, no configured domain** (no Jitsi properties anywhere in `application*.properties`; no jitsi JWT library usage).
- README/books claim JWT-protected Jitsi rooms — **this does not exist in code** (DOC issue).
- Security therefore = "the URL is distributed only to enrolled students via `joinSession`"; anyone who obtains the URL has a public room (meet.jit.si is intentional demo-grade).

### 2.3 Lifecycle (`service/livesession/LiveSessionServiceImp`)

```mermaid
stateDiagram-v2
  [*] --> SCHEDULED: createSession (mentor, rejects past schedule, notify mentorship)
  SCHEDULED --> LIVE: startLiveSession (mentor-only, rejects before scheduledAt, generates Jitsi URL, notifies)
  LIVE --> ENDED: endSession (mentor-only → attendance calc + 5-point awards + notifications)
  SCHEDULED --> [*]: deleteSession (blocked if LIVE)
```

- `joinSession` — mentor-or-enrolled-student audience check (`AuthorizationRepository.isMentorOwnLiveSession` / `isStudentEnrolledByLiveSessionId`); rejects non-LIVE/null URL. **No join-time attendance row** — attendance is snapshot-driven.
- `updateSession` — requires date+time **together**; notifies students.
- **No `@Scheduled` jobs exist for sessions** (the only scheduler in the app is OTP cleanup) — status changes are fully manual; a forgotten ENDED state is possible forever.

### 2.4 Attendance math (`getSessionAttendance` + `calculateAttendancePoints`, lines ~394–481)

- `sessionDuration = minutes(actualStart, actualEnd)`
- `totalSnapshots = max(1, duration/5)` — assumes a snapshot **every 5 minutes**
- `percentage = snapshotsTaken * 100.0 / totalSnapshots`
- `Present ⇔ percentage ≥ 75` (arbitrary constant)
- `endSession` then: writes `SessionAttendanceResult` for **every** enrolled student (default Absent), **+5 points** for each Present via `applyDelta`, one notification per student (loop; per-student 2 extra queries + a STOMP push — N+1 pattern).
- **Percentages can exceed 100** (snapshots not rate-limited to the 5-minute grid), and a mentor who never posts snapshots marks everyone Absent (0 points) regardless of actual presence.

### 2.5 Snapshot ingestion (`recordSnapshot`)

Mentor-only; validates each provided student id against enrollment, then inserts one `SessionAttendance` row per id **in a loop of `save()`** (no batch). No dedupe window — the math is snapshot-count-based, so duplicates inflate.

## 3. Interfaces & Contracts (summary)

Controller `/api/v1/liveSession/**` — endpoints: create/update/list/delete (MENTOR), start/join/end/snapshots/attendance (MENTOR), student attendance + upcoming (STUDENT), `getSession` for both roles with audience checks. DTO sets: `CreateSessionDto`/`UpdateSessionDto` (`@NotBlank title`, date/time validation), `SessionResponseDto`, `AttendanceResponse{attendancePercentage, status}`, upcoming-session variants for student/mentor, dashboard variant. Full paths: [../api/endpoints.md](../api/endpoints.md).

## 4. Security & Validation

- Audience checks verified: mentor-own-week/session, student enrolled (per-session). `getSession` relies on service membership check rather than annotation (documented).
- Meeting URL exposure is inherent to the design (no tokened Jitsi access) — documented as accepted risk/open question.

## 5. Error Handling & Edge Cases

- Starting before `scheduledAt` rejected; starting a LIVE/ENDED rejected; joining a SCHEDULED/ENDED session rejected.
- End → ENDED is idempotent-guarded (status check); a second end call rejected.
- No cleanup job for forever-SCHEDULED stale sessions.

## 6. Testing

Zero tests. Recommended: lifecycle transitions matrix; attendance math (snapshot grid vs manual; overflow >100%); enrollment audience; point awarding exactly once per end (status guard).

## 7. Known Issues (IDs in ../ISSUES.md)

- **LS-01 (High):** docs-vs-code mismatch — Jitsi JWT protection does not exist (public rooms).
- **LS-02 (Medium):** attendance denominator assumptions; >100% percentages; snapshotless end marks everyone Absent (award semantics).
- **LS-03 (Medium):** no scheduled auto-lifecycle; sessions expire/leak stale statuses.
- **LS-04 (Low):** per-row snapshot saves (no batching); per-student notification loop (N+1 + pushstorm).
- **LS-05 (Low):** missing `@Transactional` on `startLiveSession`/`recordSnapshot` (inconsistent transactional style).

## 8. Open Questions

- Is `meet.jit.si` acceptable for production, or is a self-hosted Jitsi (with app-issued JWT) planned? (Decide before stabilization: integration doc + secret handling.)
- Was automated attendance (join-time markers) intended? Current design is mentor-driven only.
- Confirm the 5-point award & 75% threshold as product constants (hardcoded).

## 9. Related Documentation

[../integrations/MDS.md](../integrations/MDS.md) (external-settlement detail) · [../learning-content/my-learning-and-achievements.md](../learning-content/my-learning-and-achievements.md) (points) · [../ISSUES.md](../ISSUES.md)

## 10. Implemented Platform Change — Zoom Integration (EB-66 / LS-01)

- **Zoom replaces Jitsi** as primary live session platform (decisions D-16 & D-29).
- Implemented via `HttpZoomMeetingClient` with Server-to-Server (S2S) OAuth token caching and 60-second expiration buffer.
- Host URL vs Join URL separation: Mentors receive Zoom `start_url` with host ZAK credentials (`hostUrl`); students receive only sanitized `meetingUrl` (`join_url`).
- 40-minute duration cap enforced on meeting creation per D-29 Basic plan constraints.
- Jitsi retained as fallback when `app.zoom.enabled=false`.

## 11. Auto-Lifecycle Housekeeping & Dormant Attendance (EB-57 / LS-02, LS-03, LS-05, D-15)

### 11.1 Auto-Close Scheduled Sweep
- **Service:** `LiveSessionSweepService` annotated with `@Scheduled(fixedDelayString = "${app.livesession.auto-close.sweep-rate-ms:300000}")`.
- **Sweep Rate:** Every 5 minutes (`300000ms`, user-adjusted to prevent DB overhead).
- **Grace Window:** 10 minutes (`grace-minutes: 10`) after the 40-minute max duration (`max-duration-minutes: 40`).
- **Expired LIVE sessions:** Any session in `LIVE` status where `now > actualStartTime + 50m` is transitioned to `ENDED`, setting `actualEndTime = now`, and signaling Zoom cloud via `endMeeting`.
- **Abandoned SCHEDULED sessions:** Any session in `SCHEDULED` status where `now > scheduledAt + 50m` without starting is transitioned to `CANCELLED`, removing the meeting from Zoom cloud via `deleteMeeting`.
- **Audience Guards:** Attempts to start, join, or update a `CANCELLED` session are rejected with clear error messages.
- **Snapshot Batching & Transactions:** `recordSnapshot` and `startLiveSession` are protected by `@Transactional` (LS-05); attendee snapshots are persisted using `attendanceRepository.saveAll(...)` (LS-04).

### 11.2 Dormant Attendance Strategy (Decision D-15)
To prevent Zoom's 40-minute cloud truncation from corrupting student points under the Basic plan, attendance enforcement and gamification point awarding are **suspended/dormant** in the active production flow, while preserving the full snapshot grid strategy:

| Component | Dormant State (`enforced=false`, default) | Active State (`enforced=true`) |
| :--- | :--- | :--- |
| **Attendance Policy** | `NoEnforcementAttendancePolicy` (logs notice, records no absence verdicts) | `SnapshotGridAttendancePolicy` (75% threshold, 5-min snapshot grid) |
| **Points Awarder** | `DormantPointsAwarder` (no-op, zero leaderboard or gamification writes) | `DefaultLivePointsAwarder` (+5 points via `TotalPointsService`, sends notifications) |

### 11.3 Activation Procedure (Zero Refactoring Required)
When the organization upgrades the Zoom account or decides to enable attendance scoring:
1. Set `APP_LIVESESSION_ATTENDANCE_ENFORCED=true` (or `app.livesession.attendance.enforced=true`).
2. Spring Boot automatically switches bean injection from the dormant policies to `SnapshotGridAttendancePolicy` and `DefaultLivePointsAwarder`.
3. All subsequent session completions will automatically calculate attendance percentages, persist `SessionAttendanceResult` rows, award +5 bonus points, and notify students.
