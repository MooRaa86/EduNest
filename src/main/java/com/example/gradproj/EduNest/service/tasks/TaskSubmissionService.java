package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.dto.tasks.requests.GradeSubmissionRequest;
import com.example.gradproj.EduNest.dto.tasks.response.SubmissionResponse;
import com.example.gradproj.EduNest.dto.tasks.requests.SubmitTaskRequest;

import java.util.List;

public interface TaskSubmissionService {
    SubmissionResponse submit(Long taskId, SubmitTaskRequest req);
    List<SubmissionResponse> listByTask(Long taskId);
    SubmissionResponse grade(Long submissionId, GradeSubmissionRequest req);
}
