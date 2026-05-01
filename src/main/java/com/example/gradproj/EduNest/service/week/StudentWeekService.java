package com.example.gradproj.EduNest.service.week;

import com.example.gradproj.EduNest.dto.weeks.*;
import com.example.gradproj.EduNest.entity.lectures.Lecture;
import com.example.gradproj.EduNest.entity.livesession.Session;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.entity.tasks.Task;
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

import java.util.*;
import java.util.stream.Collectors;

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
    public MentorshipWeeksResponse getWeeksByMentorship(Long mentorshipId, String stEmail) {
        if (!enrollmentRepository.existsByMentorShip_IdAndStudent_Email(mentorshipId, stEmail)) {
            throw new AccessDeniedException("You are not enrolled in this mentorship");
        }
        List<Week> weeks = weekRepository.findByMentorship_IdOrderByIdAsc(mentorshipId);
        if (weeks.isEmpty()) {
            return MentorshipWeeksResponse.builder()
                    .mentorshipTitle("")
                    .weeks(List.of())
                    .build();
        }
        String mentorshipTitle = weeks.get(0).getMentorship().getTitle();
        List<WeekResponse> weekResponses = weeks.stream()
                .map(w -> new WeekResponse(w.getId(), w.getTitle()))
                .toList();
        return MentorshipWeeksResponse.builder()
                .mentorshipTitle(mentorshipTitle)
                .weeks(weekResponses)
                .build();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('STUDENT')")
    public MentorshipWeeksWithContentsResponse getMentorshipWeeksWithContents(Long mentorshipId, String stEmail) {
        Long studentId = getCurrentStudentId();
        if (!enrollmentRepository.existsByMentorShip_IdAndStudent_Email(mentorshipId, stEmail)) {
            throw new AccessDeniedException("You are not enrolled in this mentorship");
        }
        List<Week> weeks = weekRepository.findByMentorship_IdOrderByIdAsc(mentorshipId);
        if (weeks.isEmpty()) {
            throw new AccessDeniedException("No weeks found for this mentorship");
        }
        String mentorshipTitle = weeks.get(0).getMentorship().getTitle();
        List<Long> weekIds = weeks.stream().map(Week::getId).toList();

        // Batch fetch all content for all weeks (5 queries only)
        Map<Long, List<Session>> sessionsByWeek = sessionRepository.findByWeek_IdIn(weekIds)
                .stream().collect(Collectors.groupingBy((Session s) -> s.getWeek().getId()));
        Map<Long, List<Lecture>> lecturesByWeek = lectureRepository.findByWeek_IdIn(weekIds)
                .stream().collect(Collectors.groupingBy((Lecture l) -> l.getWeek().getId()));
        Map<Long, List<Task>> tasksByWeek = taskRepository.findByWeek_IdInAndStatusNot(weekIds, TaskStatus.DRAFT)
                .stream().collect(Collectors.groupingBy((Task t) -> t.getWeek().getId()));
        Map<Long, List<Quiz>> quizzesByWeek = quizRepository.findByWeek_IdInAndStatusNot(weekIds, QuizStatus.DRAFT)
                .stream().collect(Collectors.groupingBy((Quiz q) -> q.getWeek().getId()));
        Map<Long, List<Project>> projectsByWeek = projectRepository.findByWeek_IdInAndStatusNot(weekIds, ProjectStatus.DRAFT)
                .stream().collect(Collectors.groupingBy((Project p) -> p.getWeek().getId()));

        // Batch fetch all completions (4 queries only)
        Set<Long> attendedSessionIds = attendanceResultRepository
                .findByStudent_IdAndSession_Week_IdIn(studentId, weekIds)
                .stream().map(r -> r.getSession().getId()).collect(Collectors.toSet());
        Set<Long> submittedTaskIds = taskSubmissionRepository
                .findByStudent_IdAndTask_Week_IdIn(studentId, weekIds)
                .stream().map(ts -> ts.getTask().getId()).collect(Collectors.toSet());
        Set<Long> submittedQuizIds = quizSubmissionRepository
                .findByStudent_IdAndQuiz_Week_IdIn(studentId, weekIds)
                .stream().map(qs -> qs.getQuiz().getId()).collect(Collectors.toSet());
        Set<Long> submittedProjectIds = projectSubmissionRepository
                .findByStudent_IdAndProject_Week_IdIn(studentId, weekIds)
                .stream().map(ps -> ps.getProject().getId()).collect(Collectors.toSet());

        List<StudentWeekContentsResponse> weekContents = weeks.stream()
                .map(w -> buildWeekContentsBatch(w, sessionsByWeek, lecturesByWeek, tasksByWeek, quizzesByWeek, projectsByWeek,
                        attendedSessionIds, submittedTaskIds, submittedQuizIds, submittedProjectIds))
                .toList();

        return MentorshipWeeksWithContentsResponse.builder()
                .mentorshipTitle(mentorshipTitle)
                .weeks(weekContents)
                .build();
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

    private StudentWeekContentsResponse buildWeekContentsBatch(
            Week week,
            Map<Long, List<Session>> sessionsByWeek,
            Map<Long, List<Lecture>> lecturesByWeek,
            Map<Long, List<Task>> tasksByWeek,
            Map<Long, List<Quiz>> quizzesByWeek,
            Map<Long, List<Project>> projectsByWeek,
            Set<Long> attendedSessionIds,
            Set<Long> submittedTaskIds,
            Set<Long> submittedQuizIds,
            Set<Long> submittedProjectIds) {

        Long weekId = week.getId();
        List<StudentWeekContentItemDTO> items = new ArrayList<>();

        sessionsByWeek.getOrDefault(weekId, List.of()).forEach(s ->
            items.add(StudentWeekContentItemDTO.builder()
                    .type("SESSION").id(s.getId()).title(s.getTitle()).createdAt(s.getCreatedAt())
                    .completed(attendedSessionIds.contains(s.getId()))
                    .build()));

        lecturesByWeek.getOrDefault(weekId, List.of()).forEach(l ->
            items.add(StudentWeekContentItemDTO.builder()
                    .type("LECTURE").id(l.getId()).title(l.getTitle()).createdAt(l.getCreatedAt())
                    .completed(true)
                    .build()));

        tasksByWeek.getOrDefault(weekId, List.of()).forEach(t ->
            items.add(StudentWeekContentItemDTO.builder()
                    .type("TASK").id(t.getId()).title(t.getTitle()).createdAt(t.getCreatedAt())
                    .completed(submittedTaskIds.contains(t.getId()))
                    .build()));

        quizzesByWeek.getOrDefault(weekId, List.of()).forEach(q ->
            items.add(StudentWeekContentItemDTO.builder()
                    .type("QUIZ").id(q.getId()).title(q.getTitle()).createdAt(q.getCreatedAt())
                    .completed(submittedQuizIds.contains(q.getId()))
                    .build()));

        projectsByWeek.getOrDefault(weekId, List.of()).forEach(p ->
            items.add(StudentWeekContentItemDTO.builder()
                    .type("PROJECT").id(p.getId()).title(p.getTitle()).createdAt(p.getCreatedAt())
                    .completed(submittedProjectIds.contains(p.getId()))
                    .build()));

        return StudentWeekContentsResponse.builder()
                .weekId(week.getId())
                .weekTitle(week.getTitle())
                .items(items)
                .build();
    }

    private StudentWeekContentsResponse buildWeekContents(Long weekId, Week week, Long studentId) {
        List<StudentWeekContentItemDTO> items = new ArrayList<>();

        // Fetch all content
        List<Session> sessions = sessionRepository.findByWeek_Id(weekId);
        List<Lecture> lectures = lectureRepository.findByWeek_Id(weekId);
        List<Task> tasks = taskRepository.findByWeek_IdAndStatusNot(weekId, TaskStatus.DRAFT);
        List<Quiz> quizzes = quizRepository.findByWeek_IdAndStatusNot(weekId, QuizStatus.DRAFT);
        List<Project> projects = projectRepository.findByWeek_IdAndStatusNot(weekId, ProjectStatus.DRAFT);

        // Batch fetch all completions (4 queries instead of N+M+K+L)
        Set<Long> attendedSessionIds = attendanceResultRepository
                .findAttendedByStudentIdAndWeekId(studentId, weekId)
                .stream().map(r -> r.getSession().getId()).collect(Collectors.toSet());
        Set<Long> submittedTaskIds = taskSubmissionRepository
                .findByStudentIdAndWeekId(studentId, weekId)
                .stream().map(ts -> ts.getTask().getId()).collect(Collectors.toSet());
        Set<Long> submittedQuizIds = quizSubmissionRepository
                .findByStudentIdAndWeekId(studentId, weekId)
                .stream().map(qs -> qs.getQuiz().getId()).collect(Collectors.toSet());
        Set<Long> submittedProjectIds = projectSubmissionRepository
                .findByStudentIdAndWeekId(studentId, weekId)
                .stream().map(ps -> ps.getProject().getId()).collect(Collectors.toSet());

        // Build items
        sessions.forEach(s -> items.add(StudentWeekContentItemDTO.builder()
                .type("SESSION").id(s.getId()).title(s.getTitle()).createdAt(s.getCreatedAt())
                .completed(attendedSessionIds.contains(s.getId()))
                .build()));

        lectures.forEach(l -> items.add(StudentWeekContentItemDTO.builder()
                .type("LECTURE").id(l.getId()).title(l.getTitle()).createdAt(l.getCreatedAt())
                .completed(true)
                .build()));

        tasks.forEach(t -> items.add(StudentWeekContentItemDTO.builder()
                .type("TASK").id(t.getId()).title(t.getTitle()).createdAt(t.getCreatedAt())
                .completed(submittedTaskIds.contains(t.getId()))
                .build()));

        quizzes.forEach(q -> items.add(StudentWeekContentItemDTO.builder()
                .type("QUIZ").id(q.getId()).title(q.getTitle()).createdAt(q.getCreatedAt())
                .completed(submittedQuizIds.contains(q.getId()))
                .build()));

        projects.forEach(p -> items.add(StudentWeekContentItemDTO.builder()
                .type("PROJECT").id(p.getId()).title(p.getTitle()).createdAt(p.getCreatedAt())
                .completed(submittedProjectIds.contains(p.getId()))
                .build()));

        return StudentWeekContentsResponse.builder()
                .weekId(week.getId())
                .weekTitle(week.getTitle())
                .items(items)
                .build();
    }
}
