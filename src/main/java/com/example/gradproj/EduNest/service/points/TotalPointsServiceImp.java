package com.example.gradproj.EduNest.service.points;

import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** EB-51 work in progress. Multi-row writer ordering is not yet established. */
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

        TotalPoints points = totalPointsRepository.findForUpdate(student.getId(), mentorship.getId())
                .orElse(null);
        if (points == null) {
            if (isPostgresBackend()) {
                totalPointsRepository.insertEmptyIfAbsentPostgres(
                        student.getId(), mentorship.getId(), resolveActorEmail(student));
                points = totalPointsRepository.findForUpdate(student.getId(), mentorship.getId())
                        .orElseThrow(() -> new IllegalStateException("Initialized points row not found"));
            } else {
                // H2 test profile only; concurrent first inserts are tested on PostgreSQL.
                points = totalPointsRepository.saveAndFlush(TotalPoints.builder()
                        .student(student).mentorship(mentorship).totalPoints(0).build());
            }
        }
        long updated = (long) points.getTotalPoints() + delta;
        if (updated > Integer.MAX_VALUE) throw new ArithmeticException("Points total exceeds integer range");
        points.setTotalPoints((int) Math.max(0L, updated));
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
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
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
