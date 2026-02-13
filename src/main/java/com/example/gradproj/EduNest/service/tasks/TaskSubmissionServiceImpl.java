package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.dto.tasks.requests.GradeTaskSubmissionRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.SubmitTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.response.TaskSubmissionResponse;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class TaskSubmissionServiceImpl implements TaskSubmissionService {
    private final TaskRepository taskRepository;
    private final TaskSubmissionRepository submissionRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final StudentRepository studentRepository;
    private final TotalPointsServiceImp totalPointsService;
    private final TotalPointsRepository totalPointsRepository;




    private String getCurrentStudentEmail() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Unauthenticated user");
        }
        return authentication.getName();
    }

    @Override
    public TaskSubmissionResponse submit(Long taskId, SubmitTaskRequest req) {
        Task task= taskRepository.findById(taskId).orElseThrow(() ->
                new IllegalArgumentException("Task not found"));

        if (task.getStatus()!= TaskStatus.PUBLISHED){
            throw new globalLogicEx("Task is not published");
        }

        Student student=studentRepository.findByEmail(getCurrentStudentEmail());

        LocalDateTime now=LocalDateTime.now();
        boolean isLate= now.isAfter(task.getDueAt());

        Optional<TaskSubmission> existingOpt=submissionRepository.findByTask_IdAndStudent_Id(taskId,student.getId());

        if (isLate && existingOpt.isPresent()){
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
    public List<TaskSubmissionResponse> listByTask(Long taskId) {

        if(!taskRepository.existsById(taskId)){
            throw new globalLogicEx("Task not found with this id");
        }

        return submissionRepository.findByTask_id(taskId).stream()
                .map(this::mapToSubmissionResponse)
                .collect(Collectors.toList());
    }

    @Override
    public TaskSubmissionResponse grade(Long submissionId, GradeTaskSubmissionRequest req) {

        TaskSubmission sub = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found"));

        Task task = sub.getTask();

        if (req.getScore() > task.getPoints()) {
            throw new globalLogicEx("score must be less than or equal to task points " + task.getPoints());
        }

        sub.setRawScore(task.getPoints());
        sub.setFeedBack(req.getFeedback());
        sub.setFinalScore(req.getScore());
        sub.setStatus(SubmissionStatus.GRADED);
        sub.setGradedAt(LocalDateTime.now());


        MentorShip mentorship = task.getWeek().getMentorship();

//        //--------------------------------------- remove points applied
        int newScore = sub.getFinalScore();
        int oldApplied = (sub.getPointsApplied() == null) ? 0 : sub.getPointsApplied();
        int delta = newScore - oldApplied;

        if (delta != 0) {
            totalPointsService.applyDelta(sub.getStudent(), mentorship, delta);
        }

        sub.setPointsApplied(newScore);

        submissionRepository.save(sub);

        return mapToSubmissionResponse(sub);
    }


    private TaskSubmissionResponse mapToSubmissionResponse(TaskSubmission s) {
        TaskSubmissionResponse res = new TaskSubmissionResponse();
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
