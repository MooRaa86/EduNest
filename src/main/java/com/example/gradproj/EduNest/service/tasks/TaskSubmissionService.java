package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.dto.tasks.requests.GradeTaskSubmissionRequest;
import com.example.gradproj.EduNest.dto.tasks.response.TaskSubmissionResponse;
import com.example.gradproj.EduNest.dto.tasks.requests.SubmitTaskRequest;

import java.util.List;

public interface TaskSubmissionService {
    TaskSubmissionResponse submit(Long taskId, SubmitTaskRequest req);
    List<TaskSubmissionResponse> listByTask(Long taskId);
    TaskSubmissionResponse grade(Long submissionId, GradeTaskSubmissionRequest req);
}
