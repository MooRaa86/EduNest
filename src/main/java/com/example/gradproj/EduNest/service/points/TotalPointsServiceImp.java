package com.example.gradproj.EduNest.service.points;

import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EB-51 / GAME-04 — atomic ledger writer.
 *
 * applyDelta is one statement per branch, never a read-modify-write of a
 * managed entity:
 *
 *  - row present  -> {@link TotalPointsRepository#incrementIfPresent} (JPQL
 *    UPDATE with a CASE floor taken by its own row lock; the Java-side
 *    SELECT FOR UPDATE hop is gone, so no two writers can interleave a
 *    between-select-and-apply window);
 *  - row absent   -> on PostgreSQL the validated one-shot
 *    {@link TotalPointsRepository#upsertDeltaRowPostgres} (ON CONFLICT
 *    upsert; a concurrent uncommitted insert takes the DO UPDATE branch with
 *    committed values, so no 23505 can ever abort the caller's transaction),
 *    on the no-Docker H2 MySQL-mode test profile a plain entity insert
 *    (single-threaded flows there; a concurrent absent-row race on H2 is
 *    documented test-only limitation, no constraint-catching/retry exists
 *    anywhere — decision D-09 / GAME cage rules).
 *
 * Clamp contract (GAME-03/CERT-04): the ledger floors at zero on every
 * branch — first insert floors {@code GREATEST(0, delta)}, absent-row insert
 * floors for H2, present-row update clamps. Nothing downstream can see a
 * negative total.
 *
 * Caller cursor (shared lock hierarchy, EB-51): applyDelta must always be
 * the LAST ledger step of a gate that already opened the higher-rank shared
 * locks (L0 mentorship / L1 week / L2 content — content, submission & badge
 * gate -> L4 here). Content-axis flows never take L0, badge/completion flows
 * never take L1/L2: so arriving writers can only ever converge here, never
 * cycle. The zero-delta short-circuit is preserved for cheap idempotent
 * callers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TotalPointsServiceImp {
    private final TotalPointsRepository totalPointsRepository;
    private final Environment environment;

    /** Cached dialect probe: settings list keeps @RefreshScope-free laziness simple. */
    private volatile Boolean postgresBackend;

    @Transactional
    public void applyDelta(Student student, MentorShip mentorship, int delta) {
        if (delta == 0) return;

        String actorEmail = resolveActorEmail(student);

        int updated = totalPointsRepository.incrementIfPresent(
                student.getId(), mentorship.getId(), delta, actorEmail);

        if (updated > 0) return; // row existed; single-statement atomic add is complete

        if (isPostgresBackend()) {
            totalPointsRepository.upsertDeltaRowPostgres(
                    student.getId(), mentorship.getId(), delta, actorEmail);
        } else {
            // H2 (test profile): first row is created through the entity where
            // BaseTime fill-in and audit fields behave exactly like enrollment
            // scaffolding. The CREATE TABLE default floor (total_points >= 0
            // documented policy) is applied in the builder.
            int floored = Math.max(0, delta);
            log.debug("EB-51 absent-row insert on non-PostgreSQL backend ({}, ms={}, delta={} clamped to {})",
                    student.getId(), mentorship.getId(), delta, floored);
            totalPointsRepository.save(TotalPoints.builder()
                    .student(student)
                    .mentorship(mentorship)
                    .totalPoints(floored)
                    .build());
        }
    }

    /**
     * GAME-05 audit attribution — the ledger row records which principal moved
     * the points; a scheduler/background award without a session falls back to
     * the student, then to "system" (never null: created_by/updated_by are NOT
     * NULL in V1__initial_schema).
     */
    private String resolveActorEmail(Student student) {
        Authentication authentication =
                SecurityContextHolder.getContext() == null
                        ? null : SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getName() != null
                && !authentication.getName().isBlank()) {
            return authentication.getName();
        }
        if (student != null && student.getEmail() != null && !student.getEmail().isBlank()) {
            return student.getEmail();
        }
        return "system";
    }

    private boolean isPostgresBackend() {
        Boolean cached = postgresBackend;
        if (cached != null) return cached;
        synchronized (this) {
            if (postgresBackend == null) {
                String url = environment.getProperty("spring.datasource.url", "");
                postgresBackend = url.contains(":postgresql:");
            }
            return postgresBackend;
        }
    }
}
