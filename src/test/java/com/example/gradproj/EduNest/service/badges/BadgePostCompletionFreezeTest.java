package com.example.gradproj.EduNest.service.badges;

import com.example.gradproj.EduNest.dto.badges.request.AwardBadgeRequest;
import com.example.gradproj.EduNest.entity.badges.Badge;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.badges.BadgeAwardRepository;
import com.example.gradproj.EduNest.repository.badges.BadgeRepository;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BadgePostCompletionFreezeTest {

    private BadgeAwardRepository badgeAwardRepository;
    private BadgeRepository badgeRepository;
    private StudentRepository studentRepository;
    private MentorRepository mentorRepository;
    private EnrollmentRepository enrollmentRepository;
    private TotalPointsServiceImp totalPointsService;
    private MentorShipRepository mentorShipRepository;
    private NotificationService notificationService;
    private EntityManager entityManager;
    private BadgeAwardService badgeAwardService;

    private static final String MENTOR_EMAIL = "mentor@test.org";
    private static final Long MENTOR_ID = 5L;
    private static final Long BADGE_ID = 20L;
    private static final Long MENTORSHIP_ID = 100L;
    private static final Long STUDENT_ID = 300L;

    @BeforeEach
    void setUp() {
        badgeAwardRepository = mock(BadgeAwardRepository.class);
        badgeRepository = mock(BadgeRepository.class);
        studentRepository = mock(StudentRepository.class);
        mentorRepository = mock(MentorRepository.class);
        enrollmentRepository = mock(EnrollmentRepository.class);
        totalPointsService = mock(TotalPointsServiceImp.class);
        mentorShipRepository = mock(MentorShipRepository.class);
        notificationService = mock(NotificationService.class);
        entityManager = mock(EntityManager.class);

        badgeAwardService = new BadgeAwardService(
                badgeAwardRepository,
                badgeRepository,
                studentRepository,
                mentorRepository,
                enrollmentRepository,
                totalPointsService,
                mentorShipRepository,
                notificationService,
                entityManager
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(MENTOR_EMAIL, "n/a", List.of(new SimpleGrantedAuthority("ROLE_MENTOR")))
        );

        Mentor mentor = Mentor.builder().id(MENTOR_ID).email(MENTOR_EMAIL).build();
        when(mentorRepository.findByEmail(MENTOR_EMAIL)).thenReturn(Optional.of(mentor));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("rejects awardBadge when mentorship status is COMPLETED")
    void rejectsAwardBadgeWhenMentorshipIsCompleted() {
        Mentor mentor = Mentor.builder().id(MENTOR_ID).email(MENTOR_EMAIL).build();
        MentorShip completedMentorship = MentorShip.builder()
                .id(MENTORSHIP_ID)
                .mentor(mentor)
                .status(Status.COMPLETED)
                .build();
        Badge badge = Badge.builder().id(BADGE_ID).mentorship(completedMentorship).points(50).build();

        when(badgeRepository.findById(BADGE_ID)).thenReturn(Optional.of(badge));
        when(badgeRepository.findForUpdateById(BADGE_ID)).thenReturn(Optional.of(badge));
        when(mentorShipRepository.findForUpdateById(MENTORSHIP_ID)).thenReturn(Optional.of(completedMentorship));

        AwardBadgeRequest req = new AwardBadgeRequest();
        req.setStudentId(STUDENT_ID);
        req.setNote("Great performance!");

        assertThatThrownBy(() -> badgeAwardService.awardBadge(BADGE_ID, req))
                .isInstanceOf(globalLogicEx.class)
                .hasMessage("Cannot award badge once the mentorship is completed");

        verify(badgeAwardRepository, never()).save(any());
        verify(totalPointsService, never()).applyDelta(any(), any(), anyInt());
    }

    @Test
    @DisplayName("allows awardBadge when mentorship status is ACTIVE")
    void allowsAwardBadgeWhenMentorshipIsActive() {
        Mentor mentor = Mentor.builder().id(MENTOR_ID).email(MENTOR_EMAIL).build();
        MentorShip activeMentorship = MentorShip.builder()
                .id(MENTORSHIP_ID)
                .mentor(mentor)
                .status(Status.ACTIVE)
                .title("Spring Boot Mastery")
                .build();
        Badge badge = Badge.builder().id(BADGE_ID).mentorship(activeMentorship).title("Top Solver").points(50).build();
        Student student = Student.builder().id(STUDENT_ID).email("student@test.org").firstName("Ali").lastName("Hassan").build();

        when(badgeRepository.findById(BADGE_ID)).thenReturn(Optional.of(badge));
        when(badgeRepository.findForUpdateById(BADGE_ID)).thenReturn(Optional.of(badge));
        when(mentorShipRepository.findForUpdateById(MENTORSHIP_ID)).thenReturn(Optional.of(activeMentorship));
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(enrollmentRepository.existsByMentorShip_IdAndStudent_Id(MENTORSHIP_ID, STUDENT_ID)).thenReturn(true);
        when(badgeAwardRepository.existsByBadge_IdAndStudent_Id(BADGE_ID, STUDENT_ID)).thenReturn(false);

        AwardBadgeRequest req = new AwardBadgeRequest();
        req.setStudentId(STUDENT_ID);
        req.setNote("Great performance!");

        assertThatCode(() -> badgeAwardService.awardBadge(BADGE_ID, req)).doesNotThrowAnyException();

        verify(badgeAwardRepository).save(any());
        verify(totalPointsService).applyDelta(student, activeMentorship, 50);
        verify(notificationService).sendToUserByEmail(eq("student@test.org"), anyString(), anyString(), any());
    }
}
