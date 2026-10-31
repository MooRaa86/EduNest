package com.example.gradproj.EduNest.service.fileSotageService;

import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.projects.projection.ProjectAuthProjection;
import com.example.gradproj.EduNest.repository.projects.projection.ProjectSubmissionAuthProjection;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.repository.tasks.projection.TaskAuthProjection;
import com.example.gradproj.EduNest.repository.tasks.projection.TaskSubmissionAuthProjection;
import com.example.gradproj.EduNest.security.CurrentUserProvider;
import com.example.gradproj.EduNest.config.file.UploadPolicy;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileAccessService {

    private final TaskSubmissionRepository taskSubmissionRepository;
    private final TaskRepository taskRepository;
    private final ProjectSubmissionRepository projectSubmissionRepository;
    private final ProjectRepository projectRepository;
    private final CurrentUserProvider currentUserProvider;
    private final EntityManager entityManager;

    @Value("${app.upload.base-dir:uploads}")
    private String baseDir;

    /** Authorize only an exact, unambiguous current database reference under the upload root. */
    public String authorizeGeneric(String input) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Authentication required");
        }
        try {
            if (input == null || input.isBlank() || input.indexOf('\\') >= 0 || input.indexOf('%') >= 0
                    || input.chars().anyMatch(Character::isISOControl)) throw unavailable();
            String[] parts = input.split("/", -1);
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.equals(".") || part.equals("..") || (part.isEmpty() && i != 0)) throw unavailable();
            }
            Path configured = Path.of(baseDir.replace('\\', '/')).normalize();
            Path root = configured.toAbsolutePath().normalize();
            Path supplied = Path.of(input);
            Path relative;
            if (input.startsWith("/uploads/") && !supplied.normalize().startsWith(root)) {
                relative = Path.of(input.substring("/uploads/".length()));
            } else if (supplied.isAbsolute()) {
                if (!supplied.normalize().startsWith(root)) throw unavailable();
                relative = root.relativize(supplied.normalize());
            } else if (!configured.isAbsolute() && supplied.startsWith(configured)) {
                relative = configured.relativize(supplied);
            } else {
                relative = supplied;
            }
            if (relative.getNameCount() != 2 || relative.isAbsolute()) throw unavailable();
            String folder = relative.getName(0).toString();
            Path target = root.resolve(relative).normalize();
            if (!target.startsWith(root)) throw unavailable();

            if (UploadPolicy.PUBLIC_FOLDERS.contains(folder)) {
                String entity = switch (folder) {
                    case "mentorship-cover-images" -> "MentorShip";
                    case "student" -> "Student";
                    case "mentor" -> "Mentor";
                    case "chatroom" -> "ChatRoom";
                    default -> throw unavailable();
                };
                String field = switch (folder) {
                    case "mentorship-cover-images" -> "coverImageUrl";
                    case "chatroom" -> "imageUrl";
                    default -> "profileImageUrl";
                };
                String url = "/uploads/" + relative.toString().replace('\\', '/');
                List<String> urls = entityManager.createQuery("select e." + field + " from " + entity + " e where e." + field + " = :url", String.class)
                        .setParameter("url", url).setMaxResults(2).getResultList();
                if (urls.size() != 1 || !url.equals(urls.get(0))) throw unavailable();
            } else if (UploadPolicy.PRIVATE_FOLDERS.contains(folder)) {
                var paths = new LinkedHashSet<String>();
                paths.add(target.toString().replace('\\', '/'));
                paths.add(relative.toString().replace('\\', '/'));
                paths.add(configured.resolve(relative).normalize().toString().replace('\\', '/'));
                List<String> candidates = List.copyOf(paths);
                List<String> entities = List.of("TaskSubmission", "ProjectSubmission", "Task", "Project");
                List<String> fields = List.of("uploadedFilePath", "uploadedFilePath", "uploadedAttachmentPath", "uploadedAttachmentPath");
                List<Integer> matches = new ArrayList<>();
                List<Long> ids = new ArrayList<>();
                for (int i = 0; i < entities.size(); i++) {
                    List<Long> found = entityManager.createQuery("select e.id from " + entities.get(i) + " e where e." + fields.get(i) + " in :paths", Long.class)
                            .setParameter("paths", candidates).setMaxResults(2).getResultList();
                    for (Long id : found) { matches.add(i); ids.add(id); }
                }
                if (matches.size() != 1) throw unavailable();
                int kind = matches.get(0);
                if ((kind < 2 && !folder.equals("submissions"))
                        || (kind == 2 && !folder.equals("task-attachment"))
                        || (kind == 3 && !folder.equals("project-attachment"))) throw unavailable();
                String email = auth.getName();
                String stored = switch (kind) {
                    case 0 -> authorizeTaskSubmission(ids.get(0), email);
                    case 1 -> authorizeProjectSubmission(ids.get(0), email);
                    case 2 -> authorizeTask(ids.get(0), email);
                    case 3 -> authorizeProject(ids.get(0), email);
                    default -> throw unavailable();
                };
                // SQL collations may be case-insensitive; require an exact Java string match too.
                if (!candidates.contains(stored)) throw unavailable();
            } else throw unavailable();

            Path realRoot = root.toRealPath();
            Path realTarget = target.toRealPath();
            if (!realTarget.equals(realRoot.resolve(relative).normalize()) || !Files.isRegularFile(realTarget)) throw unavailable();
            return realTarget.toString();
        } catch (InvalidPathException | IOException | AccessDeniedException ex) {
            throw unavailable();
        }
    }

    private EntityNotFoundException unavailable() {
        return new EntityNotFoundException("File unavailable");
    }


    public String authorizeTaskSubmission(Long submissionId) {
        return authorizeTaskSubmission(submissionId, currentUserProvider.getEmail());
    }

    private String authorizeTaskSubmission(Long submissionId, String currentUserEmail) {
        TaskSubmissionAuthProjection projection = taskSubmissionRepository.findAuthProjectionById(submissionId)
                .orElseThrow(() -> new EntityNotFoundException("Submission not found"));

        if (!projection.getStudentEmail().equals(currentUserEmail) 
                && !projection.getMentorEmail().equals(currentUserEmail)) {
            throw new AccessDeniedException("Not authorized to view this submission");
        }
        return projection.getFilePath();
    }

    public String authorizeTask(Long taskId) {
        return authorizeTask(taskId, currentUserProvider.getEmail());
    }

    private String authorizeTask(Long taskId, String currentUserEmail) {
        TaskAuthProjection projection = taskRepository.findAuthProjectionById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found"));

        // StudentWeekService exposes PUBLISHED and CLOSED, but not DRAFT.
        boolean studentVisible = projection.getStatus() == TaskStatus.PUBLISHED
                || projection.getStatus() == TaskStatus.CLOSED;
        if (!projection.getMentorEmail().equals(currentUserEmail)
                && !(studentVisible && taskRepository.isStudentEnrolled(projection.getMentorshipId(), currentUserEmail))) {
            throw new AccessDeniedException("Not authorized to view this task attachment");
        }
        return projection.getFilePath();
    }

    public String authorizeProject(Long projectId) {
        return authorizeProject(projectId, currentUserProvider.getEmail());
    }

    private String authorizeProject(Long projectId, String currentUserEmail) {
        ProjectAuthProjection projection = projectRepository.findAuthProjectionById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));

        boolean studentVisible = projection.getStatus() == ProjectStatus.PUBLISHED
                || projection.getStatus() == ProjectStatus.CLOSED;
        if (!projection.getMentorEmail().equals(currentUserEmail)
                && !(studentVisible && projectRepository.isStudentEnrolled(projection.getMentorshipId(), currentUserEmail))) {
            throw new AccessDeniedException("Not authorized to view this project attachment");
        }
        return projection.getFilePath();
    }

    public String authorizeProjectSubmission(Long submissionId) {
        return authorizeProjectSubmission(submissionId, currentUserProvider.getEmail());
    }

    private String authorizeProjectSubmission(Long submissionId, String currentUserEmail) {
        ProjectSubmissionAuthProjection projection = projectSubmissionRepository.findAuthProjectionById(submissionId)
                .orElseThrow(() -> new EntityNotFoundException("Project submission not found"));

        if (!projection.getStudentEmail().equals(currentUserEmail)
                && !projection.getMentorEmail().equals(currentUserEmail)) {
            throw new AccessDeniedException("Not authorized to view this project submission");
        }
        return projection.getFilePath();
    }
}
