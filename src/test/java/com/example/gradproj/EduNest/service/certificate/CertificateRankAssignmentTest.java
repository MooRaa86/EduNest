package com.example.gradproj.EduNest.service.certificate;

import com.example.gradproj.EduNest.dto.studentMentorship.StudentInLeaderboardDto;
import com.example.gradproj.EduNest.entity.certificate.Certificate;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.repository.certificate.CertificateRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CertificateRankAssignmentTest {

    private static final Long MENTORSHIP_ID = 7L;

    private TotalPointsRepository totalPointsRepository;
    private MentorShipRepository mentorShipRepository;
    private CertificateRepository certificateRepository;
    private StudentRepository studentRepository;
    private NotificationService notificationService;
    private SecurityService securityService;

    private CertificateService service;
    private MentorShip mentorship;

    @BeforeEach
    void setUp() {
        totalPointsRepository = mock(TotalPointsRepository.class);
        mentorShipRepository = mock(MentorShipRepository.class);
        certificateRepository = mock(CertificateRepository.class);
        studentRepository = mock(StudentRepository.class);
        notificationService = mock(NotificationService.class);
        securityService = mock(SecurityService.class);

        // Constructor field order (Lombok @RequiredArgsConstructor):
        // totalPointsRepository, mentorShipRepository, certificateRepository,
        // studentRepository, notificationService, securityService
        service = new CertificateService(
                totalPointsRepository, mentorShipRepository, certificateRepository,
                studentRepository, notificationService, securityService);

        mentorship = MentorShip.builder().id(MENTORSHIP_ID).title("Backend Track").build();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Student student(long id, String email) {
        return Student.builder().id(id).firstName("Stu").lastName(String.valueOf(id))
                .email(email).build();
    }

    private static StudentInLeaderboardDto row(String email, int points) {
        return StudentInLeaderboardDto.builder()
                .studentEmail(email)
                .totalPoints(points)
                .build();
    }

    /** Common stubbing; `students` is what findAllByEmailIn "finds" in the DB. */
    private void stubIssuePath(List<StudentInLeaderboardDto> leaderboard, List<Student> students) {
        when(securityService.getCurrentUserEmail()).thenReturn("mentor@test.com");
        when(securityService.isMentorOwnMentorship(MENTORSHIP_ID, "mentor@test.com")).thenReturn(true);
        when(mentorShipRepository.findById(MENTORSHIP_ID)).thenReturn(Optional.of(mentorship));
        // matchers (not raw values) — Pageable.unpaged() instances are not safely equals-comparable
        when(totalPointsRepository.findLeaderboardByMentorshipId(eq(MENTORSHIP_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(leaderboard));
        when(studentRepository.findAllByEmailIn(
                leaderboard.stream().map(StudentInLeaderboardDto::getStudentEmail).toList()))
                .thenReturn(students);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<Certificate>> listCaptor() {
        return (ArgumentCaptor<List<Certificate>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Ranks follow leaderboard order 1..N and every student is notified")
    void ranksFollowLeaderboardOrder() {
        // leaderboard is already sorted DESC by points (repository query)
        List<StudentInLeaderboardDto> leaderboard = List.of(
                row("gold@test.com", 90), row("silver@test.com", 60), row("bronze@test.com", 30));
        List<Student> students = List.of(
                student(11L, "gold@test.com"), student(12L, "silver@test.com"), student(13L, "bronze@test.com"));
        stubIssuePath(leaderboard, students);

        service.issueCertificates(MENTORSHIP_ID);

        ArgumentCaptor<List<Certificate>> captor = listCaptor();
        verify(certificateRepository, times(1)).saveAll(captor.capture());
        List<Certificate> saved = captor.getValue();

        assertThat(saved).hasSize(3);
        assertThat(saved).extracting(Certificate::getRank).containsExactly(1L, 2L, 3L);
        assertThat(saved).extracting(c -> c.getStudent().getEmail())
                .containsExactly("gold@test.com", "silver@test.com", "bronze@test.com");
        assertThat(saved).extracting(Certificate::getMentorship).containsOnly(mentorship);
        // All issued at the same instant
        assertThat(saved).extracting(Certificate::getIssuedAt).containsOnly(saved.get(0).getIssuedAt());

        // One notification per certificate, message carries the rank
        verify(notificationService).sendToUserByEmail(
                eq("gold@test.com"), eq("Certificate Issued!"),
                contains("rank #1"), eq(NotificationType.CERTIFICATE));
        verify(notificationService).sendToUserByEmail(
                eq("bronze@test.com"), eq("Certificate Issued!"),
                contains("rank #3"), eq(NotificationType.CERTIFICATE));
        verify(notificationService, times(3))
                .sendToUserByEmail(any(), any(), contains("rank #"), eq(NotificationType.CERTIFICATE));
    }

    @Test
    @DisplayName("Student missing from the DB map is SKIPPED; later students keep rank i+1 (no gap-fill)")
    void missingStudentSkipped_laterStudentsKeepIndexRank() {
        // rows: gold(i=0), silver(i=1), ghost(i=2) — ghost has no Student entity
        List<StudentInLeaderboardDto> leaderboard = List.of(
                row("gold@test.com", 90), row("silver@test.com", 60), row("ghost@test.com", 40));
        List<Student> students = List.of(student(11L, "gold@test.com"), student(12L, "silver@test.com"));
        stubIssuePath(leaderboard, students);

        service.issueCertificates(MENTORSHIP_ID);

        ArgumentCaptor<List<Certificate>> captor = listCaptor();
        verify(certificateRepository, times(1)).saveAll(captor.capture());
        List<Certificate> saved = captor.getValue();

        // ghost skipped; silver keeps rank 2 (its leaderboard index) — ranks are NOT renumbered
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(Certificate::getRank).containsExactly(1L, 2L);
        assertThat(saved).extracting(c -> c.getStudent().getEmail())
                .containsExactly("gold@test.com", "silver@test.com");

        // No notification was attempted for the ghost
        verify(notificationService, times(2))
                .sendToUserByEmail(any(), any(), contains("rank #"), eq(NotificationType.CERTIFICATE));
    }

    @Test
    @DisplayName("Empty leaderboard: nothing saved, no notifications (early return)")
    void emptyLeaderboard_noop() {
        when(securityService.getCurrentUserEmail()).thenReturn("mentor@test.com");
        when(securityService.isMentorOwnMentorship(MENTORSHIP_ID, "mentor@test.com")).thenReturn(true);
        when(mentorShipRepository.findById(MENTORSHIP_ID)).thenReturn(Optional.of(mentorship));
        when(totalPointsRepository.findLeaderboardByMentorshipId(eq(MENTORSHIP_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.issueCertificates(MENTORSHIP_ID);

        verify(certificateRepository, never()).saveAll(any());
        verifyNoInteractions(notificationService);
    }
}
