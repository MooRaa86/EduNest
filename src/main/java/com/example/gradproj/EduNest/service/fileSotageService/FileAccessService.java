package com.example.gradproj.EduNest.service.fileSotageService;

import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.projects.projection.ProjectAuthProjection;
import com.example.gradproj.EduNest.repository.projects.projection.ProjectSubmissionAuthProjection;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.repository.tasks.projection.TaskAuthProjection;
import com.example.gradproj.EduNest.repository.tasks.projection.TaskSubmissionAuthProjection;
import com.example.gradproj.EduNest.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileAccessService {

    private final TaskSubmissionRepository taskSubmissionRepository;
    private final TaskRepository taskRepository;
    private final ProjectSubmissionRepository projectSubmissionRepository;
    private final ProjectRepository projectRepository;
    private final CurrentUserProvider currentUserProvider;


    public String authorizeTaskSubmission(Long submissionId) {
        TaskSubmissionAuthProjection projection = taskSubmissionRepository.findAuthProjectionById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));

        String currentUserEmail = currentUserProvider.getEmail();
        if (!projection.getStudentEmail().equals(currentUserEmail) 
                && !projection.getMentorEmail().equals(currentUserEmail)) {
            throw new AccessDeniedException("Not authorized to view this submission");
        }
        return projection.getFilePath();
    }

    public String authorizeTask(Long taskId) {
        TaskAuthProjection projection = taskRepository.findAuthProjectionById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        String currentUserEmail = currentUserProvider.getEmail();
        if (!projection.getMentorEmail().equals(currentUserEmail)
                && !taskRepository.isStudentEnrolled(projection.getMentorshipId(), currentUserEmail)) {
            throw new AccessDeniedException("Not authorized to view this task attachment");
        }
        return projection.getFilePath();
    }

    public String authorizeProject(Long projectId) {
        ProjectAuthProjection projection = projectRepository.findAuthProjectionById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        String currentUserEmail = currentUserProvider.getEmail();
        if (!projection.getMentorEmail().equals(currentUserEmail)
                && !projectRepository.isStudentEnrolled(projection.getMentorshipId(), currentUserEmail)) {
            throw new AccessDeniedException("Not authorized to view this project attachment");
        }
        return projection.getFilePath();
    }

    public String authorizeProjectSubmission(Long submissionId) {
        ProjectSubmissionAuthProjection projection = projectSubmissionRepository.findAuthProjectionById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project submission not found"));

        String currentUserEmail = currentUserProvider.getEmail();
        if (!projection.getStudentEmail().equals(currentUserEmail)
                && !projection.getMentorEmail().equals(currentUserEmail)) {
            throw new AccessDeniedException("Not authorized to view this project submission");
        }
        return projection.getFilePath();
    }
}