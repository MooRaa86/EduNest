# File Access Service & Controller – Optimization Report

## 📋 Overview
This document reviews the **`FileAccessService`** and **`FileController`** classes in the EduNest backend, focusing on:
- Database queries (JPA repository usage)
- Path‑handling logic in the controller
- Potential performance bottlenecks
- Recommendations for optimisation (without altering the existing source code).

---

## 🏗️ Current Implementation Snapshot
### `FileAccessService`
| Method | Main DB Calls | Permission Checks |
|--------|---------------|-------------------|
| `authorizeTaskSubmission(Long id)` | `taskSubmissionRepository.findById(id)` | student email equality **OR** mentor email equality |
| `authorizeTask(Long id)` | `taskRepository.findById(id)` & `enrollmentRepository.existsByMentorShip_IdAndStudent_Email(...)` | mentor email equality **OR** enrollment existence |
| `authorizeProject(Long id)` | `projectRepository.findById(id)` & `enrollmentRepository.existsByMentorShip_IdAndStudent_Email(...)` | mentor email equality **OR** enrollment existence |
| `authorizeProjectSubmission(Long id)` | `projectSubmissionRepository.findById(id)` | student email equality **OR** mentor email equality |
| `getCurrentUserEmail()` | *none* – extracts from `SecurityContextHolder` |

### `FileController`
- Retrieves the authorized entity via the service.
- Calls a **single** private method `resolveAndServeFile(String filePath, boolean download)`.
- The method performs:
  1. Null/blank checks.
  2. Null‑byte detection.
  3. Normalisation of backslashes → forward slashes and removal of leading slashes.
  4. Strips the configured `baseDir` prefix.
  5. Constructs a **canonical** `Path` (`resolvedBaseDir.resolve(cleanPath).normalize()`).
  6. Path‑traversal protection (`startsWith(resolvedBaseDir)`).
  7. Directory‑access protection (`Files.isDirectory`).
  8. File existence / readability checks.
  9. Content‑type probing, safe‑inline decision, `Content‑Disposition` building.
 10. Returns a `ResponseEntity<Resource>` with suitable security headers.

---

## 🔎 Query / Database Optimisation Analysis
### 1. **Entity Fetching Overfetches Data**
- All `findById` calls load the **entire entity graph** (`Task`, `Project`, `TaskSubmission`, `ProjectSubmission`).
- For authorisation we only need a **few fields**:
  - IDs are already known.
  - Email of the related `Student`/`Mentor`.
  - `week.id` for the enrolment check.
- Loading the full object tree can trigger **eager fetching** of related collections (e.g., `task.getWeek().getMentorship().getMentor()`), increasing the number of SQL joins and memory consumption.

### 2. **Repeated Calls to `getCurrentUserEmail()`**
- Each authorisation method independently invokes `getCurrentUserEmail()`. The method pulls the authentication object from the `SecurityContextHolder` each time, which is cheap but still incurs a small overhead and reduces readability.

### 3. **Separate Existence Checks (`existsBy…`)**
- The `authorizeTask`/`authorizeProject` methods perform a **second** DB query (`existsByMentorShip_IdAndStudent_Email`).
- This results in **2 queries** per request (one to fetch the entity, another to verify enrolment).
- It could be merged into a **single query** that simultaneously verifies both the entity existence and the enrolment condition, or replaced by a **join‑fetch** projection.

### 4. **Missing Indexes on Frequently Queried Columns**
- `EnrollmentRepository.existsByMentorShip_IdAndStudent_Email` filters on `mentorShip_id` and `student_email`. Without a composite index (`mentor_ship_id`, `student_email`) the DB will perform a full‑table scan for each check.
- Similar for `TaskSubmissionRepository.findById` / `ProjectSubmissionRepository.findById` where the `student.email` is later accessed – indexing the `email` column on the `student` table helps the equality checks.

---

## 🚀 Recommendations & Optimisation Strategies
> **All suggestions are advisory – they do **not** modify the existing source files.**

### A. Use Projections / DTOs for Authorisation Queries
Create lightweight interfaces (or Spring Projections) that only retrieve the columns needed for the check, e.g.:
```java
public interface TaskAuthView {
    Long getId();
    String getStudentEmail();
    String getMentorEmail();
    Long getMentorshipId();
}
```
Then change the repository method:
```java
@Query("SELECT t.id AS id, s.email AS studentEmail, m.email AS mentorEmail, w.mentorship.id AS mentorshipId " +
       "FROM Task t JOIN t.student s JOIN t.week w JOIN w.mentorship ms JOIN ms.mentor m " +
       "WHERE t.id = :taskId")
Optional<TaskAuthView> findAuthViewById(@Param("taskId") Long taskId);
```
This removes the need to load the whole `Task` graph and eliminates the second `existsBy…` query (the mentorship ID is already available for an enrolment check).

### B. Combine Enrolment Verification into a Single Query
If projection is not feasible, add a repository method that checks both conditions in one shot:
```java
@Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END " +
       "FROM Task t LEFT JOIN Enrollment e ON e.mentorShip.id = t.week.mentorship.id " +
       "WHERE t.id = :taskId AND (e.student.email = :email OR t.week.mentorship.mentor.email = :email)")
boolean existsAuthorizedTask(@Param("taskId") Long taskId, @Param("email") String email);
```
The service can then replace the `findById` + `existsBy…` logic with a single `existsAuthorizedTask` call followed by a minimal fetch of the file path only.

### C. Add Composite Indexes
- **Enrollment** table: `(mentor_ship_id, student_email)`.
- **TaskSubmission** and **ProjectSubmission** tables: add an index on `student_email` (if the relation is not already indexed via foreign key).
- **Task** and **Project** tables: index `week_id` and the `mentor.email` column (through a join table or materialised column).
These indexes dramatically reduce query latency for the frequent authorisation checks.

### D. Cache Current User Email per Request
Inject a request‑scoped bean, e.g., `CurrentUserProvider`, that resolves the email once and supplies it to the service methods:
```java
@Service
@RequestScope
public class CurrentUserProvider {
    private final String email;
    public CurrentUserProvider() {
        this.email = SecurityContextHolder.getContext().getAuthentication().getName();
    }
    public String getEmail() { return email; }
}
```
Then replace calls to `getCurrentUserEmail()` with the injected provider. This eliminates repeated `SecurityContextHolder` lookups and makes the code clearer.

### E. Mark Service Methods as Read‑Only Transactions
```java
@Transactional(readOnly = true)
public TaskSubmission authorizeTaskSubmission(Long id) { … }
```
Read‑only transactions avoid unnecessary dirty‑checking and can enable database‑level optimisations (e.g., snapshot isolation).

### F. Reduce Verbose Logging in Production
The controller logs every resolved path and existence check, which can be noisy. Consider switching to `log.debug` for routine path tracing and keep `log.info` only for exceptional events.

### G. Stream File Content Instead of Loading Whole Resource
`UrlResource` loads the file lazily, but the response still buffers the entire file when `ResponseEntity.ok().body(resource)` is used. For large files, switch to `ResponseEntity.ok().body(new InputStreamResource(Files.newInputStream(targetPath)))` to enable **chunked streaming** and lower memory footprint.

### H. Centralise Security Headers
Create a reusable `ResponseEntityBuilder` utility that adds the common security headers (`Cache-Control`, `X-Content-Type-Options`, `Content-Security-Policy`). This reduces duplication and ensures consistent policies.

---

## ✅ Verdict – Is the Current Design “Good Enough”?
- **Security** – The code already guards against path traversal, null‑byte injection, directory access, and unauthenticated users. ✅
- **Performance** – There are **avoidable extra DB round‑trips**, potential over‑fetching, and missing indexes. This can cause latency spikes under load. ⚠️
- **Maintainability** – Repeating `getCurrentUserEmail()` and permission logic across methods increases the risk of inconsistency. A shared provider would improve readability. ⚠️

**Overall rating:** *Adequate for functional correctness but sub‑optimal for high‑traffic production.*

---

## 📌 Actionable Summary for the Development Team
1. **Introduce projection interfaces** for the authorisation queries and refactor the service to use them.  
2. **Create a combined authorisation query** (`existsAuthorizedTask`, `existsAuthorizedProject`) to replace the two‑step pattern.  
3. **Add composite indexes** on `Enrollment` (`mentor_ship_id`, `student_email`) and other frequently filtered columns.  
4. **Inject a request‑scoped `CurrentUserProvider`** to cache the email once per request.  
5. **Annotate service methods with `@Transactional(readOnly = true)`**.  
6. **Switch heavy file responses to streaming (`InputStreamResource`)** for large downloads.  
7. **Adjust logging levels** and centralise security header creation.  

Implementing the above will reduce DB load, lower memory consumption for file transfers, and make the codebase easier to maintain while preserving the existing secure behaviour.

---

*Prepared by Antigravity – your AI coding assistant.*
