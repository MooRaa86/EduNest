package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.dto.tasks.GradeSubmissionRequest;
import com.example.gradproj.EduNest.dto.tasks.SubmissionResponse;
import com.example.gradproj.EduNest.dto.tasks.SubmitTaskRequest;

import java.util.List;

public interface SubmissionService {
    SubmissionResponse submit(Long taskId, SubmitTaskRequest req);
    List<SubmissionResponse> listByTask(Long taskId);
    SubmissionResponse grade(Long submissionId, GradeSubmissionRequest req);
}
