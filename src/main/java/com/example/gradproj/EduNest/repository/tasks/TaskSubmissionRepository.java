package com.example.gradproj.EduNest.repository.tasks;

import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.repository.tasks.projection.TaskSubmissionAuthProjection;
import com.example.gradproj.EduNest.repository.tasks.projection.TaskWithSubmissionProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission,Long> {
    java.util.Optional<TaskSubmission> findByPublicId(java.util.UUID publicId);
    @Query("SELECT ts.id FROM TaskSubmission ts WHERE ts.publicId = :publicId")
    java.util.Optional<Long> findIdByPublicId(@Param("publicId") java.util.UUID publicId);
    boolean existsByPublicId(java.util.UUID publicId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ts FROM TaskSubmission ts WHERE ts.publicId = :publicId")
    java.util.Optional<TaskSubmission> findForUpdateByPublicId(@Param("publicId") java.util.UUID publicId);

    @Query("""
        SELECT ts.id AS id,
               s.email AS studentEmail,
               m.email AS mentorEmail,
               ts.uploadedFilePath AS filePath
        FROM TaskSubmission ts
        JOIN ts.student s
        JOIN ts.task t
        JOIN t.week w
        JOIN w.mentorship ms
        JOIN ms.mentor m
        WHERE ts.id = :id
    """)
    Optional<TaskSubmissionAuthProjection> findAuthProjectionById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ts FROM TaskSubmission ts WHERE ts.id = :id")
    Optional<TaskSubmission> findForUpdateById(@Param("id") Long id);

    @Query("SELECT s FROM TaskSubmission s JOIN FETCH s.student WHERE s.task.id = :taskId")
    Page<TaskSubmission> findByTask_Id(Long taskId, Pageable pageable);
    long countByTask_IdAndStatus(Long taskId, SubmissionStatus status);
    Optional<TaskSubmission> findByTask_IdAndStudent_Id(Long taskId, Long studentId);
    boolean existsByTask_IdAndStudent_Id(Long taskId, Long studentId);
    boolean existsByTask_Id(Long taskId);
    /** EB-51 / D-09 — freeze predicate: at least one qualifying (GRADED) submission exists. */
    boolean existsByTask_IdAndStatus(Long taskId, SubmissionStatus status);
    /** EB-51 — cascade-delete protection: rejects deleting week if submissions exist. */
    boolean existsByTask_Week_Id(Long weekId);
    boolean existsByTask_Week_IdAndStatus(Long weekId, SubmissionStatus status);

    @Query("""
        SELECT
            t.publicId                              AS taskId,
            t.title                                 AS taskTitle,
            t.points                                AS points,
            t.dueAt                                 AS dueAt,
            t.description                           AS description,
            t.attachmentUrl                         AS attachmentUrl,
            t.uploadedAttachmentPath                AS uploadedAttachmentPath,
            t.estimatedMinutes                      AS estimatedMinutes,
            ts.publicId                             AS submissionId,
            ts.fileUrl                              AS fileUrl,
            ts.finalScore                           AS finalScore,
            t.points                                AS totalPoints,
            ts.status                               AS submissionStatus,
            ts.uploadedFilePath                     AS uploadedSubmissionPath,    
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

    @Query("""
        SELECT ts.student.id
        FROM TaskSubmission ts
        WHERE ts.task.id = :taskId
    """)
    List<Long> findStudentIdsByTaskId(@Param("taskId") Long taskId);

    @Query("""
        SELECT ts
        FROM TaskSubmission ts
        JOIN ts.task t
        JOIN t.week w
        WHERE ts.student.id = :studentId
          AND w.mentorship.id = :mentorshipId
    """)
    List<TaskSubmission> findByStudentIdAndMentorshipId(@Param("studentId") Long studentId,
                                                        @Param("mentorshipId") Long mentorshipId);
}
