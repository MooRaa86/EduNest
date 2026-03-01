package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.tasks.requests.GradeTaskSubmissionRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.SubmitTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.response.TaskSubmissionResponse;
import org.springframework.security.access.prepost.PreAuthorize;

public interface TaskSubmissionService {
    @PreAuthorize("hasRole('STUDENT')")
    TaskSubmissionResponse submit(Long taskId, SubmitTaskRequest req);
    @PreAuthorize("hasRole('MENTOR')")
    public PageResponse<TaskSubmissionResponse> listByTask(
            Long taskId,
            int page,
            int size
    );
    @PreAuthorize("hasRole('MENTOR')")
    TaskSubmissionResponse grade(Long submissionId, GradeTaskSubmissionRequest req);
}
