package com.example.gradproj.EduNest.service.file;

import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FileAccessService {

    private final EnrollmentRepository enrollmentRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final TaskRepository taskRepository;
    private final ProjectSubmissionRepository projectSubmissionRepository;
    private final ProjectRepository projectRepository;


    public TaskSubmission authorizeTaskSubmission(Long submissionId) {

        TaskSubmission sub = taskSubmissionRepository.findById(submissionId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Submission not found"
                        ));

        String currentUserEmail = getCurrentUserEmail();

        boolean isStudent =
                sub.getStudent()
                        .getEmail()
                        .equals(currentUserEmail);

        boolean isMentor =
                sub.getTask()
                        .getWeek()
                        .getMentorship()
                        .getMentor()
                        .getEmail()
                        .equals(currentUserEmail);

        if (!isStudent && !isMentor) {
            throw new AccessDeniedException(
                    "Not authorized to view this submission"
            );
        }

        return sub;
    }

    public Task authorizeTask(Long taskId) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        String currentUserEmail = getCurrentUserEmail();

        boolean isMentor =
                task.getWeek()
                        .getMentorship()
                        .getMentor()
                        .getEmail()
                        .equals(currentUserEmail);

        boolean isEnrolledStudent = enrollmentRepository.existsByMentorShip_IdAndStudent_Email(task.getWeek().getMentorship().getId(), currentUserEmail);

        if (!isMentor && !isEnrolledStudent) {
            throw new AccessDeniedException(
                    "Not authorized to view this task attachment"
            );
        }

        return task;
    }

    public Project authorizeProject(Long projectId) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        String currentUserEmail = getCurrentUserEmail();

        boolean isMentor =
                project.getWeek()
                        .getMentorship()
                        .getMentor()
                        .getEmail()
                        .equals(currentUserEmail);

        boolean isEnrolledStudent =
                enrollmentRepository
                        .existsByMentorShip_IdAndStudent_Email(
                                project.getWeek()
                                        .getMentorship()
                                        .getId(),
                                currentUserEmail
                        );

        if (!isMentor && !isEnrolledStudent) {
            throw new AccessDeniedException(
                    "Not authorized to view this project attachment"
            );
        }

        return project;
    }

    public ProjectSubmission authorizeProjectSubmission(
            Long submissionId
    ) {

        ProjectSubmission submission = projectSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project submission not found"));
        String currentUserEmail = getCurrentUserEmail();
        boolean isStudent = submission.getStudent().getEmail().equals(currentUserEmail);
        boolean isMentor =
                submission.getProject()
                        .getWeek()
                        .getMentorship()
                        .getMentor()
                        .getEmail()
                        .equals(currentUserEmail);
        if (!isStudent && !isMentor) {
            throw new AccessDeniedException("Not authorized to view this project submission");
        }

        return submission;
    }

    private String getCurrentUserEmail() {

        return Optional.ofNullable(
                        org.springframework.security.core.context
                                .SecurityContextHolder
                                .getContext()
                                .getAuthentication()
                )
                .filter(authentication -> authentication.isAuthenticated())
                .map(authentication -> authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Unauthenticated user"));
    }
}