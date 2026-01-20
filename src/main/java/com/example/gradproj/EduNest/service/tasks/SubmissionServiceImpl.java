package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.dto.tasks.GradeSubmissionRequest;
import com.example.gradproj.EduNest.dto.tasks.SubmissionResponse;
import com.example.gradproj.EduNest.dto.tasks.SubmitTaskRequest;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.utils.SystemUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class SubmissionServiceImpl implements SubmissionService {
    private final TaskRepository taskRepository;
    private final TaskSubmissionRepository submissionRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;

    public SubmissionServiceImpl(TaskRepository taskRepository, TaskSubmissionRepository submissionRepository, TaskSubmissionRepository taskSubmissionRepository) {
        this.taskRepository = taskRepository;
        this.submissionRepository = submissionRepository;
        this.taskSubmissionRepository = taskSubmissionRepository;
    }
    @Override
    public SubmissionResponse submit(Long taskId, SubmitTaskRequest req) {
        Task task= taskRepository.findById(taskId).orElseThrow(() -> new IllegalArgumentException("Task not found with id: " + taskId));
        if (task.getStatus()!= TaskStatus.PUBLISHED){
            throw new IllegalArgumentException("Task is not published");
        }
        if (task.getMaxAttempts()!=null&&task.getMaxAttempts()==0){
            throw  new IllegalArgumentException("Submissions are disabled for this task");
        }
        int usedAttempts=submissionRepository.countByTask_idAndStudentId(taskId,req.getStudentId());
        if (usedAttempts>=task.getMaxAttempts()){
            throw   new IllegalArgumentException("Submissions are disabled for this task");
        }
        int attemptedNo=usedAttempts+1;
        LocalDateTime now=LocalDateTime.now();
        boolean isLate= now.isAfter(task.getDueAt());

        TaskSubmission sub=TaskSubmission
        .builder()
                .task(task)
                .studentId(req.getStudentId())
                .attemptNo(attemptedNo)
                .fileUrl(req.getFileUrl())
                .submittedAt(now)
                .isLate(isLate)
                .status(SubmissionStatus.SUBMITTED)
                .build();
//لحد منعمل ال login عشان نجيب الي جوه ال context
        sub.setUpdatedBy("student with id :"+req.getStudentId());
        sub.setCreatedBy("student with id :"+req.getStudentId());

        TaskSubmission saved=submissionRepository.save(sub);
        return mapToSubmissionResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubmissionResponse> listByTask(Long taskId) {
        return submissionRepository.findByTask_id(taskId).stream()
                .map(this::mapToSubmissionResponse)
                .collect(Collectors.toList());
    }

    @Override
    public SubmissionResponse grade(Long submissionId, GradeSubmissionRequest req) {
        TaskSubmission sub =submissionRepository.findById(submissionId).orElseThrow(() -> new IllegalArgumentException("Submission not found with id: " + submissionId));
        Task task=sub.getTask();
        if (req.getScore()>task.getPoints()){
            throw new IllegalArgumentException("score must be <= task points");
        }
        sub.setRawScore(req.getScore());
        sub.setFeedBack(req.getFeedback());
        int finalScore=sub.getIsLate() ? (req.getScore()/2) : (req.getScore());
        sub.setFinalScore(finalScore);
        sub.setStatus(SubmissionStatus.GRADED);
        sub.setGradedBy(SystemUtils.MENTOR);
        sub.setGradedAt(LocalDateTime.now());
        sub.setUpdatedBy(SystemUtils.MENTOR);

        return mapToSubmissionResponse(sub);
    }

    private SubmissionResponse mapToSubmissionResponse(TaskSubmission s) {
        SubmissionResponse res = new SubmissionResponse();
        res.setId(s.getId());
        res.setTaskId(s.getTask().getId());
        res.setStudentId(s.getStudentId());
        res.setAttemptNo(s.getAttemptNo());
        res.setFileUrl(s.getFileUrl());
        res.setStatus(s.getStatus().name());
        res.setIsLate(s.getIsLate());
        res.setRawScore(s.getRawScore());
        res.setFinalScore(s.getFinalScore());
        res.setSubmittedAt(s.getSubmittedAt());
        res.setGradedAt(s.getGradedAt());
        return res;
    }
}
