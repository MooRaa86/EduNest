package com.example.gradproj.EduNest.repository.tasks;

import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.repository.tasks.projection.TaskWithSubmissionProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission,Long> {
    @Query("SELECT s FROM TaskSubmission s JOIN FETCH s.student WHERE s.task.id = :taskId")
    Page<TaskSubmission> findByTask_Id(Long taskId, Pageable pageable);
    Optional<TaskSubmission> findByTask_IdAndStudent_Id(Long taskId, Long studentId);
    boolean existsByTask_IdAndStudent_Id(Long taskId, Long studentId);

    @Query("""
        SELECT
            t.id                                    AS taskId,
            t.title                                 AS taskTitle,
            t.points                                AS points,
            t.dueAt                                 AS dueAt,
            t.description                           AS description,
            t.attachmentUrl                         AS attachmentUrl,
            t.uploadedAttachmentPath                AS uploadedAttachmentPath,
            t.estimatedMinutes                      AS estimatedMinutes,
            ts.fileUrl                              AS fileUrl,
            ts.finalScore                           AS finalScore,
            t.points                                AS totalPoints,
            ts.status                               AS submissionStatus,
            ts.feedBack                             AS feedback,
            CONCAT(m.firstName, ' ', m.lastName)    AS mentorName,
            m.profileImageUrl                       AS mentorPhoto
        FROM Task t
        JOIN t.week w
        JOIN w.mentorship ms
        JOIN ms.mentor m
        LEFT JOIN t.submissions ts ON ts.student.email = :email
        WHERE t.id = :taskId
    """)
    TaskWithSubmissionProjection findTaskWithSubmission(
            @Param("taskId") Long taskId,
            @Param("email") String email
    );

    @Query("""
        SELECT ts
        FROM TaskSubmission ts
        JOIN ts.task t
        WHERE ts.student.id = :studentId
          AND t.week.id = :weekId
    """)
    List<TaskSubmission> findByStudentIdAndWeekId(@Param("studentId") Long studentId,
                                                   @Param("weekId") Long weekId);

    @Query("""
        SELECT ts
        FROM TaskSubmission ts
        JOIN ts.task t
        WHERE ts.student.id = :studentId
          AND t.week.id IN :weekIds
    """)
    List<TaskSubmission> findByStudent_IdAndTask_Week_IdIn(@Param("studentId") Long studentId,
                                                             @Param("weekIds") List<Long> weekIds);
}
