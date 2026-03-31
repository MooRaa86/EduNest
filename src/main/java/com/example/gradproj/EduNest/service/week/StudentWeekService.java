package com.example.gradproj.EduNest.service.week;

import com.example.gradproj.EduNest.dto.weeks.StudentWeekContentItemDTO;
import com.example.gradproj.EduNest.dto.weeks.StudentWeekContentsResponse;
import com.example.gradproj.EduNest.dto.weeks.WeekResponse;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.livesession.SessionAttendanceResult;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.lectures.LectureRepository;
import com.example.gradproj.EduNest.repository.livesession.LiveSessionRepository;
import com.example.gradproj.EduNest.repository.livesession.SessionAttendanceResultRepository;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizSubmissionRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StudentWeekService {

    private final WeekRepository weekRepository;
    private final TaskRepository taskRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final QuizRepository quizRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final ProjectRepository projectRepository;
    private final ProjectSubmissionRepository projectSubmissionRepository;
    private final LiveSessionRepository sessionRepository;
    private final SessionAttendanceResultRepository attendanceResultRepository;
    private final LectureRepository lectureRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;

    private Long getCurrentStudentId() {
        String email = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName)
                .orElseThrow(() -> new AccessDeniedException("Unauthenticated user"));
        return studentRepository.findIdByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("Student not found"));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('STUDENT')")
    public List<WeekResponse> getWeeksByMentorship(Long mentorshipId,String stEmail) {
        if (!enrollmentRepository.existsByMentorShip_IdAndStudent_Email(mentorshipId, stEmail)) {
            throw new AccessDeniedException("You are not enrolled in this mentorship");
        }
        return weekRepository.findByMentorship_IdOrderByIdAsc(mentorshipId)
                .stream()
                .map(w -> new WeekResponse(w.getId(), w.getTitle()))
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('STUDENT')")
    public List<StudentWeekContentsResponse> getMentorshipWeeksWithContents(Long mentorshipId,String stEmail) {
        Long studentId = getCurrentStudentId();
        if (!enrollmentRepository.existsByMentorShip_IdAndStudent_Email(mentorshipId, stEmail)) {
            throw new AccessDeniedException("You are not enrolled in this mentorship");
        }
        return weekRepository.findByMentorship_IdOrderByIdAsc(mentorshipId)
                .stream()
                .map(week -> buildWeekContents(week.getId(), week, studentId))
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('STUDENT')")
    public StudentWeekContentsResponse getWeekContents(Long weekId) {
        Week week = weekRepository.findById(weekId)
                .orElseThrow(() -> new globalLogicEx("Week not found"));
        Long studentId = getCurrentStudentId();
        if (!enrollmentRepository.isStudentEnrolledInWeekMentorship(weekId, studentId)) {
            throw new AccessDeniedException("You are not enrolled in this mentorship");
        }
        return buildWeekContents(weekId, week, studentId);
    }

    private StudentWeekContentsResponse buildWeekContents(Long weekId, Week week, Long studentId) {
        List<StudentWeekContentItemDTO> items = new ArrayList<>();

        sessionRepository.findByWeek_Id(weekId).forEach(s ->
                items.add(StudentWeekContentItemDTO.builder()
                        .type("SESSION").id(s.getId()).title(s.getTitle()).createdAt(s.getCreatedAt())
                        .completed(attendanceResultRepository
                                .findBySession_IdAndStudent_Id(s.getId(), studentId)
                                .map(SessionAttendanceResult::isAttended)
                                .orElse(false))
                        .build()));

        lectureRepository.findByWeek_Id(weekId).forEach(l ->
                items.add(StudentWeekContentItemDTO.builder()
                        .type("LECTURE").id(l.getId()).title(l.getTitle()).createdAt(l.getCreatedAt())
                        .completed(true)
                        .build()));

        taskRepository.findByWeek_IdAndStatusNot(weekId, TaskStatus.DRAFT).forEach(t ->
                items.add(StudentWeekContentItemDTO.builder()
                        .type("TASK").id(t.getId()).title(t.getTitle()).createdAt(t.getCreatedAt())
                        .completed(taskSubmissionRepository.existsByTask_IdAndStudent_Id(t.getId(), studentId))
                        .build()));

        quizRepository.findByWeek_IdAndStatusNot(weekId, QuizStatus.DRAFT).forEach(q ->
                items.add(StudentWeekContentItemDTO.builder()
                        .type("QUIZ").id(q.getId()).title(q.getTitle()).createdAt(q.getCreatedAt())
                        .completed(quizSubmissionRepository.existsByStudent_IdAndQuiz_Id(studentId, q.getId()))
                        .build()));

        projectRepository.findByWeek_IdAndStatusNot(weekId, ProjectStatus.DRAFT).forEach(p ->
                items.add(StudentWeekContentItemDTO.builder()
                        .type("PROJECT").id(p.getId()).title(p.getTitle()).createdAt(p.getCreatedAt())
                        .completed(projectSubmissionRepository.existsByProject_IdAndStudent_Id(p.getId(), studentId))
                        .build()));

        return StudentWeekContentsResponse.builder()
                .weekId(week.getId())
                .weekTitle(week.getTitle())
                .items(items)
                .build();
    }
}
