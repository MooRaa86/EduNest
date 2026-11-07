package com.example.gradproj.EduNest.service.certificate;

import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Behavioral replacement for the old saveAll/mock-specific assertions. */
class CertificateRankAssignmentTest extends ProdChainIntegrationTestBase {
    @Autowired private AuthFixture auth;
    @Autowired private TotalPointsRepository points;
    @Autowired private CertificateService service;
    @Autowired private JdbcTemplate jdbc;

    private void authenticate(String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void ranksFollowLeaderboardOrder() {
        String owner = "eb54-rank-owner@example.org";
        var chain = auth.mentorship(owner, Status.ACTIVE);
        var a = auth.enroll(chain.mentorship().getId(), "eb54-rank-a@example.org").getStudent();
        var b = auth.enroll(chain.mentorship().getId(), "eb54-rank-b@example.org").getStudent();
        var c = auth.enroll(chain.mentorship().getId(), "eb54-rank-c@example.org").getStudent();
        for (var student : List.of(a, b, c)) points.saveAndFlush(TotalPoints.builder().student(student)
                .mentorship(chain.mentorship()).totalPoints(student == c ? 30 : 90).build());
        authenticate(owner);
        service.issueCertificates(chain.mentorship().getId());
        var rows = jdbc.queryForList("select student_id, student_rank, issued_at from certificates where mentorship_id = ? order by student_rank", chain.mentorship().getId());
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(r -> ((Number) r.get("student_id")).longValue()).containsExactly(a.getId(), b.getId(), c.getId());
        assertThat(rows).extracting(r -> ((Number) r.get("student_rank")).longValue()).containsExactly(1L, 2L, 3L);
        assertThat(rows).extracting(r -> r.get("issued_at")).containsOnly(rows.get(0).get("issued_at"));
        assertThat(jdbc.queryForObject("select count(*) from user_notification un join notification n on n.id=un.notification_id where un.user_id in (?, ?, ?) and n.type='CERTIFICATE'", Long.class, a.getId(), b.getId(), c.getId())).isEqualTo(3);
        assertThatThrownBy(() -> service.issueCertificates(chain.mentorship().getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select count(*) from certificates where mentorship_id = ?", Long.class, chain.mentorship().getId())).isEqualTo(3);
    }

    @Test
    void missingStudentCannotEnterLeaderboardAndOtherRanksRemainCorrect() {
        String owner = "eb54-rank-missing-owner@example.org";
        var chain = auth.mentorship(owner, Status.ACTIVE);
        var student = auth.enroll(chain.mentorship().getId(), "eb54-rank-present@example.org").getStudent();
        points.saveAndFlush(TotalPoints.builder().student(student).mentorship(chain.mentorship()).totalPoints(10).build());
        assertThatThrownBy(() -> jdbc.update("insert into total_points (student_id, mentorship_id, total_points, created_at, updated_at, created_by, updated_by, public_id) values (?, ?, ?, current_timestamp, current_timestamp, ?, ?, ?)", -99999L, chain.mentorship().getId(), 99, owner, owner, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        authenticate(owner);
        service.issueCertificates(chain.mentorship().getId());
        assertThat(jdbc.queryForObject("select student_rank from certificates where mentorship_id=? and student_id=?", Long.class, chain.mentorship().getId(), student.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from certificates where mentorship_id=?", Long.class, chain.mentorship().getId())).isEqualTo(1);
    }

    @Test
    void emptyLeaderboardAndForeignMentor() {
        String owner = "eb54-rank-empty-owner@example.org";
        var chain = auth.mentorship(owner, Status.ACTIVE);
        authenticate("eb54-foreign@example.org");
        assertThatThrownBy(() -> service.issueCertificates(chain.mentorship().getId())).isInstanceOf(AccessDeniedException.class);
        authenticate(owner);
        service.issueCertificates(chain.mentorship().getId());
        assertThat(jdbc.queryForObject("select count(*) from certificates where mentorship_id=?", Long.class, chain.mentorship().getId())).isZero();
    }
}
