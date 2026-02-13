package com.example.gradproj.EduNest.service.projects;

import com.example.gradproj.EduNest.dto.projects.request.GradeProjectSubmissionRequest;
import com.example.gradproj.EduNest.dto.projects.request.SubmitProjectRequest;
import com.example.gradproj.EduNest.dto.projects.response.ProjectSubmissionResponse;
import com.example.gradproj.EduNest.dto.tasks.requests.GradeTaskSubmissionRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.SubmitTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.response.TaskSubmissionResponse;

import java.util.List;

public interface ProjectSubmissionService {
    ProjectSubmissionResponse submit(Long projectId, SubmitProjectRequest req);
    List<ProjectSubmissionResponse> listByProject(Long projectId);
    ProjectSubmissionResponse gradeProject(Long submissionId, GradeProjectSubmissionRequest req);
}
