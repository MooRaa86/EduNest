package com.example.gradproj.EduNest.service.projects;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.projects.request.GradeProjectSubmissionRequest;
import com.example.gradproj.EduNest.dto.projects.response.ProjectSubmissionResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

public interface ProjectSubmissionService {
    @PreAuthorize("hasRole('STUDENT')")
    ProjectSubmissionResponse submit(Long projectId, MultipartFile file, String fileUrl);
    @PreAuthorize("hasRole('MENTOR')")
    PageResponse<ProjectSubmissionResponse> listByProject(
            Long projectId,
            int page,
            int size
    );
    @PreAuthorize("hasRole('MENTOR')")
    ProjectSubmissionResponse gradeProject(Long submissionId, GradeProjectSubmissionRequest req);
}
