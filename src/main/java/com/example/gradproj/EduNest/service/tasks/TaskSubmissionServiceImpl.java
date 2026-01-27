package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.dto.tasks.requests.GradeSubmissionRequest;
import com.example.gradproj.EduNest.dto.tasks.response.SubmissionResponse;
import com.example.gradproj.EduNest.dto.tasks.requests.SubmitTaskRequest;
import com.example.gradproj.EduNest.entity.Student;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.StudentRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class TaskSubmissionServiceImpl implements TaskSubmissionService {
    private final TaskRepository taskRepository;
    private final TaskSubmissionRepository submissionRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final StudentRepository studentRepository;

    public TaskSubmissionServiceImpl(TaskRepository taskRepository, TaskSubmissionRepository submissionRepository, TaskSubmissionRepository taskSubmissionRepository, StudentRepository studentRepository) {
        this.taskRepository = taskRepository;
        this.submissionRepository = submissionRepository;
        this.taskSubmissionRepository = taskSubmissionRepository;
        this.studentRepository= studentRepository;
    }

    private String getCurrentStudentEmail() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Unauthenticated user");
        }
        return authentication.getName();
    }

    @Override
    public SubmissionResponse submit(Long taskId, SubmitTaskRequest req) {
        Task task= taskRepository.findById(taskId).orElseThrow(() -> new IllegalArgumentException("Task not found with id: " + taskId));
        if (task.getStatus()!= TaskStatus.PUBLISHED){
            throw new globalLogicEx("Task is not published");
        }

        Student student=studentRepository.findByEmail(getCurrentStudentEmail());

        LocalDateTime now=LocalDateTime.now();
        boolean isLate= now.isAfter(task.getDueAt());

        Optional<TaskSubmission> existingOpt=submissionRepository.findByTask_IdAndStudent_Id(taskId,student.getId());

        if (existingOpt.isPresent()&&isLate){
            throw  new globalLogicEx("Deadline passed. You can't resubmit because you already submitted before.");
        }


        TaskSubmission sub = existingOpt.orElseGet(() -> {
            TaskSubmission s = new TaskSubmission();
            s.setTask(task);
            s.setStudent(student);
            s.setStatus(SubmissionStatus.SUBMITTED);
            return s;
        });


        sub.setFileUrl(req.getFileUrl());
        sub.setSubmittedAt(now);
        sub.setIsLate(isLate);
        sub.setStatus(SubmissionStatus.SUBMITTED);
        sub.setRawScore(null);
        sub.setFinalScore(null);
        sub.setFeedBack(null);
        sub.setGradedAt(null);

        TaskSubmission saved = submissionRepository.save(sub);
        return mapToSubmissionResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubmissionResponse> listByTask(Long taskId) {

        if(!taskRepository.existsById(taskId)){
            throw new globalLogicEx("Task not found with this id");
        }

        return submissionRepository.findByTask_id(taskId).stream()
                .map(this::mapToSubmissionResponse)
                .collect(Collectors.toList());
    }

    @Override
    public SubmissionResponse grade(Long submissionId, GradeSubmissionRequest req) {
        TaskSubmission sub =submissionRepository.findById(submissionId).orElseThrow(() -> new IllegalArgumentException("Submission not found with id: " + submissionId));
        Task task=sub.getTask();
        if (req.getScore()>task.getPoints()){
            throw new globalLogicEx("score must be less than or equal to task points " + task.getPoints());
        }
        sub.setRawScore(task.getPoints());
        sub.setFeedBack(req.getFeedback());
        sub.setFinalScore(req.getScore());
        sub.setStatus(SubmissionStatus.GRADED);
        sub.setGradedAt(LocalDateTime.now());
        return mapToSubmissionResponse(sub);
    }

    private SubmissionResponse mapToSubmissionResponse(TaskSubmission s) {
        SubmissionResponse res = new SubmissionResponse();
        res.setSubmissionId(s.getId());
        res.setTaskId(s.getTask().getId());
        res.setStudentId(s.getStudent().getId());
        res.setFileUrl(s.getFileUrl());
        res.setStatus(SubmissionStatus.valueOf(s.getStatus().name()));
        res.setIsLate(s.getIsLate());
        res.setRawScore(s.getRawScore());
        res.setFinalScore(s.getFinalScore());
        res.setSubmittedAt(s.getSubmittedAt());
        res.setFeedback(s.getFeedBack());
        return res;
    }
}
