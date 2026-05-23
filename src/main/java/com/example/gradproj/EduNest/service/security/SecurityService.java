package com.example.gradproj.EduNest.service.security;

import com.example.gradproj.EduNest.entity.lectures.Lecture;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.AuthorizationRepository;
import com.example.gradproj.EduNest.repository.lectures.LectureRepository;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SecurityService {
    private final EnrollmentRepository enrollmentRepository;
    private final MentorShipRepository mentorShipRepository;
    private final MentorRepository mentorRepository;
    private final StudentRepository studentRepository;
    private final LectureRepository lectureRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final WeekRepository weekRepository;
    private final ProjectSubmissionRepository projectSubmissionRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final AuthorizationRepository securityRepository;

    public String getCurrentUserEmail() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName)
                .orElseThrow(() -> new AccessDeniedException("Unauthenticated user"));
    }

    public Boolean isMentorOwnMentorship(Long id,String email){
        return mentorShipRepository.existsByIdAndMentor_Email(id, email);
    }

    public Boolean isStudentEnrolledByMentorshipId(String stEmail, Long mentorShipId) {
        return enrollmentRepository.existsByMentorShip_IdAndStudent_Email(mentorShipId, stEmail);
    }

    public Boolean isStudentEnrolledByWeekId(String studentEmail, Long weekId) {
        return enrollmentRepository.isStudentEnrolledInWeekMentorshipByEmail(weekId, studentEmail);
    }

    //ToDo query joins on Task,Project,Quiz,Session,Lecture & week & mentorshipID & enrollment tables with enrolled status
    //ToDo query joins week on mentorshipId -> MentorshipTable

    //------------------------------------------------------

    public Boolean isMentorOwnLiveSession(Long sessionId, String email) {
        return securityRepository.isMentorOwnLiveSession(sessionId, email);
    }

    public Boolean isStudentEnrolledByLiveSessionId(String email, Long sessionId) {
        return securityRepository.isStudentEnrolledByLiveSessionId(email, sessionId);
    }

    public Boolean isMentorOwnQuiz(Long quizId, String email) {
        return securityRepository.isMentorOwnQuiz(quizId, email);
    }

    public Boolean isStudentEnrolledByQuizId(String email, Long quizId) {
        return securityRepository.isStudentEnrolledByQuizId(email, quizId);
    }

    public Boolean isMentorOwnQuestion(Long questionId, String email) {
        return securityRepository.isMentorOwnQuestion(questionId, email);
    }

    public Boolean isUserOwnNotification(Long notificationId, String email) {
        return securityRepository.isUserOwnNotification(notificationId, email);
    }

    public Boolean isUserMemberOfChatRoom(Long roomId, String email) {
        return securityRepository.isUserMemberOfChatRoom(roomId, email);
    }

    //------------------------------------------------------

    public Long getCurrentMentorId() {
        return mentorRepository.findByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new AccessDeniedException("Mentor not found"))
                .getId();
    }

    public com.example.gradproj.EduNest.entity.users.Mentor getCurrentMentor() {
        return mentorRepository.findByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new AccessDeniedException("Mentor not found"));
    }

    public Long getCurrentStudentId() {
        return studentRepository.findIdByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Student not found"));
    }

    public com.example.gradproj.EduNest.entity.users.Student getCurrentStudent() {
        return studentRepository.findByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Student not found"));
    }

    public boolean isMentorOwnerOfMentorship(String mentorEmail, Long mentorshipId) {
        return mentorShipRepository.getMentorshipStats(mentorshipId, mentorEmail) != null;
    }

    public void validateMentorOwnsMentorship(Long mentorshipId) {
        Long mentorId = mentorShipRepository.findById(mentorshipId)
                .orElseThrow(() -> new globalLogicEx("mentorShip not found"))
                .getMentor().getId();
        if (!mentorId.equals(getCurrentMentorId())) {
            throw new AccessDeniedException("You are not authorized to access this mentorship");
        }
    }

    public void validateMentorOwnsMentorship(Long mentorshipId, String email) {
        if (!mentorShipRepository.existsByIdAndMentor_Email(mentorshipId, email)) {
            throw new AccessDeniedException("You are not authorized to access this mentorship");
        }
    }




    public Project validateMentorOwnsProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new globalLogicEx("Project not found"));
        Long mentorId = project.getWeek().getMentorship().getMentor().getId();
        if (!mentorId.equals(getCurrentMentorId())) {
            throw new AccessDeniedException("You are not authorized to access this project");
        }
        return project;
    }

    public Project validateMentorOwnsProjectByEmail(Long projectId, String email) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new globalLogicEx("Project not found"));
        if (!mentorShipRepository.existsByIdAndMentor_Email(project.getWeek().getMentorship().getId(), email)) {
            throw new AccessDeniedException("You are not authorized to access this project");
        }
        return project;
    }

    public Task validateMentorOwnsTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new globalLogicEx("Task not found"));
        Long mentorId = task.getWeek().getMentorship().getMentor().getId();
        if (!mentorId.equals(getCurrentMentorId())) {
            throw new AccessDeniedException("You are not authorized to access this task");
        }
        return task;
    }

    public Task validateMentorOwnsTaskByEmail(Long taskId, String email) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new globalLogicEx("Task not found"));
        if (!mentorShipRepository.existsByIdAndMentor_Email(task.getWeek().getMentorship().getId(), email)) {
            throw new AccessDeniedException("You are not authorized to access this task");
        }
        return task;
    }

    public Week validateMentorOwnsWeek(Long weekId) {
        Week week = weekRepository.findById(weekId)
                .orElseThrow(() -> new globalLogicEx("Week not found"));
        Long mentorId = week.getMentorship().getMentor().getId();
        if (!mentorId.equals(getCurrentMentorId())) {
            throw new AccessDeniedException("You are not authorized to access this week");
        }
        return week;
    }

    public Lecture validateMentorOwnsLecture(Long lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new globalLogicEx("lecture not found"));
        Long mentorId = lecture.getWeek().getMentorship().getMentor().getId();
        if (!mentorId.equals(getCurrentMentorId())) {
            throw new AccessDeniedException("You are not authorized to access this lecture");
        }
        return lecture;
    }

    public TaskSubmission validateMentorOwnsTaskSubmission(Long submissionId) {
        TaskSubmission sub = taskSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new globalLogicEx("Submission not found"));
        Long mentorId = sub.getTask().getWeek().getMentorship().getMentor().getId();
        if (!mentorId.equals(getCurrentMentorId())) {
            throw new AccessDeniedException("You are not authorized to grade this submission");
        }
        return sub;
    }

    public ProjectSubmission validateMentorOwnsProjectSubmissionByEmail(Long submissionId, String email) {
        ProjectSubmission sub = projectSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new globalLogicEx("Submission not found"));
        if (!sub.getProject().getWeek().getMentorship().getMentor().getEmail().equals(email)) {
            throw new AccessDeniedException("You are not authorized to grade this submission");
        }
        return sub;
    }

    public void validateStudentEnrolledForProjectByEmail(Long projectId, String email) {
        if (!enrollmentRepository.isStudentEnrolledForProjectByEmail(projectId, email)) {
            throw new globalLogicEx("You are not enrolled in this mentorship");
        }
    }

    public void validateStudentEnrolledForTask(Long taskId, Long studentId) {
        if (!enrollmentRepository.isStudentEnrolledForTask(taskId, studentId)) {
            throw new globalLogicEx("You must enroll in this mentorship before submitting tasks.");
        }
    }

    public void validateStudentEnrolledInMentorship(Long mentorshipId, Long studentId) {
        if (!enrollmentRepository.existsByMentorShip_IdAndStudent_Id(mentorshipId, studentId)) {
            throw new globalLogicEx("Student is not enrolled in the mentorship that owns this badge");
        }
    }

    public void validateMentorHasAccessToStudent(Long studentId) {
        Long mentorId = getCurrentMentorId();
        boolean hasAccess = enrollmentRepository.existsByMentorIdAndStudentId(mentorId, studentId);
        if (!hasAccess) {
            throw new AccessDeniedException("You are not authorized to access this student's profile");
        }
    }
}
