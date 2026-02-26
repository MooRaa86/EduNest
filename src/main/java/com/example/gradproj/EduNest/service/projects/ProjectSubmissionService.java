package com.example.gradproj.EduNest.service.projects;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.projects.request.GradeProjectSubmissionRequest;
import com.example.gradproj.EduNest.dto.projects.request.SubmitProjectRequest;
import com.example.gradproj.EduNest.dto.projects.response.ProjectSubmissionResponse;

public interface ProjectSubmissionService {
    ProjectSubmissionResponse submit(Long projectId, SubmitProjectRequest req);
    PageResponse<ProjectSubmissionResponse> listByProject(
            Long projectId,
            int page,
            int size
    );
    ProjectSubmissionResponse gradeProject(Long submissionId, GradeProjectSubmissionRequest req);
}
