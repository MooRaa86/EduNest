package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.tasks.requests.GradeTaskSubmissionRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.SubmitTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.response.TaskSubmissionResponse;

public interface TaskSubmissionService {
    TaskSubmissionResponse submit(Long taskId, SubmitTaskRequest req);
    public PageResponse<TaskSubmissionResponse> listByTask(
            Long taskId,
            int page,
            int size
    );
    TaskSubmissionResponse grade(Long submissionId, GradeTaskSubmissionRequest req);
}
